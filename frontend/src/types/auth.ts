/** 用户信息 */
export interface SysUser {
  id: number;
  username: string;
  displayName: string;
  role: 'ADMIN' | 'INTERVIEWER';
}

/** 登录请求 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 登录响应 */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  user: SysUser;
}

/** 刷新令牌响应 */
export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
}
