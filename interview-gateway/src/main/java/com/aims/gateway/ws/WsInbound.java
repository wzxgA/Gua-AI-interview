package com.aims.gateway.ws;

/**
 * WebSocket 客户端消息。
 *
 * @param roundId 当前展示题的轮次 ID（FE.12 P7：提交 ANSWER 时携带，后端按已答幂等校验防连点重入；可选）
 */
public record WsInbound(String type, String text, Long roundId) {

    /** 判断消息类型是否匹配（忽略大小写）。 */
    public boolean isType(String expected) {
        return type != null && type.equalsIgnoreCase(expected);
    }
}
