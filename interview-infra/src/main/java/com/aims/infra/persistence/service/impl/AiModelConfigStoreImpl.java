package com.aims.infra.persistence.service.impl;

import com.aims.infra.persistence.entity.AiProviderConfigEntity;
import com.aims.infra.persistence.entity.AiTierConfigEntity;
import com.aims.infra.persistence.mapper.AiProviderConfigMapper;
import com.aims.infra.persistence.mapper.AiTierConfigMapper;
import com.aims.infra.persistence.service.AiModelConfigStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelConfigStoreImpl implements AiModelConfigStore {

    private final AiProviderConfigMapper providerMapper;
    private final AiTierConfigMapper tierMapper;

    public AiModelConfigStoreImpl(
            AiProviderConfigMapper providerMapper, AiTierConfigMapper tierMapper) {
        this.providerMapper = providerMapper;
        this.tierMapper = tierMapper;
    }

    @Override
    public List<AiProviderConfigEntity> listProviders() {
        return providerMapper.selectList(null);
    }

    @Override
    public List<AiTierConfigEntity> listTiers() {
        return tierMapper.selectList(null);
    }

    @Override
    public void upsertProvider(AiProviderConfigEntity entity) {
        providerMapper.upsert(entity);
    }

    @Override
    public void clearProviderApiKey(String name) {
        providerMapper.clearApiKey(name);
    }

    @Override
    public void upsertTier(AiTierConfigEntity entity) {
        tierMapper.upsert(entity);
    }

    @Override
    public void deleteProvider(String name) {
        providerMapper.deleteById(name);
    }

    @Override
    public void clearTierOverrideBaseUrl(String tier) {
        tierMapper.clearOverrideBaseUrl(tier);
    }

    @Override
    public void clearTierOverrideApiKey(String tier) {
        tierMapper.clearOverrideApiKey(tier);
    }

    @Override
    @Transactional
    public void resetAll() {
        providerMapper.delete(null);
        tierMapper.delete(null);
    }
}
