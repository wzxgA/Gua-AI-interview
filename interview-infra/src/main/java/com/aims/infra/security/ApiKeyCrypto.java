package com.aims.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * API Key 加密工具：AES-256-GCM 加密/解密 + 掩码回显。
 *
 * <p>密钥来源（优先级）：
 *
 * <ol>
 *   <li>环境变量 {@code AIMS_CONFIG_ENCRYPT_KEY}（base64，32 字节）
 *   <li>由 {@code AIMS_JWT_SECRET} 派生（SHA-256）
 *   <li>均缺失：warning 并退化「明文存储」——仅限开发环境，生产必须配置密钥
 * </ol>
 *
 * <p>密文格式：{@code v1:<base64(iv)>:<base64(ciphertextWithTag)>}（iv 12 字节随机，每次不同）。
 */
@Component
public class ApiKeyCrypto {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyCrypto.class);
    private static final String VERSION = "v1";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec key;
    private final boolean degradePlaintext;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCrypto(
            @Value("${aims.ai.config-store.encrypt-key:}") String encryptKey,
            @Value("${aims.security.jwt.secret:}") String jwtSecret) {
        byte[] derived = null;
        if (encryptKey != null && !encryptKey.isBlank()) {
            try {
                derived = Base64.getDecoder().decode(encryptKey.trim());
            } catch (IllegalArgumentException e) {
                log.warn("AIMS_CONFIG_ENCRYPT_KEY 不是合法 base64，尝试按 UTF-8 原样作为密钥源");
                derived = sha256(encryptKey.trim());
            }
            if (derived.length != 32) {
                log.warn("AIMS_CONFIG_ENCRYPT_KEY 解码后长度 {} != 32，退化为 SHA-256 派生", derived.length);
                derived = sha256(encryptKey.trim());
            }
        } else if (jwtSecret != null && !jwtSecret.isBlank()) {
            derived = sha256(jwtSecret);
        }

        if (derived == null) {
            this.degradePlaintext = true;
            this.key = null;
            log.warn("未配置 AIMS_CONFIG_ENCRYPT_KEY 且 AIMS_JWT_SECRET 为空，API Key 将以明文存储（仅限开发）");
        } else {
            this.degradePlaintext = false;
            this.key = new SecretKeySpec(derived, "AES");
        }
    }

    /** 加密；明文为空返回 null。 */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        if (degradePlaintext) {
            return plain;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return VERSION
                    + ":"
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /** 解密；密文为空返回 null。 */
    public String decrypt(String enc) {
        if (enc == null || enc.isEmpty()) {
            return null;
        }
        if (degradePlaintext) {
            return enc;
        }
        try {
            String[] parts = enc.split(":", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                log.warn("API Key 密文格式非法，视为明文处理");
                return enc;
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("API Key 解密失败", e);
            return null;
        }
    }

    /** 掩码回显：保留前 3 与后 4 字符，中间以 **** 代替；过短全部打星。 */
    public String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.length() <= 8) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    private static byte[] sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 派生密钥失败", e);
        }
    }
}
