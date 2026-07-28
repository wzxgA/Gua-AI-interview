/** 对齐 PositionResponse */
export interface PositionResponse {
  id: number;
  title: string;
  department: string | null;
  jdText: string;
  requirementsJson: string | null;
  status: string;
  hasEmbedding: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 对齐 CreatePositionRequest */
export interface CreatePositionRequest {
  title: string;
  department?: string;
  jdText: string;
  requirementsJson?: string;
}

/** 对齐 UpdatePositionRequest */
export interface UpdatePositionRequest {
  title?: string;
  department?: string;
  jdText?: string;
  requirementsJson?: string;
  status?: string;
}

/** 列表查询参数 */
export interface PositionQuery {
  page?: number;
  size?: number;
  title?: string;
  department?: string;
}
