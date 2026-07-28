import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        space: {
          900: 'var(--space-900)',
          800: 'var(--space-800)',
          700: 'var(--space-700)',
          600: 'var(--space-600)',
        },
        silver: {
          100: 'var(--silver-100)',
          200: 'var(--silver-200)',
          300: 'var(--silver-300)',
          glow: 'var(--silver-glow)',
          dim: 'var(--silver-dim)',
        },
        'text-primary': 'var(--text-primary)',
        'text-secondary': 'var(--text-secondary)',
        'text-muted': 'var(--text-muted)',
        success: 'var(--success)',
        warning: 'var(--warning)',
        danger: 'var(--danger)',
        info: 'var(--info)',
      },
      fontFamily: {
        sans: ['Inter', 'HarmonyOS Sans SC', 'PingFang SC', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      borderRadius: {
        lg: '16px',
        md: '10px',
      },
      animation: {
        stream: 'stream 2.4s ease-in-out infinite',
        twinkle: 'twinkle 3s ease-in-out infinite',
        drift: 'drift 120s linear infinite',
        'pulse-slow': 'pulse 3s ease-in-out infinite',
      },
      keyframes: {
        stream: {
          '0%': { transform: 'translateX(-100%)' },
          '50%, 100%': { transform: 'translateX(100%)' },
        },
        twinkle: {
          '0%, 100%': { opacity: '0.3' },
          '50%': { opacity: '0.9' },
        },
        drift: {
          '0%': { transform: 'translate(0, 0)' },
          '100%': { transform: 'translate(-50px, -30px)' },
        },
      },
    },
  },
  plugins: [],
};

export default config;
