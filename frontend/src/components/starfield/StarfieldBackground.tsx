import { useEffect, useRef } from 'react';
import { useTheme } from '@/contexts/ThemeContext';

/**
 * 星空字段（Float32Array 扁平存储，按 STAR_FIELDS 步长寻址）
 * [x, y, size, baseOpacity, period, phase, driftX]
 */
const STAR_FIELDS = 7;

interface ShootingStar {
  x: number;
  y: number;
  vx: number;
  vy: number;
  length: number;
  speed: number;
}

export function StarfieldBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { resolvedTheme } = useTheme();
  const isDark = resolvedTheme === 'dark';

  useEffect(() => {
    if (!isDark) return;

    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // 高分屏清晰度适配（DPR 上限 2，避免超大屏无谓开销）
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    let w = 0;
    let h = 0;
    let stars = new Float32Array(0);
    let starCount = 0;
    let meteors: ShootingStar[] = [];
    let nextMeteorIn = 1.5 + Math.random() * 2.5; // 首颗 1.5~4s 内出现，避免冷场
    let raf = 0;
    let lastTime = performance.now();

    const resize = () => {
      w = window.innerWidth;
      h = window.innerHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      // 星星总数 80~150，随面积自适应
      starCount = Math.max(80, Math.min(150, Math.round((w * h) / 16000)));
      const buf = new Float32Array(starCount * STAR_FIELDS);
      for (let i = 0; i < starCount; i++) {
        const o = i * STAR_FIELDS;
        // 大小 1~3px，幂等分布（多为小星、少数亮星）
        const size = 1 + Math.pow(Math.random(), 2) * 2;
        // 亮度 0.3~1.0
        const baseOpacity = 0.3 + Math.random() * 0.7;
        // 层次感：远层小暗、近层大亮，近层视差漂移更快
        const layer = Math.random();
        let scaleSize = 1;
        let scaleOpacity = 1;
        let driftX = 0;
        if (layer < 0.5) {
          // 远层 50%
          scaleSize = 0.5 + Math.random() * 0.25;
          scaleOpacity = 0.5 + Math.random() * 0.25;
          driftX = -2 + Math.random() * 4; // 极慢
        } else if (layer < 0.85) {
          // 中层 35%
          scaleSize = 0.8 + Math.random() * 0.25;
          scaleOpacity = 0.65 + Math.random() * 0.25;
          driftX = -4 + Math.random() * 8;
        } else {
          // 近层 15%
          scaleOpacity = 0.8 + Math.random() * 0.2;
          driftX = -6 + Math.random() * 12;
        }
        buf[o] = Math.random() * w;
        buf[o + 1] = Math.random() * h;
        buf[o + 2] = Math.max(1, size * scaleSize);
        buf[o + 3] = Math.min(1, baseOpacity * scaleOpacity);
        buf[o + 4] = 2 + Math.random() * 3; // 闪烁周期 2~5s 随机
        buf[o + 5] = Math.random() * Math.PI * 2; // 随机相位
        buf[o + 6] = driftX;
      }
      stars = buf;
    };

    const spawnMeteor = () => {
      // 角度 20°~56°（右下向左下斜落），叠加轻微随机
      const angle = Math.PI / 9 + Math.random() * (Math.PI / 5);
      // 轨迹长度 ≈ 屏宽 1/3
      const length = w * (0.28 + Math.random() * 0.1);
      // 0.8~1.1s 走完全程
      const speed = length / (0.8 + Math.random() * 0.3);
      meteors.push({
        x: w * (0.55 + Math.random() * 0.37), // 右上区域
        y: h * (0.05 + Math.random() * 0.3),
        vx: -Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        length,
        speed,
      });
      // 下一颗 8~15s 后
      nextMeteorIn = 8 + Math.random() * 7;
    };

    /** reduced-motion 降级：单帧静态星空（无闪烁/漂移/流星） */
    const drawStatic = () => {
      ctx.clearRect(0, 0, w, h);
      ctx.fillStyle = '#eef0f4';
      for (let i = 0; i < starCount; i++) {
        const o = i * STAR_FIELDS;
        ctx.globalAlpha = stars[o + 3];
        ctx.beginPath();
        ctx.arc(stars[o], stars[o + 1], stars[o + 2], 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
    };

    const drawFrame = (now: number) => {
      // 秒级时间基准，闪烁周期与帧率解耦
      const t = now / 1000;
      // dt 上限 50ms，防切后台/跳帧时流星瞬移
      const dt = Math.min(0.05, Math.max(0, (now - lastTime) / 1000));
      lastTime = now;

      ctx.clearRect(0, 0, w, h);

      // 星星：颜色统一，亮度用 globalAlpha，减少状态切换
      ctx.fillStyle = '#eef0f4';
      for (let i = 0; i < starCount; i++) {
        const o = i * STAR_FIELDS;
        // 远/近层视差漂移（极慢），屏边循环
        const x = (stars[o] + stars[o + 6] * dt + w) % w;
        stars[o] = x;

        const twinkle =
          (Math.sin((t * Math.PI * 2) / stars[o + 4] + stars[o + 5]) + 1) / 2; // 0~1
        // 基值 × 0.55~1.0 波动，clamp 到 0.3~1.0
        const alpha = Math.max(
          0.3,
          Math.min(1, stars[o + 3] * (0.55 + 0.45 * twinkle)),
        );
        ctx.globalAlpha = alpha;
        ctx.beginPath();
        ctx.arc(x, stars[o + 1], stars[o + 2], 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      // 流星调度：8~15s 一颗，并发 ≤ 2
      nextMeteorIn -= dt;
      if (nextMeteorIn <= 0 && meteors.length < 2) {
        spawnMeteor();
      }
      meteors = meteors.filter((m) => {
        m.x += m.vx * dt;
        m.y += m.vy * dt;
        if (m.x < -m.length || m.y > h + m.length) return false;

        // 拖尾：头部亮白 → 尾部透明，沿运动反方向延伸
        const tx = m.x - (m.vx / m.speed) * m.length;
        const ty = m.y - (m.vy / m.speed) * m.length;
        const grad = ctx.createLinearGradient(m.x, m.y, tx, ty);
        grad.addColorStop(0, 'rgba(246, 248, 250, 0.9)');
        grad.addColorStop(1, 'rgba(246, 248, 250, 0)');
        ctx.strokeStyle = grad;
        ctx.lineWidth = 2;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(m.x, m.y);
        ctx.lineTo(tx, ty);
        ctx.stroke();

        // 头部亮核
        ctx.fillStyle = 'rgba(250, 251, 253, 0.95)';
        ctx.beginPath();
        ctx.arc(m.x, m.y, 1.6, 0, Math.PI * 2);
        ctx.fill();
        return true;
      });

      raf = requestAnimationFrame(drawFrame);
    };

    const onResize = () => {
      resize();
      if (reduceMotion) drawStatic();
    };

    const onVisibility = () => {
      if (document.hidden) {
        cancelAnimationFrame(raf);
        raf = 0;
      } else if (!raf && !reduceMotion) {
        lastTime = performance.now();
        raf = requestAnimationFrame(drawFrame);
      }
    };

    resize();
    if (reduceMotion) {
      drawStatic();
    } else {
      lastTime = performance.now();
      raf = requestAnimationFrame(drawFrame);
    }

    window.addEventListener('resize', onResize);
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', onResize);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [isDark]);

  // 浅色主题：静态渐变背景，无星空动画
  if (!isDark) {
    return (
      <div className="fixed inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--space-700)_0%,_var(--space-900)_60%)]" />
    );
  }

  return (
    <div className="fixed inset-0 -z-10">
      <canvas ref={canvasRef} className="h-full w-full" />
      {/* 边缘暗角：增强夜空纵深，稳定前景文字对比度 */}
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,_transparent_55%,_rgba(0,0,0,0.3)_100%)]" />
    </div>
  );
}
