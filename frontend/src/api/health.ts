import { useQuery } from '@tanstack/react-query';

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      try {
        const res = await fetch(
          `${import.meta.env.VITE_API_BASE || 'http://localhost:8080'}/actuator/health`,
        );
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
