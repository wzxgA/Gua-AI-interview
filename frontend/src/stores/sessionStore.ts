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
  addSystem: (status: SessionStatus, finishedBy?: string, finishReason?: string) => void;
  startQuestion: (roundId: number | undefined, seq: number | undefined, followUpType?: string, parentSeq?: number, followUpIndex?: number) => void;
  appendChunk: (text: string) => void;
  finalizeQuestion: (roundId?: number) => void;
  addAnswer: (text: string, roundId?: number) => void;
  addQuestion: (roundId: number | undefined, seq: number | undefined, text: string, followUpType?: string, parentSeq?: number, followUpIndex?: number) => void;
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

  /** 注入一条结束回执系统消息（按路径身份 + 状态去重，避免重复/重连恢复后仍仅一条）。 */
  addSystem: (status, finishedBy, finishReason) =>
    set((state) => {
      const already = state.messages.some(
        (m) =>
          m.role === 'system' &&
          m.status === status &&
          m.finishedBy === (finishedBy ?? null) &&
          m.finishReason === (finishReason ?? null),
      );
      if (already) return state;
      return {
        messages: [
          ...state.messages,
          {
            id: nextId(),
            role: 'system',
            text: '',
            status,
            finishedBy: finishedBy ?? null,
            finishReason: finishReason ?? null,
            timestamp: new Date().toISOString(),
          },
        ],
      };
    }),

  startQuestion: (roundId, seq, followUpType, parentSeq, followUpIndex) =>
    set((state) => {
      // 追问去重键：parentSeq + followUpIndex；主问题去重键：roundId 或 seq
      const isFollowUp = parentSeq != null && followUpIndex != null;
      if (
        state.messages.some((m) => {
          if (m.role !== 'question') return false;
          if (isFollowUp) {
            return m.parentSeq === parentSeq && m.followUpIndex === followUpIndex;
          }
          return m.roundId === roundId || m.seq === seq;
        })
      ) {
        return state;
      }
      return {
        currentRoundId: roundId ?? null,
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

  finalizeQuestion: (roundId?: number) =>
    set((state) => {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last && last.role === 'question' && last.streaming) {
        // END 携带 roundId 时绑定到气泡（Engine 预落库回传；旧链路亦有）
        messages[messages.length - 1] =
          roundId != null
            ? { ...last, roundId, streaming: false }
            : { ...last, streaming: false };
      }
      return {
        messages,
        isStreaming: false,
        // 问题结束并绑定 roundId 后，回答应关联该轮次；否则本地回答 roundId=undefined，
        // 与历史恢复 addAnswer(r.id) 去重键不一致 → 同一回答重复显示
        currentRoundId: roundId ?? state.currentRoundId,
      };
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
      // 追问去重键：parentSeq + followUpIndex；主问题去重键：roundId 或 seq
      const isFollowUp = parentSeq != null && followUpIndex != null;
      if (
        state.messages.some((m) => {
          if (m.role !== 'question') return false;
          if (isFollowUp) {
            return m.parentSeq === parentSeq && m.followUpIndex === followUpIndex;
          }
          return m.roundId === roundId || m.seq === seq;
        })
      ) {
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
