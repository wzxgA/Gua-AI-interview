package com.aims.infra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.infra.config.TtsConfigResolver.ResolvedTts;
import com.aims.infra.persistence.entity.TtsConfigEntity;
import com.aims.infra.persistence.service.TtsConfigStore;
import com.aims.infra.security.ApiKeyCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link TtsConfigResolver} 单元测试：DB 覆盖 yml、apiKey 解密、无缓存命中时回退 yml、invalidate 强制重读。 */
class TtsConfigResolverTest {

    private static final TtsProperties YML =
            new TtsProperties(
                    false,
                    "volc",
                    "yml-key",
                    "seed-tts-2.0",
                    "https://yml",
                    "yml-speaker",
                    "mp3",
                    24000,
                    1,
                    false);

    private TtsConfigStore store;
    private ApiKeyCrypto crypto;
    private TtsConfigResolver resolver;

    @BeforeEach
    void setUp() {
        store = mock(TtsConfigStore.class);
        crypto = new ApiKeyCrypto("", "test-jwt-secret");
        resolver = new TtsConfigResolver(YML, store, crypto);
    }

    @Test
    void resolve_noDbRow_fallsBackToYml() {
        when(store.current()).thenReturn(null);

        ResolvedTts r = resolver.resolve();

        assertFalse(r.enabled());
        assertEquals("volc", r.provider());
        assertEquals("yml-key", r.apiKey());
        assertEquals("seed-tts-2.0", r.resourceId());
        assertEquals("https://yml", r.baseUrl());
        assertEquals("yml-speaker", r.defaultSpeaker());
        assertEquals(24000, r.sampleRate());
        assertFalse(r.personaVoiceLink());
    }

    @Test
    void resolve_dbOverridesYmlAndDecryptsApiKey() {
        TtsConfigEntity db = new TtsConfigEntity();
        db.setEnabled(true);
        db.setBaseUrl("https://db");
        db.setResourceId("db-rid");
        db.setDefaultSpeaker("db-speaker");
        db.setSampleRate(48000);
        db.setPersonaVoiceLink(true);
        // 只覆盖上述字段；provider/format/speechRate 保持 NULL，应沿用 yml
        db.setApiKeyEnc(crypto.encrypt("db-key"));
        when(store.current()).thenReturn(db);

        ResolvedTts r = resolver.resolve();

        assertTrue(r.enabled());
        assertEquals("db-key", r.apiKey());
        assertEquals("https://db", r.baseUrl());
        assertEquals("db-rid", r.resourceId());
        assertEquals("db-speaker", r.defaultSpeaker());
        assertEquals(48000, r.sampleRate());
        assertTrue(r.personaVoiceLink());
        // 未覆盖字段沿用 yml
        assertEquals("volc", r.provider());
        assertEquals("mp3", r.format());
        assertEquals(1, r.speechRate());
    }

    @Test
    void resolve_keepsCacheUntilInvalidate() {
        when(store.current()).thenReturn(null);
        ResolvedTts before = resolver.resolve();
        assertEquals("yml-key", before.apiKey());

        TtsConfigEntity db = new TtsConfigEntity();
        db.setApiKeyEnc(crypto.encrypt("db-key"));
        when(store.current()).thenReturn(db);

        // 未失效缓存：仍返回 yml
        assertEquals("yml-key", resolver.resolve().apiKey());
        // 失效后重读 DB
        resolver.invalidate();
        assertEquals("db-key", resolver.resolve().apiKey());
    }
}
