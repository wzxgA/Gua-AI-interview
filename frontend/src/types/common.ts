/** 统一响应包装，对齐 com.aims.core.common.Result */
export interface Result<T> {
  code: number;
  message: string;
  data: T | null;
  traceId: string;
  timestamp: number;
}

/** 分页响应，对齐 MyBatis-Plus IPage */
export interface Page<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

/** 分页查询参数 */
export interface PageQuery {
  page?: number;
  size?: number;
}
