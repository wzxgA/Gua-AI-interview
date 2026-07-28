import { useQuery } from '@tanstack/react-query';

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      try {
        const res = await fetch('/actuator/health');
        if (!res.ok) return { status: 'DOWN' };
        const data = await res.json();
        return data as { status: string };
      } catch {
        return { status: 'DOWN' };
      }
    },
    refetchInterval: 30000,
  });
}
