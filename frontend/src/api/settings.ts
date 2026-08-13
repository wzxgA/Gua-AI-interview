import { useQuery } from '@tanstack/react-query';
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
