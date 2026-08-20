import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from './client';

export interface TierItem {
  tier: string;
  provider: string;
  model: string;
  temperature: number | null;
  maxTokens: number | null;
  dimensions: number | null;
  fallback: string | null;
}

export interface ModelTiersData {
  defaultTier: string;
  tiers: TierItem[];
}

export function useModelTiers() {
  return useQuery({
    queryKey: ['system', 'model-tiers'],
    queryFn: () => http.get<ModelTiersData>('/api/v1/system/model-tiers'),
  });
}

// ---- AI 模型配置（v1.2 D 方案：设置页配置 url/apiKey，按档位独立配置） ----

/** provider 级生效配置（GET model-config）。 */
export interface ModelProviderConfig {
  name: string;
  baseUrl: string;
  /** 掩码后的 API Key（空串表示未配置）。 */
  apiKeyMasked: string;
  /** 'db' = DB 覆盖，'yml' = 沿用配置文件。 */
  source: 'db' | 'yml';
  maxConcurrency: number | null;
  /** true = yml 内置 provider（不可改名/删除）；false = DB 自定义 provider。 */
  builtin: boolean;
}

/** 档位级生效配置（GET model-config）。 */
export interface ModelTierConfig {
  tier: string;
  provider: string;
  model: string;
  temperature: number | null;
  maxTokens: number | null;
  dimensions: number | null;
  fallback: string | null;
  thinking: boolean | null;
  reasoningEffort: string | null;
  /** 档位独立 baseUrl（优先于 provider）；null 表示未配置。 */
  overrideBaseUrl: string | null;
  /** 档位独立 apiKey 掩码；null 表示未配置。 */
  overrideApiKeyMasked: string | null;
  source: 'db' | 'yml';
}

/** GET model-config 响应。 */
export interface ModelConfigView {
  defaultTier: string;
  providers: ModelProviderConfig[];
  tiers: ModelTierConfig[];
}

/**
 * 保存请求体。
 *
 * apiKey/overrideApiKey 语义：不传(null)=保留 DB 旧值，空串''=清除覆盖回退 yml，非空=覆盖。
 */
export interface SaveModelConfigCommand {
  providers: Array<{
    name: string;
    baseUrl: string;
    apiKey?: string | null;
    maxConcurrency?: number | null;
  }>;
  tiers: Array<{
    tier: string;
    provider?: string | null;
    model?: string | null;
    temperature?: number | null;
    maxTokens?: number | null;
    dimensions?: number | null;
    fallback?: string | null;
    thinking?: boolean | null;
    reasoningEffort?: string | null;
    overrideBaseUrl?: string | null;
    overrideApiKey?: string | null;
  }>;
}

/** 连通性测试结果。 */
export interface ModelConfigTestResult {
  results: Array<{
    tier: string;
    success: boolean;
    latencyMs: number | null;
    error: string | null;
  }>;
}

/** 当前生效配置（含掩码 key）。 */
export function useModelConfig() {
  return useQuery({
    queryKey: ['system', 'model-config'],
    queryFn: () => http.get<ModelConfigView>('/api/v1/system/model-config'),
  });
}

/** 保存配置并热刷新。 */
export function useSaveModelConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (command: SaveModelConfigCommand) =>
      http.put<ModelConfigView>('/api/v1/system/model-config', command),
    onSuccess: (view) => {
      queryClient.setQueryData(['system', 'model-config'], view);
      queryClient.invalidateQueries({ queryKey: ['system', 'model-tiers'] });
    },
  });
}

/** 连通性测试（不保存）。 */
export function useTestModelConfig() {
  return useMutation({
    mutationFn: (command: SaveModelConfigCommand) =>
      http.post<ModelConfigTestResult>('/api/v1/system/model-config/test', command),
  });
}

/** 恢复默认（清空 DB 覆盖，回退 yml）。 */
export function useResetModelConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<ModelConfigView>('/api/v1/system/model-config/reset'),
    onSuccess: (view) => {
      queryClient.setQueryData(['system', 'model-config'], view);
      queryClient.invalidateQueries({ queryKey: ['system', 'model-tiers'] });
    },
  });
}

/** 删除自定义 provider（yml 内置或正被档位引用时后端会拒绝）。 */
export function useDeleteModelConfigProvider() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      http.del<ModelConfigView>(`/api/v1/system/model-config/provider/${encodeURIComponent(name)}`),
    onSuccess: (view) => {
      queryClient.setQueryData(['system', 'model-config'], view);
      queryClient.invalidateQueries({ queryKey: ['system', 'model-tiers'] });
    },
  });
}

// ---- TTS 连接配置（v1.4 F 方案：设置页配置 url / apiKey / 音色） ----

/** GET tts-config 响应的生效值（yml + DB 合并，key 掩码）。 */
export interface TtsConfigView {
  enabled: boolean | null;
  enabledSource: 'db' | 'yml';
  provider: string | null;
  providerSource: 'db' | 'yml';
  baseUrl: string | null;
  baseUrlSource: 'db' | 'yml';
  /** 掩码后的 API Key（空串表示未配置）。 */
  apiKeyMasked: string;
  apiKeySource: 'db' | 'yml';
  resourceId: string | null;
  resourceIdSource: 'db' | 'yml';
  defaultSpeaker: string | null;
  defaultSpeakerSource: 'db' | 'yml';
  format: string | null;
  formatSource: 'db' | 'yml';
  sampleRate: number | null;
  sampleRateSource: 'db' | 'yml';
  speechRate: number | null;
  speechRateSource: 'db' | 'yml';
  personaVoiceLink: boolean | null;
  personaVoiceLinkSource: 'db' | 'yml';
  updatedBy: string | null;
  updatedAt: string | null;
}

/** PUT tts-config 请求体。apiKey/baseUrl 语义同模型配置：空串=清除回退 yml，null=保留。 */
export interface SaveTtsConfigCommand {
  enabled?: boolean | null;
  provider?: string | null;
  baseUrl?: string | null;
  apiKey?: string | null;
  resourceId?: string | null;
  defaultSpeaker?: string | null;
  format?: string | null;
  sampleRate?: number | null;
  speechRate?: number | null;
  personaVoiceLink?: boolean | null;
}

/** 当前生效 TTS 配置。 */
export function useTtsConfig() {
  return useQuery({
    queryKey: ['system', 'tts-config'],
    queryFn: () => http.get<TtsConfigView>('/api/v1/system/tts-config'),
  });
}

/** 保存配置并立即生效。 */
export function useSaveTtsConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (command: SaveTtsConfigCommand) =>
      http.put<TtsConfigView>('/api/v1/system/tts-config', command),
    onSuccess: (view) => queryClient.setQueryData(['system', 'tts-config'], view),
  });
}

/** 恢复默认（清空 DB 覆盖，回退 yml）。 */
export function useResetTtsConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => http.post<TtsConfigView>('/api/v1/system/tts-config/reset'),
    onSuccess: (view) => queryClient.setQueryData(['system', 'tts-config'], view),
  });
}
