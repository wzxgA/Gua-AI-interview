package com.aims.infra.config;

import com.aims.infra.persistence.entity.TtsConfigEntity;
import com.aims.infra.persistence.service.TtsConfigStore;
import com.aims.infra.security.ApiKeyCrypto;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TTS 生效配置解析器：yml（{@link TtsProperties}）与 DB（{@code tts_config}）合并，DB 有值覆盖 yml。
 *
 * <p>每次 {@link #resolve()} 读取合并后的生效配置（含解密后的 apiKey），供 {@link
 * com.aims.infra.service.impl.VolcanoTtsService} 调用时实时取用。DB 单行读取极轻量，故做短缓存；保存后应调用 {@link
 * #invalidate()} 使新配置下一题立即生效。
 */
@Component
public class TtsConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(TtsConfigResolver.class);

    private final TtsProperties ymlBase;
    private final TtsConfigStore store;
    private final ApiKeyCrypto apiKeyCrypto;

    // 缓存：解析一次后复用；保存后 invalidate 强制重读。null 表示当前无有效缓存。
    private final AtomicReference<ResolvedTts> cached = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();

    public TtsConfigResolver(
            TtsProperties ymlBase, TtsConfigStore store, ApiKeyCrypto apiKeyCrypto) {
        this.ymlBase = ymlBase;
        this.store = store;
        this.apiKeyCrypto = apiKeyCrypto;
    }

    /** 当前生效的 TTS 配置（DB 覆盖 yml，apiKey 已解密）。 */
    public ResolvedTts resolve() {
        ResolvedTts hit = cached.get();
        if (hit != null) {
            return hit;
        }
        lock.lock();
        try {
            ResolvedTts again = cached.get();
            if (again != null) {
                return again;
            }
            ResolvedTts resolved = doResolve();
            cached.set(resolved);
            return resolved;
        } finally {
            lock.unlock();
        }
    }

    /** 保存后调用，使新配置立即生效。 */
    public void invalidate() {
        cached.set(null);
    }

    private ResolvedTts doResolve() {
        TtsConfigEntity db = store.current();
        ResolvedTts r = new ResolvedTts();
        // 布尔：DB 有值优先，否则 yml
        Boolean enabled = db != null ? db.getEnabled() : null;
        r.enabled = enabled != null ? enabled : ymlBase.enabled();
        r.provider =
                db != null && notBlank(db.getProvider()) ? db.getProvider() : ymlBase.provider();
        r.baseUrl = db != null && notBlank(db.getBaseUrl()) ? db.getBaseUrl() : ymlBase.baseUrl();
        // apiKey：DB 密文解密；空则回退 yml
        String dbApiKeyEnc = db != null ? db.getApiKeyEnc() : null;
        String dbApiKey = dbApiKeyEnc == null ? null : apiKeyCrypto.decrypt(dbApiKeyEnc);
        r.apiKey = notBlank(dbApiKey) ? dbApiKey : ymlBase.apiKey();
        r.resourceId =
                db != null && notBlank(db.getResourceId())
                        ? db.getResourceId()
                        : ymlBase.resourceId();
        r.defaultSpeaker =
                db != null && notBlank(db.getDefaultSpeaker())
                        ? db.getDefaultSpeaker()
                        : ymlBase.defaultSpeaker();
        r.format = db != null && notBlank(db.getFormat()) ? db.getFormat() : ymlBase.format();
        r.sampleRate =
                db != null && db.getSampleRate() != null
                        ? db.getSampleRate()
                        : ymlBase.sampleRate();
        r.speechRate =
                db != null && db.getSpeechRate() != null
                        ? db.getSpeechRate()
                        : ymlBase.speechRate();
        Boolean pl = db != null ? db.getPersonaVoiceLink() : null;
        r.personaVoiceLink = pl != null ? pl : ymlBase.personaVoiceLink();
        return r;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 生效的 TTS 配置（可写内部构建，对外只读字段）。 */
    public static final class ResolvedTts {
        private boolean enabled;
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String resourceId;
        private String defaultSpeaker;
        private String format;
        private int sampleRate;
        private int speechRate;
        private boolean personaVoiceLink;

        private ResolvedTts() {}

        public boolean enabled() {
            return enabled;
        }

        public String provider() {
            return provider;
        }

        public String baseUrl() {
            return baseUrl;
        }

        public String apiKey() {
            return apiKey;
        }

        public String resourceId() {
            return resourceId;
        }

        public String defaultSpeaker() {
            return defaultSpeaker;
        }

        public String format() {
            return format;
        }

        public int sampleRate() {
            return sampleRate;
        }

        public int speechRate() {
            return speechRate;
        }

        public boolean personaVoiceLink() {
            return personaVoiceLink;
        }
    }
}
