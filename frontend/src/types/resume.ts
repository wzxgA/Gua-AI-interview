/** 对齐 ResumeResponse */
export interface ResumeResponse {
  id: number;
  candidateName: string;
  phone: string | null;
  email: string | null;
  rawText: string | null;
  parseStatus: string;
  parsedResume: ParsedResume | null;
  fileUrl: string | null;
  hasEmbedding: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 对齐 ParsedResume */
export interface ParsedResume {
  candidateName: string;
  phone: string | null;
  email: string | null;
  yearsOfExperience: number | null;
  education: string | null;
  currentTitle: string | null;
  skills: string[];
  workExperiences: WorkExperience[];
  projectHighlights: string[];
}

/** 对齐 WorkExperience */
export interface WorkExperience {
  company: string;
  title: string;
  period: string;
  description: string;
}

/** 对齐 ResumeSearchResult */
export interface ResumeSearchResult {
  id: number;
  candidateName: string;
  phone: string | null;
  email: string | null;
  score: number;
}

/** 列表查询参数 */
export interface ResumeQuery {
  page?: number;
  size?: number;
  candidateName?: string;
}
