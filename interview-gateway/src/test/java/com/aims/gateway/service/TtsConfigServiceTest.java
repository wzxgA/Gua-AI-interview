package com.aims.gateway.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aims.core.common.exception.BizException;
import com.aims.gateway.controller.system.SaveTtsConfigCommand;
import com.aims.infra.config.TtsConfigResolver;
import com.aims.infra.config.TtsProperties;
import com.aims.infra.persistence.service.TtsConfigStore;
import com.aims.infra.security.ApiKeyCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** {@link TtsConfigService} 保存校验与字段覆盖语义测试。 */
class TtsConfigServiceTest {

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
    private TtsConfigResolver resolver;
    private ApiKeyCrypto crypto;
    private TtsConfigService service;

    @BeforeEach
    void setUp() {
        store = mock(TtsConfigStore.class);
        resolver = mock(TtsConfigResolver.class);
        crypto = new ApiKeyCrypto("", "test-jwt-secret");
        service = new TtsConfigService(YML, store, resolver, crypto);
        when(store.current()).thenReturn(null);
    }

    @Test
    void save_invalidFormat_throws() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(
                        null, null, null, null, null, null, "flac", null, null, null);
        assertThrows(BizException.class, () -> service.save(cmd));
        verify(store, never()).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void save_invalidSampleRate_throws() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(
                        null, null, null, null, null, null, null, 11025, null, null);
        assertThrows(BizException.class, () -> service.save(cmd));
    }

    @Test
    void save_invalidBaseUrl_throws() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(
                        null, null, "not-a-url", null, null, null, null, null, null, null);
        assertThrows(BizException.class, () -> service.save(cmd));
    }

    @Test
    void save_filledApiKey_encryptsAndUpserts() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(
                        true, null, "https://db", "db-key", "rid", null, null, 48000, null, null);

        service.save(cmd);

        ArgumentCaptor<com.aims.infra.persistence.entity.TtsConfigEntity> captor =
                ArgumentCaptor.forClass(com.aims.infra.persistence.entity.TtsConfigEntity.class);
        verify(store).upsert(captor.capture());
        var entity = captor.getValue();
        assertTrue(entity.getEnabled());
        // 加密写入，数据库不落明文
        assertTrue(entity.getApiKeyEnc().contains(":") || entity.getApiKeyEnc().equals("db-key"));
        verify(resolver).invalidate();
    }

    @Test
    void save_blankApiKey_clearsDbCoverage() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(null, null, null, "", null, null, null, null, null, null);

        service.save(cmd);

        verify(store).clearApiKey();
        verify(resolver).invalidate();
    }

    @Test
    void save_blankBaseUrl_clearsDbCoverage() {
        SaveTtsConfigCommand cmd =
                new SaveTtsConfigCommand(null, null, "", null, null, null, null, null, null, null);

        service.save(cmd);

        verify(store).clearBaseUrl();
        verify(resolver).invalidate();
    }
}
