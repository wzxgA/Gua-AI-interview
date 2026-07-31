package com.aims.gateway.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** RefreshToken 的 Redis 存储/校验/删除。 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "aims:refresh:";

    private final StringRedisTemplate redis;
    private final long refreshTtl;

    public RefreshTokenService(
            StringRedisTemplate redis,
            @Value("${aims.security.jwt.refresh-ttl:604800}") long refreshTtl) {
        this.redis = redis;
        this.refreshTtl = refreshTtl;
    }

    /** 存储 refreshToken，关联 username。 */
    public void save(String refreshToken, String username) {
        redis.opsForValue()
                .set(KEY_PREFIX + refreshToken, username, Duration.ofSeconds(refreshTtl));
    }

    /** 校验 refreshToken 是否有效，返回关联的 username，无效返回 null。 */
    public String validate(String refreshToken) {
        return redis.opsForValue().get(KEY_PREFIX + refreshToken);
    }

    /** 删除 refreshToken（登出时调用）。 */
    public void revoke(String refreshToken) {
        redis.delete(KEY_PREFIX + refreshToken);
    }
}
