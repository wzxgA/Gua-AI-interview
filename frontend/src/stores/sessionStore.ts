import { create } from 'zustand';
import type { SessionStatus, ChatMessage } from '@/types/interview';

/** 面试会话状态管理 */
interface SessionStore {
  sessionId: number | null;
  status: SessionStatus;
  currentRoundId: number | null;
  currentQuestion: string;
  isStreaming: boolean;
  messages: ChatMessage[];
  isConnected: boolean;
  retryCount: number;
  error: string | null;

  // Actions
  setSession: (sessionId: number, status: SessionStatus) => void;
  setStatus: (status: SessionStatus) => void;
  startQuestion: (roundId: number, seq: number | undefined, followUpType?: string, parentSeq?: number, followUpIndex?: number) => void;
  appendChunk: (text: string) => void;
  finalizeQuestion: () => void;
  addAnswer: (text: string, roundId?: number) => void;
  addQuestion: (roundId: number, seq: number | undefined, text: string, followUpType?: string, parentSeq?: number, followUpIndex?: number) => void;
  setAudio: (roundId: number, audioUrl: string, durationMs?: number) => void;
  hasRound: (roundId: number) => boolean;
  setConnected: (connected: boolean) => void;
  incrementRetry: () => void;
  resetRetry: () => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

const initialState = {
  sessionId: null as number | null,
  status: 'CREATED' as SessionStatus,
  currentRoundId: null as number | null,
  currentQuestion: '',
  isStreaming: false,
  messages: [] as ChatMessage[],
  isConnected: false,
  retryCount: 0,
  error: null as string | null,
};

let messageSeq = 0;
const nextId = () => `msg-${++messageSeq}`;

export const useSessionStore = create<SessionStore>((set, get) => ({
  ...initialState,

  setSession: (sessionId, status) =>
    set({ sessionId, status }),

  setStatus: (status) => set({ status }),

  startQuestion: (roundId, seq, followUpType, parentSeq, followUpIndex) =>
    set((state) => {
      if (state.messages.some((m) => m.role === 'question' && m.roundId === roundId)) {
        return state;
      }
      return {
        currentRoundId: roundId,
        currentQuestion: '',
        isStreaming: true,
        messages: [
          ...state.messages,
          {
            id: nextId(),
            role: 'question',
            text: '',
            roundId,
            seq,
            followUpType,
            parentSeq,
            followUpIndex,
            timestamp: new Date().toISOString(),
            streaming: true,
          },
        ],
      };
    }),

  appendChunk: (text) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'question' && last.streaming) {
        messages[messages.length - 1] = {
          ...last,
          text: last.text + text,
        };
      }
      return {
        messages,
        currentQuestion:
          state.currentQuestion + text,
      };
    }),

  finalizeQuestion: () =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'question' && last.streaming) {
        messages[messages.length - 1] = { ...last, streaming: false };
      }
      return { messages, isStreaming: false };
    }),

  addAnswer: (text, roundId) =>
    set((state) => {
      if (roundId != null && state.messages.some((m) => m.role === 'answer' && m.roundId === roundId)) {
        return state;
      }
      return {
        messages: [
          ...state.messages,
          {
            id: nextId(),
            role: 'answer' as const,
            text,
            roundId,
            timestamp: new Date().toISOString(),
          },
        ],
      };
    }),

  addQuestion: (roundId, seq, text, followUpType, parentSeq, followUpIndex) =>
    set((state) => {
      if (state.messages.some((m) => m.role === 'question' && m.roundId === roundId)) {
        return state;
      }
      return {
        messages: [
          ...state.messages,
          {
            id: nextId(),
            role: 'question',
            text,
            roundId,
            seq,
            followUpType,
            parentSeq,
            followUpIndex,
            timestamp: new Date().toISOString(),
            streaming: false,
          },
        ],
      };
    }),

  setAudio: (roundId, audioUrl, durationMs) =>
    set((state) => ({
      messages: state.messages.map((m) =>
        m.roundId === roundId ? { ...m, audioUrl, durationMs } : m,
      ),
    })),

  hasRound: (roundId) =>
    get().messages.some((m) => m.role === 'question' && m.roundId === roundId),

  setConnected: (connected) =>
    set(connected ? { isConnected: true, retryCount: 0 } : { isConnected: false }),

  incrementRetry: () =>
    set((state) => ({ retryCount: state.retryCount + 1 })),

  resetRetry: () => set({ retryCount: 0 }),

  setError: (error) => set({ error }),

  reset: () => set({ ...initialState, messages: [] }),
}));
