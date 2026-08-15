import { useCallback, useEffect, useRef, useState } from 'react';
import { postProctorEvents } from '@/api/access';

/** 采样间隔（5fps） */
const SAMPLE_INTERVAL_MS = 200;
/** 偏离/失脸持续达该时长 → 记一次事件（防瞬时误判） */
const CONFIRM_MS = 3000;

type GazeCls = 'normal' | 'face_lost' | 'gaze_away';
type GazeEventType = 'FACE_LOST' | 'GAZE_AWAY' | 'CAMERA_DENIED' | 'CAMERA_OFF' | 'CAMERA_ON';

export interface GazeDetectionHandle {
  /** 当前摄像头授权状态机：'prompt' 待弹窗授权 / 'granted' 运行中 / 'denied' 拒绝 / 'unavailable' 浏览器不支持 */
  camState: 'prompt' | 'granted' | 'denied' | 'unavailable';
  /** 用户确认授权（需在用户手势中调用） */
  grant: () => void;
  /** 用户拒绝授权（不阻断面试） */
  deny: () => void;
}

interface ActiveEvent {
  type: GazeEventType;
  at: number;
}

/**
 * 眼神检测：请求摄像头 → 每 200ms 采样一帧（VideoFrame）移交 Web Worker 推理
 * （MediaPipe FaceLandmarker，帧不出浏览器）→ 判定 face_lost/gaze_away →
 * 持续 CONFIRM_MS 才记一次事件并批量上报。
 * 仅在 proctor.gaze 开启且面试 IN_PROGRESS 时启用。
 */
