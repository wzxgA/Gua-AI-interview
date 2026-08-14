package com.aims.gateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 候选人 guestToken 服务：密码验证通过后签发，供候选人后续 REST + WebSocket 鉴权。
 *
 * <p>guestToken 为 JWT（role=GUEST），携带 sid（会话 ID），只能访问绑定会话的资源。
 */
@Component
public class GuestTokenService {

    private static final String CLAIM_SID = "sid";

    private final JwtUtil jwtUtil;
    private final long guestTtlSeconds;

    public GuestTokenService(
            JwtUtil jwtUtil, @Value("${aims.security.jwt.guest-ttl:7200}") long guestTtlSeconds) {
        this.jwtUtil = jwtUtil;
        this.guestTtlSeconds = guestTtlSeconds;
    }

    /** 为指定会话签发 guestToken。 */
    public String issueGuestToken(Long sessionId) {
        return jwtUtil.generateGuestToken(sessionId, guestTtlSeconds);
    }

    /** 从 guestToken 的 claims 中提取绑定的会话 ID；非 GUEST 或无 sid 返回 null。 */
    public static Long extractSessionId(Claims claims) {
        if (claims == null || !"GUEST".equals(claims.get("role", String.class))) {
            return null;
        }
        Number sid = claims.get(CLAIM_SID, Number.class);
        return sid == null ? null : sid.longValue();
    }
}
