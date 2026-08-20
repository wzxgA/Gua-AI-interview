package com.aims.infra.persistence.service.impl;

import com.aims.infra.persistence.entity.TtsConfigEntity;
import com.aims.infra.persistence.mapper.TtsConfigMapper;
import com.aims.infra.persistence.service.TtsConfigStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TtsConfigStoreImpl implements TtsConfigStore {

    private final TtsConfigMapper mapper;

    public TtsConfigStoreImpl(TtsConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TtsConfigEntity current() {
        return mapper.selectById(1);
    }

    @Override
    public void upsert(TtsConfigEntity entity) {
        mapper.upsert(entity);
    }

    @Override
    public void clearBaseUrl() {
        mapper.clearBaseUrl();
    }

    @Override
    public void clearApiKey() {
        mapper.clearApiKey();
    }

    @Override
    public void clearResourceId() {
        mapper.clearResourceId();
    }

    @Override
    public void clearDefaultSpeaker() {
        mapper.clearDefaultSpeaker();
    }

    @Override
    @Transactional
    public void resetAll() {
        mapper.deleteRow();
    }
}