export function useGazeDetection(
  enabled: boolean,
  sessionId: number | null,
  videoEl: HTMLVideoElement | null,
): GazeDetectionHandle {
  const [camState, setCamState] = useState<'prompt' | 'granted' | 'denied' | 'unavailable'>('prompt');

  const streamRef = useRef<MediaStream | null>(null);
  const workerRef = useRef<Worker | null>(null);
  // 预览视频元素由外部（CandidateRoomPage）渲染，hook 只绑定/采样
  const videoElRef = useRef<HTMLVideoElement | null>(null);
  const timerRef = useRef<number | null>(null);
  const rafRef = useRef<number | null>(null);
  /** 采样背压标志：上帧推理未返回时跳过新帧 */
  const inflightRef = useRef(false);

  const sessionIdRef = useRef(sessionId);
  const enabledRef = useRef(enabled);
  const camStateRef = useRef(camState);
  sessionIdRef.current = sessionId;
  enabledRef.current = enabled;
  camStateRef.current = camState;
  videoElRef.current = videoEl;

  /** 进行中的偏离/失脸事件（未达 CONFIRM_MS 或未恢复）。 */
  const activeRef = useRef<ActiveEvent | null>(null);
  /** 待上报 buffer（累计事件在恢复时结算入上报，比 tabSwitch 更即时：受 window focus 约束）。 */
  const pendingRef = useRef<ActiveEvent[]>([]);
  /** 上次的帧分类，用于状态切换判断。 */
  const lastClsRef = useRef<GazeCls>('normal');
  /** 当前偏离状态起始时间（wall clock，未确认）。 */
  const devStartRef = useRef<number | null>(null);
  const currentDevRef = useRef<GazeCls>('normal');

  const flush = useCallback(() => {
    if (pendingRef.current.length === 0 || !sessionIdRef.current) return;
    const items = pendingRef.current.splice(0).map((e) => ({
      eventType: e.type,
      occurredAt: new Date(e.at).toISOString(),
      durationMs: null,
    }));
    postProctorEvents(sessionIdRef.current, items).catch(() => {});
  }, []);

  const report = useCallback((types: GazeEventType[]) => {
    const now = Date.now();
    pendingRef.current.push(...types.map((t) => ({ type: t, at: now })));
    flush();
  }, [flush]);

  const stopCamera = useCallback(() => {
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    rafRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    // 预览元素由 React 管理，仅解绑流
    if (videoElRef.current) videoElRef.current.srcObject = null;
    workerRef.current?.terminate();
    workerRef.current = null;
    // 停用前结算进行中的偏离事件（未达确认阈值则不记）
    const active = activeRef.current;
    if (active) {
      activeRef.current = null;
      if (Date.now() - active.at >= CONFIRM_MS) {
        pendingRef.current.push({ type: active.type, at: active.at });
      }
    }
    flush();
  }, [flush]);

  const handleResult = useCallback((cls: GazeCls) => {
    const now = Date.now();
    const dev = cls !== 'normal';
    if (dev && currentDevRef.current === 'normal') {
      // 进入偏离
      devStartRef.current = now;
      currentDevRef.current = cls === 'gaze_away' ? 'gaze_away' : 'face_lost';
    }
    if (!dev && currentDevRef.current !== 'normal') {
      // 恢复：若已确认过则结算事件
      if (activeRef.current) {
        activeRef.current = null;
        pendingRef.current.push({ type: currentDevRef.current === 'gaze_away' ? 'GAZE_AWAY' : 'FACE_LOST', at: devStartRef.current! });
        flush();
      }
      currentDevRef.current = 'normal';
      devStartRef.current = null;
    }
    if (dev && !activeRef.current && devStartRef.current && now - devStartRef.current >= CONFIRM_MS) {
      // 达到确认阈值 → 记一次事件
      activeRef.current = { type: currentDevRef.current === 'gaze_away' ? 'GAZE_AWAY' : 'FACE_LOST', at: devStartRef.current };
    }
    lastClsRef.current = cls;
  }, [flush]);

  const startCamera = useCallback(async () => {
    if (!sessionIdRef.current) return;
    if (streamRef.current) return; // 幂等：已有流则跳过（自动恢复场景）
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 640, height: 480, facingMode: 'user' },
        audio: false, // 纯视频，不采音频
      });
      streamRef.current = stream;

      // 初始化 worker（加载模型）；消息监听随 worker 一起创建/销毁，避免独立 effect 时序问题
      const worker = new Worker(new URL('../workers/gazeWorker.ts', import.meta.url), { type: 'module' });
      workerRef.current = worker;
      worker.addEventListener('message', (e: MessageEvent) => {
        // 收到任一返回（含 result）即代表 worker 已消费上一帧，复位背压标志
        inflightRef.current = false;
        if (e.data?.type === 'result') handleResult(e.data.cls as GazeCls);
      });

      const video = videoElRef.current;
      if (!video) return;
      video.muted = true;
      video.playsInline = true;
      video.srcObject = stream;

      await new Promise<void>((resolve, reject) => {
        const onReady = () => {
          worker.removeEventListener('message', onReady);
          resolve();
        };
        const onErr = (e: MessageEvent) => {
          if (e.data?.type === 'error') {
            worker.removeEventListener('message', onErr);
            reject(new Error(e.data.message));
          }
        };
        worker.addEventListener('message', onReady);
        worker.addEventListener('message', onErr);
        worker.postMessage({
          type: 'init',
          wasmPath: `${import.meta.env.BASE_URL}mediapipe-wasm`,
          modelPath: `${import.meta.env.BASE_URL}models/face_landmarker.task`,
        });
      });

      await video.play();
      setCamState('granted');
      report(['CAMERA_ON']);

      let ts = 0;
      const tick = () => {
        // 背压：上一帧未推理完成则跳过本帧，避免消息堆积/帧延迟关闭导致内存上涨
        if (inflightRef.current || !video.readyState) return;
        inflightRef.current = true;
        ts += 1;
        try {
          const frame = new VideoFrame(video, { timestamp: ts });
          worker.postMessage({ type: 'frame', frame, ts }, [frame]);
        } catch {
          inflightRef.current = false;
        }
      };
      timerRef.current = window.setInterval(tick, SAMPLE_INTERVAL_MS);

      // 摄像头生命周期：onended 在 tab 恢复/被系统占用时触发
      stream.getVideoTracks()[0]?.addEventListener('ended', () => {
        report(['CAMERA_OFF']);
        setCamState('denied');
        stopCamera();
      });
    } catch {
      // 用户拒绝/浏览器不支持
      report(['CAMERA_DENIED']);
      setCamState('unavailable');
    }
  }, [report, stopCamera, handleResult]);

  // 启用/停用：disabled 时停止摄像头；enabled 且已授权但流缺失（状态短暂波动被 stop 后恢复）→ 自动重启
  useEffect(() => {
    if (!enabledRef.current || !sessionIdRef.current) {
      stopCamera();
      return;
    }
    if (camStateRef.current === 'granted' && !streamRef.current) {
      startCamera();
    }
    return () => {
      if (!enabledRef.current) stopCamera();
    };
  }, [enabled, sessionId, stopCamera, startCamera]);

  const grant = useCallback(() => {
    if (camStateRef.current === 'granted') return;
    setCamState('granted');
    startCamera();
  }, [startCamera]);

  const deny = useCallback(() => {
    if (camStateRef.current === 'granted') return;
    setCamState('denied');
  }, []);

  return { camState, grant, deny };
}
