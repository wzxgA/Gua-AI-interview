package com.aims.gateway.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

/** WebSocket 服务端消息。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsOutbound(
        String type,
        Long sessionId,
        Long roundId,
        Integer seq,
        String status,
        Integer code,
        String message,
        String text) {

    public static WsOutbound sessionReady(Long sessionId, String status) {
        return new WsOutbound("SESSION_READY", sessionId, null, null, status, null, null, null);
    }

    public static WsOutbound questionStart(Long sessionId, Long roundId, int seq) {
        return new WsOutbound("QUESTION_START", sessionId, roundId, seq, null, null, null, null);
    }

    public static WsOutbound questionChunk(Long sessionId, Long roundId, String text) {
        return new WsOutbound("QUESTION_CHUNK", sessionId, roundId, null, null, null, null, text);
    }

    public static WsOutbound questionEnd(Long sessionId, Long roundId, int seq, String text) {
        return new WsOutbound("QUESTION_END", sessionId, roundId, seq, null, null, null, text);
    }

    public static WsOutbound answerAck(Long sessionId, Long roundId) {
        return new WsOutbound("ANSWER_ACK", sessionId, roundId, null, null, null, null, null);
    }

    public static WsOutbound status(Long sessionId, String status) {
        return new WsOutbound("STATUS", sessionId, null, null, status, null, null, null);
    }

    public static WsOutbound heartbeatAck(Long sessionId) {
        return new WsOutbound("HEARTBEAT_ACK", sessionId, null, null, null, null, null, null);
    }

    public static WsOutbound completed(Long sessionId) {
        return new WsOutbound(
                "SESSION_COMPLETED", sessionId, null, null, "COMPLETED", null, null, null);
    }

    public static WsOutbound error(int code, String message) {
        return new WsOutbound("ERROR", null, null, null, null, code, message, null);
    }
}
