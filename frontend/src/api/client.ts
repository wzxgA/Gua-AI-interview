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
}

async function request<T>(url: string, options?: RequestOptions): Promise<T> {
  const { unwrap = true, ...init } = options ?? {};

  // FormData 上传时不设置 Content-Type，让浏览器自动生成 multipart boundary
  const isFormData = init.body instanceof FormData;
  const headers = new Headers(init?.headers);
  if (!isFormData) {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(`${API_BASE}${url}`, {
    ...init,
    headers,
  });

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
