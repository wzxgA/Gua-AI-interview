import { useCallback, useEffect, useRef, useState } from 'react';

export type SpeechErrorCode =
  | 'not-allowed'
  | 'no-speech'
  | 'audio-capture'
  | 'network'
  | 'aborted'
  | 'service-not-allowed'
  | 'generic';

/** 语音转写错误码（UI 层映射为 speech.* 文案，aborted 静默） */
export type SpeechError = SpeechErrorCode;

/** 连续无结果（no-speech）的最大次数，超过后才视为失败并提示 */
const MAX_NO_SPEECH_SESSIONS = 5;

/** 语音适配器接口：为未来后端 ASR 预留抽象，本期仅实现 WebSpeechAdapter */
export interface SpeechAdapter {
  start(): void;
  stop(): void;
  abort(): void;
  /** 更新识别语言（下次 start 生效） */
  setLang(lang: string): void;
  readonly supported: boolean;
}

type RecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((event: unknown) => void) | null;
  onerror: ((event: unknown) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
};

function getRecognitionCtor(): (new () => RecognitionLike) | undefined {
  const w = window as unknown as {
    SpeechRecognition?: new () => RecognitionLike;
    webkitSpeechRecognition?: new () => RecognitionLike;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition;
}

/** 基于浏览器 Web Speech API 的适配器实现 */
export function createWebSpeechAdapter(
  lang: string,
  onResult: (final: string) => void,
  onError: (code: SpeechError) => void,
  onEnd: () => void,
): SpeechAdapter {
  const Ctor = getRecognitionCtor();
  const recognition = Ctor ? new Ctor() : null;

  if (recognition) {
    recognition.lang = lang;
    recognition.continuous = true;
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event) => {
      const results = (event as { results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }).results;
      let finalText = '';
      for (let i = 0; i < results.length; i++) {
        const result = results[i];
        if (result.isFinal) finalText += result[0].transcript;
      }
      if (finalText) onResult(finalText);
    };

    recognition.onerror = (event) => {
      const raw = (event as { error?: string }).error ?? 'generic';
      const code: SpeechError =
        raw === 'not-allowed' || raw === 'service-not-allowed' || raw === 'audio-capture'
          ? raw
          : raw === 'no-speech'
            ? 'no-speech'
            : raw === 'network'
              ? 'network'
              : raw === 'aborted'
                ? 'aborted'
                : 'generic';
      onError(code);
    };

    recognition.onend = () => onEnd();
  }

  return {
    get supported() {
      return Ctor != null;
    },
    start() {
      if (recognition) {
        try {
          recognition.start();
        } catch {
          // 已处于 started 状态时再次调用会抛错，忽略
        }
      }
    },
    stop() {
      if (recognition) {
        try {
          recognition.stop();
        } catch {
          // 未开始时 stop 会抛错，忽略
        }
      }
    },
    abort() {
      if (recognition) {
        try {
          recognition.abort();
        } catch {
          // 忽略
        }
      }
    },
    setLang(next: string) {
      if (recognition) recognition.lang = next;
    },
  };
}

interface UseSpeechToTextOptions {
  /** 识别语言，如 'zh-CN' / 'en' */
  lang: string;
  /** 增量转写结果（追加语义，final 结果） */
  onResult: (text: string) => void;
}

interface UseSpeechToTextReturn {
  /** 浏览器是否支持语音识别（不支持时隐藏按钮） */
  supported: boolean;
  /** 是否正在录音 */
  listening: boolean;
  /** 错误码（aborted 不置位） */
  error: SpeechError | null;
  /** 开始录音 */
  start: () => void;
  /** 结束录音 */
  stop: () => void;
  /** 清除错误提示 */
  resetError: () => void;
}

/**
 * 语音转文字 hook：封装 Web Speech API 生命周期。
 *
 * 稳定性策略（应对 Web Speech API 的 no-speech 已知特性，Chromium #40786350）：
 * - Chrome 约 5s 无语音即触发 no-speech 并结束会话，此时用户再说话会收不到；
 * - 因此 no-speech 不作为致命错误：onend 后自动重启识别，会话间连续无结果；
 * - 仅在连续 MAX_NO_SPEECH_SESSIONS 次无结果时才置 error 并停止，提示用户；
 * - onresult 收到结果即重置计数。
 * 语言变化时中止并重启识别；组件卸载时释放麦克风。
 */
export function useSpeechToText({ lang, onResult }: UseSpeechToTextOptions): UseSpeechToTextReturn {
  const [listening, setListening] = useState(false);
  const [error, setError] = useState<SpeechError | null>(null);

  const onResultRef = useRef(onResult);
  onResultRef.current = onResult;

  // 用户是否仍想录音（true 期间 onend 自动重启）
  const shouldListenRef = useRef(false);
  // 连续无结果会话计数（onresult 命中即清零）
  const noSpeechCountRef = useRef(0);
  const langRef = useRef(lang);

  const handleResult = useCallback((final: string) => {
    noSpeechCountRef.current = 0;
    onResultRef.current(final);
  }, []);

  const handleError = useCallback((code: SpeechError) => {
    if (code === 'aborted') return;
    if (code === 'no-speech') return; // 由 onend 自动重启处理
    setError(code);
  }, []);

  const handleEnd = useCallback(() => {
    if (!shouldListenRef.current) {
      setListening(false);
      return;
    }
    // 本会话未产生任何结果 → no-speech；连续超过阈值则停止并提示
    noSpeechCountRef.current += 1;
    if (noSpeechCountRef.current > MAX_NO_SPEECH_SESSIONS) {
      shouldListenRef.current = false;
      noSpeechCountRef.current = 0;
      setListening(false);
      setError('no-speech');
      return;
    }
    // 自动重启，保持录音状态（用户无需再次点击）
    adapterRef.current?.start();
  }, []);

  const adapterRef = useRef<SpeechAdapter | null>(null);
  if (!adapterRef.current) {
    adapterRef.current = createWebSpeechAdapter(lang, handleResult, handleError, handleEnd);
  }

  // 语言变化时重启识别
  useEffect(() => {
    if (langRef.current === lang) return;
    langRef.current = lang;
    const adapter = adapterRef.current;
    if (!adapter) return;
    adapter.abort();
    adapter.setLang(lang);
    if (shouldListenRef.current) adapter.start();
  }, [lang]);

  // 卸载时释放麦克风
  useEffect(() => {
    const adapter = adapterRef.current;
    return () => {
      shouldListenRef.current = false;
      adapter?.abort();
    };
  }, []);

  const start = useCallback(() => {
    const adapter = adapterRef.current;
    if (!adapter?.supported) return;
    shouldListenRef.current = true;
    noSpeechCountRef.current = 0;
    setError(null);
    setListening(true);
    adapter.start();
  }, []);

  const stop = useCallback(() => {
    const adapter = adapterRef.current;
    if (!adapter) return;
    shouldListenRef.current = false;
    noSpeechCountRef.current = 0;
    setListening(false);
    adapter.stop();
  }, []);

  const resetError = useCallback(() => setError(null), []);

  return {
    supported: adapterRef.current?.supported ?? false,
    listening,
    error,
    start,
    stop,
    resetError,
  };
}
