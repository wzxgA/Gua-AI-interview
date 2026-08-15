import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface AudioPlayerProps {
  audioUrl: string;
  durationMs?: number;
}

/** TTS 音频播放器：播放/暂停 + 进度条 + 时长 */
export function AudioPlayer({ audioUrl, durationMs }: AudioPlayerProps) {
  const { t } = useTranslation();
  const audioRef = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [current, setCurrent] = useState(0);

  const fullUrl = `${import.meta.env.VITE_API_BASE || 'http://localhost:8080'}/api/v1/audio${audioUrl}`;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const onTimeUpdate = () => {
      setCurrent(audio.currentTime);
      if (audio.duration > 0) {
        setProgress((audio.currentTime / audio.duration) * 100);
      }
    };
    const onEnded = () => {
      setPlaying(false);
      setProgress(0);
      setCurrent(0);
    };

    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('ended', onEnded);
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('ended', onEnded);
    };
  }, []);

  // 自动播放：音频就绪后自动播放（浏览器可能阻止，阻止时降级为手动点击）
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    audio.play()
      .then(() => setPlaying(true))
      .catch(() => {
        // 浏览器 autoplay policy 阻止自动播放，用户需手动点击播放按钮
      });
  }, [fullUrl]);

  const toggle = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing) {
      audio.pause();
      setPlaying(false);
    } else {
      audio.play();
      setPlaying(true);
    }
  };

  const fmt = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return `${m}:${sec.toString().padStart(2, '0')}`;
  };

  const estimatedDuration = durationMs ? durationMs / 1000 : 0;

  return (
    <div className="mt-2 flex items-center gap-2 rounded-md border border-border-subtle bg-surface-hover/50 px-2 py-1.5">
      <audio ref={audioRef} src={fullUrl} preload="metadata" />

      {/* 播放/暂停按钮 */}
      <button
        onClick={toggle}
        className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-border-default bg-surface-overlay text-text-primary transition-colors hover:bg-surface-hover"
        aria-label={playing ? t('interviews.audioPause') : t('interviews.audioPlay')}
      >
        {playing ? (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <rect x="2" y="1" width="3" height="10" rx="0.5" />
            <rect x="7" y="1" width="3" height="10" rx="0.5" />
          </svg>
        ) : (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <path d="M3 1.5v9a0.5 0.5 0 0 0 0.77.42l7-4.5a0.5 0.5 0 0 0 0-.84l-7-4.5A0.5 0.5 0 0 0 3 1.5Z" />
          </svg>
        )}
      </button>

      {/* 进度条 */}
      <div className="relative h-1 flex-1 overflow-hidden rounded-full bg-border-subtle">
        <div
          className="absolute left-0 top-0 h-full rounded-full bg-silver-300 transition-[width] duration-150"
          style={{ width: `${progress}%` }}
        />
      </div>

      {/* 时长 */}
      <span className="shrink-0 text-xs tabular-nums text-text-muted">
        {fmt(current) || '0:00'}
        {estimatedDuration > 0 && ` / ${fmt(estimatedDuration)}`}
      </span>
    </div>
  );
}
