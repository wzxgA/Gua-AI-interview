import type { Result } from '@/types/common';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public traceId: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface RequestOptions extends RequestInit {
  // 是否直接返回 data（默认 true）
  unwrap?: boolean;
  // 内部标记：已自动刷新过，避免无限循环
  _retried?: boolean;
}

// ---- Token 刷新锁 ----
let refreshing: Promise<string | null> | null = null;

async function doRefresh(): Promise<string | null> {
  if (refreshing) return refreshing;
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;
  refreshing = (async () => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return null;
      const result: Result<{ accessToken: string; refreshToken: string }> = await res.json();
      if (result.code !== 0 || !result.data) return null;
      localStorage.setItem('accessToken', result.data.accessToken);
      localStorage.setItem('refreshToken', result.data.refreshToken);
      return result.data.accessToken;
    } catch {
      return null;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

function clearTokens() {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
}

/** 是否处于候选端（/i/* 路径，免登录）。 */
function isGuestMode(): boolean {
  return window.location.pathname.startsWith('/i/');
}

/** 取当前请求的鉴权 token：候选端优先 guestToken，管理端取 accessToken。 */
function getAuthToken(): string | null {
  if (isGuestMode()) {
    return sessionStorage.getItem('guestToken');
  }
  return localStorage.getItem('accessToken');
}

function clearGuestTokens() {
  sessionStorage.removeItem('guestToken');
  sessionStorage.removeItem('guestSessionId');
}

async function request<T>(url: string, options?: RequestOptions): Promise<T> {
  const { unwrap = true, _retried = false, ...init } = options ?? {};

  // FormData 上传时不设置 Content-Type，让浏览器自动生成 multipart boundary
  const isFormData = init.body instanceof FormData;
  const headers = new Headers(init?.headers);
  if (!isFormData) {
    headers.set('Content-Type', 'application/json');
  }
  // 注入 Authorization 头
  const token = getAuthToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const res = await fetch(`${API_BASE}${url}`, {
    ...init,
    headers,
  });

  // 401 处理（排除 auth 接口自身）
  if (res.status === 401 && !_retried && !url.includes('/auth/')) {
    // 候选端：guestToken 过期 → 清除并回到进入页重新输入密码
    if (isGuestMode()) {
      clearGuestTokens();
      const enterPath = window.location.pathname.replace(/\/room$|\/report$/, '');
      window.location.href = enterPath;
      throw new ApiError(5001, '会话已过期，请重新输入访问密码', '');
    }
    // 管理端：自动刷新 + 重放
    const newToken = await doRefresh();
    if (newToken) {
      return request<T>(url, { ...options, _retried: true });
    }
    // 刷新失败，清除 token 并跳转登录
    clearTokens();
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new ApiError(5001, '未登录或令牌已过期', '');
  }

  if (!res.ok) {
    let result: Result<unknown> | null = null;
    try {
      result = await res.json();
    } catch {
      // 非 JSON 响应
    }
    if (result) {
      throw new ApiError(result.code, result.message, result.traceId);
    }
    throw new ApiError(res.status, `HTTP ${res.status}`, '');
  }

  if (!unwrap) {
    return (await res.json()) as T;
  }

  const result: Result<T> = await res.json();
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message, result.traceId);
  }
  return result.data as T;
}

export const http = {
  get: <T>(url: string, options?: RequestOptions) =>
    request<T>(url, { ...options, method: 'GET' }),

  post: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),

  patch: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    }),

  put: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }),

  del: <T>(url: string, options?: RequestOptions) =>
    request<T>(url, { ...options, method: 'DELETE' }),

  upload: <T>(url: string, formData: FormData, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'POST',
      body: formData,
      headers: {
        // 不设置 Content-Type，让浏览器自动设置 multipart boundary
        ...options?.headers,
      },
    }),
};
