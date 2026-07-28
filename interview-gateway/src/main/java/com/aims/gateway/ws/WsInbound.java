package com.aims.gateway.ws;

/** WebSocket 客户端消息。 */
public record WsInbound(String type, String text) {

    /** 判断消息类型是否匹配（忽略大小写）。 */
    public boolean isType(String expected) {
        return type != null && type.equalsIgnoreCase(expected);
    }
}
