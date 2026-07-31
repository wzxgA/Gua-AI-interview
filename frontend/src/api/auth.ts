import { http } from './client';
import type { LoginRequest, LoginResponse, RefreshResponse, SysUser } from '@/types/auth';

export function login(data: LoginRequest) {
  return http.post<LoginResponse>('/api/v1/auth/login', data);
}

export function refreshToken(refreshToken: string) {
  return http.post<RefreshResponse>('/api/v1/auth/refresh', { refreshToken });
}

export function logout(refreshToken: string) {
  return http.post<void>('/api/v1/auth/logout', { refreshToken });
}

export function getMe() {
  return http.get<SysUser>('/api/v1/auth/me');
}
