import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';

/**
 * 眼神检测 Web Worker：MediaPipe FaceLandmarker WASM 推理在后台线程执行，
 * 视频帧（VideoFrame）由主线程转移所有权传入，帧数据不出浏览器。
 *
 * 协议：
 *  main → worker: { type: 'init', wasmPath, modelPath }
 *  main → worker: { type: 'frame', frame: VideoFrame, ts }（transfer frame）
 *  worker → main: { type: 'ready' } / { type: 'error', message }
 *  worker → main: { type: 'result', cls: 'normal' | 'face_lost' | 'gaze_away', ts }
 */

interface WorkerScope {
  onmessage: ((e: MessageEvent) => void) | null;
  postMessage(msg: unknown): void;
}
const ctx = self as unknown as WorkerScope;

/** 人脸关键点索引（MediaPipe 478 点） */
const NOSE = 1;
const RIGHT_CHEEK = 234;
const LEFT_CHEEK = 454;
const RIGHT_EYE_OUTER = 33;
const RIGHT_EYE_INNER = 133;
const LEFT_EYE_INNER = 362;
const LEFT_EYE_OUTER = 263;
const RIGHT_IRIS = 468;
const LEFT_IRIS = 473;

/** 阈值（可调）：鼻尖相对脸颊中线的水平偏移 / 脸宽 > 该值 → 头部姿态偏离 */
const YAW_RATIO_THRESHOLD = 0.2;
/** 阈值（可调）：虹膜在眼裂内的归一化位置偏离中心 > 该值 → 水平注视偏离 */
const IRIS_DEVIATION_THRESHOLD = 0.3;

let landmarker: FaceLandmarker | null = null;

type FrameClass = 'normal' | 'face_lost' | 'gaze_away';

interface Landmark {
  x: number;
  y: number;
  z: number;
}

/** 基于关键点的简化注意力判定：无人脸 → face_lost；头部偏转或虹膜偏移到眼角 → gaze_away */
function classify(lm: Landmark[] | undefined): FrameClass {
  if (!lm || lm.length === 0) return 'face_lost';
  const nose = lm[NOSE];
  const cheekL = lm[LEFT_CHEEK];
  const cheekR = lm[RIGHT_CHEEK];
  const faceWidth = Math.abs(cheekL.x - cheekR.x) || 1e-6;
  const faceCenterX = (cheekL.x + cheekR.x) / 2;

  // 头部姿态：鼻尖相对脸颊中线的水平偏移
  const yawRatio = Math.abs(nose.x - faceCenterX) / faceWidth;
  if (yawRatio > YAW_RATIO_THRESHOLD) return 'gaze_away';

  // 水平注视：虹膜在眼裂（外眼角→内眼角）内的归一化位置，居中约 0.5
  const irisDeviation = (iris: Landmark, outer: Landmark, inner: Landmark) => {
    const span = Math.abs(inner.x - outer.x) || 1e-6;
    const ratio = Math.abs(iris.x - outer.x) / span;
    return Math.abs(ratio - 0.5);
  };
  const dev = Math.max(
    irisDeviation(lm[RIGHT_IRIS], lm[RIGHT_EYE_OUTER], lm[RIGHT_EYE_INNER]),
    irisDeviation(lm[LEFT_IRIS], lm[LEFT_EYE_OUTER], lm[LEFT_EYE_INNER]),
  );
  if (dev > IRIS_DEVIATION_THRESHOLD) return 'gaze_away';

  return 'normal';
}

ctx.onmessage = async (e: MessageEvent) => {
  const msg = e.data as { type: string; wasmPath?: string; modelPath?: string; frame?: VideoFrame; ts?: number };

  if (msg.type === 'init') {
    try {
      const vision = await FilesetResolver.forVisionTasks(msg.wasmPath!);
      landmarker = await FaceLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: msg.modelPath! },
        runningMode: 'VIDEO',
        numFaces: 1,
      });
      ctx.postMessage({ type: 'ready' });
    } catch (err) {
      ctx.postMessage({ type: 'error', message: String(err) });
    }
    return;
  }

  if (msg.type === 'frame' && landmarker && msg.frame && msg.ts != null) {
    const frame = msg.frame;
    try {
      const res = landmarker.detectForVideo(frame, msg.ts);
      ctx.postMessage({ type: 'result', cls: classify(res.faceLandmarks?.[0]), ts: msg.ts });
    } catch {
      // 单帧推理失败跳过，下个采样周期继续
    } finally {
      frame.close();
    }
  }
};
