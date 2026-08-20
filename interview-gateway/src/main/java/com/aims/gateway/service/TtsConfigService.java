package com.aims.gateway.service;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.gateway.controller.system.SaveTtsConfigCommand;
import com.aims.gateway.controller.system.TtsConfigView;
import com.aims.infra.config.TtsConfigResolver;
import com.aims.infra.config.TtsProperties;
import com.aims.infra.persistence.entity.TtsConfigEntity;
import com.aims.infra.persistence.service.TtsConfigStore;
import com.aims.infra.security.ApiKeyCrypto;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TTS 连接配置管理：yml 与 DB 增量合并、API Key 加密存储、保存后立即失效缓存使下一题生效。
 *
 * <p>合并语义：DB 该行有值的字段覆盖 yml（含环境变量）；无值/无行沿用 yml。保存透传给 {@link
 * TtsConfigResolver#invalidate()}，使火山合成下次取到新配置。
 */
@Service
public class TtsConfigService {

    private static final Logger log = LoggerFactory.getLogger(TtsConfigService.class);

    private static final Set<String> VALID_FORMATS = Set.of("mp3", "wav", "pcm");
    private static final Set<Integer> VALID_SAMPLE_RATES = Set.of(16000, 24000, 48000);

    private final TtsProperties ymlBase;
    private final TtsConfigStore store;
    private final TtsConfigResolver resolver;
    private final ApiKeyCrypto apiKeyCrypto;

    public TtsConfigService(
            TtsProperties ymlBase,
            TtsConfigStore store,
            TtsConfigResolver resolver,
            ApiKeyCrypto apiKeyCrypto) {
        this.ymlBase = ymlBase;
        this.store = store;
        this.resolver = resolver;
        this.apiKeyCrypto = apiKeyCrypto;
    }

    /** 当前生效配置（yml + DB 合并，apiKey 掩码回显、每字段来源）。 */
    public TtsConfigView getConfig() {
        return toView(store.current());
    }

    /** 保存配置并立即生效（下一题合成即用新配置）。事务性：DB 写入与缓存失效原子。 */
    @Transactional(rollbackFor = Exception.class)
    public TtsConfigView save(SaveTtsConfigCommand cmd) {
        validate(cmd);
        String operator = currentUsername();

        TtsConfigEntity e = new TtsConfigEntity();
        e.setEnabled(cmd.enabled());
        e.setProvider(blankToNull(cmd.provider()));
        e.setResourceId(blankToNull(cmd.resourceId()));
        e.setDefaultSpeaker(blankToNull(cmd.defaultSpeaker()));
        e.setFormat(blankToNull(cmd.format()));
        e.setSampleRate(cmd.sampleRate());
        e.setSpeechRate(cmd.speechRate());
        e.setPersonaVoiceLink(cmd.personaVoiceLink());
        e.setUpdatedBy(operator);

        // baseUrl / apiKey / resourceId / defaultSpeaker 支持空串回退 yml（COALESCE 无法主动置 NULL，需独立 clear）
        if (cmd.baseUrl() != null) {
            if (cmd.baseUrl().isBlank()) {
                store.clearBaseUrl();
            } else {
                e.setBaseUrl(cmd.baseUrl());
            }
        }
        if (cmd.apiKey() != null) {
            if (cmd.apiKey().isBlank()) {
                store.clearApiKey();
            } else {
                e.setApiKeyEnc(apiKeyCrypto.encrypt(cmd.apiKey()));
            }
        }
        if (cmd.resourceId() != null && cmd.resourceId().isBlank()) {
            store.clearResourceId();
            e.setResourceId(null);
        }
        if (cmd.defaultSpeaker() != null && cmd.defaultSpeaker().isBlank()) {
            store.clearDefaultSpeaker();
            e.setDefaultSpeaker(null);
        }

        store.upsert(e);
        resolver.invalidate();
        log.info("TTS 配置已保存并立即生效 operator={}", operator);
        return getConfig();
    }

    /** 清空 DB 覆盖配置，整体回退 yml 并立即生效。 */
    @Transactional(rollbackFor = Exception.class)
    public TtsConfigView reset() {
        store.resetAll();
        resolver.invalidate();
        return getConfig();
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    private void validate(SaveTtsConfigCommand cmd) {
        if (cmd.format() != null
                && !cmd.format().isBlank()
                && !VALID_FORMATS.contains(cmd.format().toLowerCase())) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "format 仅支持 mp3 / wav / pcm，当前=" + cmd.format());
        }
        if (cmd.sampleRate() != null && !VALID_SAMPLE_RATES.contains(cmd.sampleRate())) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "sampleRate 仅支持 16000 / 24000 / 48000，当前=" + cmd.sampleRate());
        }
        if (cmd.speechRate() != null && (cmd.speechRate() < 0 || cmd.speechRate() > 5)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "speechRate 需在 [0, 5]，当前=" + cmd.speechRate());
        }
        if (cmd.baseUrl() != null
                && !cmd.baseUrl().isBlank()
                && !cmd.baseUrl().startsWith("http://")
                && !cmd.baseUrl().startsWith("https://")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "baseUrl 必须是 http(s):// 开头的合法地址");
        }
    }

    // ------------------------------------------------------------------
    // 视图
    // ------------------------------------------------------------------

    private TtsConfigView toView(TtsConfigEntity db) {
        return new TtsConfigView(
                eff(db != null ? db.getEnabled() : null, ymlBase.enabled()),
                src(db, TtsConfigEntity::getEnabled),
                eff(db != null ? db.getProvider() : null, ymlBase.provider()),
                src(db, TtsConfigEntity::getProvider),
                eff(db != null ? db.getBaseUrl() : null, ymlBase.baseUrl()),
                src(db, TtsConfigEntity::getBaseUrl),
                maskedApiKey(db),
                src(db, TtsConfigEntity::getApiKeyEnc),
                eff(db != null ? db.getResourceId() : null, ymlBase.resourceId()),
                src(db, TtsConfigEntity::getResourceId),
                eff(db != null ? db.getDefaultSpeaker() : null, ymlBase.defaultSpeaker()),
                src(db, TtsConfigEntity::getDefaultSpeaker),
                eff(db != null ? db.getFormat() : null, ymlBase.format()),
                src(db, TtsConfigEntity::getFormat),
                eff(db != null ? db.getSampleRate() : null, ymlBase.sampleRate()),
                src(db, TtsConfigEntity::getSampleRate),
                eff(db != null ? db.getSpeechRate() : null, ymlBase.speechRate()),
                src(db, TtsConfigEntity::getSpeechRate),
                eff(db != null ? db.getPersonaVoiceLink() : null, ymlBase.personaVoiceLink()),
                src(db, TtsConfigEntity::getPersonaVoiceLink),
                db != null ? db.getUpdatedBy() : null,
                db != null ? db.getUpdatedAt() : null);
    }

    /** apiKey 掩码回显：优先 DB 解密，否则 yml 明文掩码。 */
    private String maskedApiKey(TtsConfigEntity db) {
        String plain;
        if (db != null && db.getApiKeyEnc() != null) {
            plain = apiKeyCrypto.decrypt(db.getApiKeyEnc());
        } else {
            plain = ymlBase.apiKey();
        }
        return apiKeyCrypto.mask(plain);
    }

    /** 字段来源：DB 有值即 db，否则 yml。 */
    private static <T> String src(
            TtsConfigEntity db, java.util.function.Function<TtsConfigEntity, T> getter) {
        return db != null && getter.apply(db) != null ? "db" : "yml";
    }

    /** 取 DB 有值优先、否则 yml 的生效值。 */
    private static <T> T eff(T dbVal, T ymlVal) {
        return dbVal != null ? dbVal : ymlVal;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
