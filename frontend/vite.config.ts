import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import fs from 'fs';

/**
 * MediaPipe wasm loader 在运行时动态 import `/mediapipe-wasm/*.js`（public 目录文件）。
 * Vite 有两处会报错：
 *  1. 浏览器直接请求该 URL 时被 viteTransformMiddleware 当作模块 transform → 用中间件直接从 public 读文件返回，绕过；
 *  2. Vite 在 transform mediapipe 预构建 bundle 时静态解析其中的动态 import → 命中 public 文件检查抛错。
 *     → 用 transform hook 给该动态 import 注入 @vite-ignore 注释，跳过静态解析（运行时仍正常加载）。
 * build 不受影响（public 文件原样复制，浏览器静态加载动态 import）。
 */
function mediapipeWasmDev(): Plugin {
  return {
    name: 'mediapipe-wasm-dev',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        // 模块请求会带 `?import` 等 query，必须取 pathname 再拼文件路径
        const pathname = decodeURIComponent(new URL(req.url ?? '', 'http://localhost').pathname);
        if (!pathname.startsWith('/mediapipe-wasm/')) return next();
        const file = path.join(server.config.root, 'public', pathname);
        if (!fs.existsSync(file)) return next();
        const ext = path.extname(pathname);
        res.setHeader(
          'Content-Type',
          ext === '.js' || ext === '.mjs' ? 'application/javascript' : 'application/octet-stream',
        );
        fs.createReadStream(file).pipe(res);
      });
    },
    transform(code) {
      if (!code.includes('/mediapipe-wasm/')) return null;
      return code.replace(
        /(import\s*\(\s*)(['"`])\/mediapipe-wasm\//g,
        '$1/* @vite-ignore */ $2/mediapipe-wasm/',
      );
    },
  };
}

export default defineConfig({
  plugins: [react(), mediapipeWasmDev()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  optimizeDeps: {
    // 预构建产物不经插件 transform hook，@vite-ignore 无法注入；排除后由 Vite 直接转换源码，
    // 其内部动态 import 可被上方 transform hook 处理
    exclude: ['@mediapipe/tasks-vision'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
});
