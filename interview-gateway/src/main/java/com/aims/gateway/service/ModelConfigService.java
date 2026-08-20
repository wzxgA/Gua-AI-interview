package com.aims.gateway.service;

import com.aims.ai.config.AiModelProperties;
import com.aims.ai.config.ModelHandleFactory;
import com.aims.ai.router.ModelHandle;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.gateway.controller.system.ModelConfigTestResult;
import com.aims.gateway.controller.system.ModelConfigView;
import com.aims.gateway.controller.system.SaveModelConfigCommand;
import com.aims.infra.persistence.entity.AiProviderConfigEntity;
import com.aims.infra.persistence.entity.AiTierConfigEntity;
import com.aims.infra.persistence.service.AiModelConfigStore;
import com.aims.infra.security.ApiKeyCrypto;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 模型配置管理：yml 与 DB 增量合并、API Key 加密存储、热刷新模型路由、连通性测试。
 *
 * <p>合并语义：DB 有值的字段覆盖 yml（含环境变量）；DB 无值/无行则沿用 yml。档位级 override url/key 通过创建「{@code
 * <TIER>@override}」专用 provider 实现，不污染共享 provider。
 */
@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private final AiModelProperties ymlBase;
    private final AiModelConfigStore store;
    private final ModelRouter modelRouter;
    private final ModelHandleFactory modelHandleFactory;
    private final ApiKeyCrypto apiKeyCrypto;

    public ModelConfigService(
            AiModelProperties ymlBase,
            AiModelConfigStore store,
            ModelRouter modelRouter,
            ModelHandleFactory modelHandleFactory,
            ApiKeyCrypto apiKeyCrypto) {
        this.ymlBase = ymlBase;
        this.store = store;
        this.modelRouter = modelRouter;
        this.modelHandleFactory = modelHandleFactory;
        this.apiKeyCrypto = apiKeyCrypto;
    }

    // ------------------------------------------------------------------
    // 查询 / 保存 / 重置
    // ------------------------------------------------------------------

    /** 当前生效配置（yml + DB 合并，key 掩码回显）。 */
    public ModelConfigView getConfig() {
        Map<String, AiProviderConfigEntity> dbProviders =
                toMap(store.listProviders(), AiProviderConfigEntity::getName);
        Map<String, AiTierConfigEntity> dbTiers =
                toMap(store.listTiers(), AiTierConfigEntity::getTier);

        // 与路由热刷新（mergeEffective）共用同一套合并逻辑：DB 覆盖 yml，保证回显即生效配置
        Map<String, AiModelProperties.ProviderConfig> mergedProviders = mergeProviders(dbProviders);
        Map<ModelTier, AiModelProperties.TierConfig> mergedTiers = new EnumMap<>(ModelTier.class);
        ymlBase.tiers()
                .forEach(
                        (tier, cfg) ->
                                mergedTiers.put(
                                        tier,
                                        mergeTier(
                                                tier,
                                                cfg,
                                                dbTiers.get(tier.name()),
                                                mergedProviders)));

        List<ModelConfigView.ProviderView> providers = new ArrayList<>();
        mergedProviders.forEach(
                (name, cfg) -> {
                    // 档位 override 动态创建的 "<TIER>@override" provider 不展示
                    if (name.endsWith("@override")) {
                        return;
                    }
                    providers.add(toProviderView(name, cfg, dbProviders.get(name)));
                });

        List<ModelConfigView.TierView> tiers = new ArrayList<>();
        mergedTiers.forEach(
                (tier, cfg) -> tiers.add(toTierView(tier, cfg, dbTiers.get(tier.name()))));

        return new ModelConfigView(ymlBase.defaultTier().name(), providers, tiers);
    }

    /** 当前生效的完整配置（yml + DB 合并），供只读展示类接口（如 model-tiers）复用。 */
    public AiModelProperties effectiveProperties() {
        return mergeEffective();
    }

    /**
     * 保存配置并立即热刷新路由，返回保存后的生效配置。
     *
     * <p>事务性：DB 写入与路由刷新原子——任一失败整体回滚，避免「DB 有新配置但路由未生效」的脏状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigView save(SaveModelConfigCommand command) {
        String operator = currentUsername();
        if (command.providers() != null) {
            for (SaveModelConfigCommand.ProviderItem item : command.providers()) {
                applyProviderSave(item, operator);
            }
        }
        if (command.tiers() != null) {
            for (SaveModelConfigCommand.TierItem item : command.tiers()) {
                applyTierSave(item, operator);
            }
        }
        refreshRouter();
        return getConfig();
    }

    /** 清空 DB 覆盖配置并热刷新路由（整体回退 yml）。 */
    public ModelConfigView reset() {
        store.resetAll();
        refreshRouter();
        return getConfig();
    }

    /** 删除自定义 provider（yml 内置 provider 不可删；正被档位引用时拒绝）。 */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigView deleteProvider(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "provider name 不能为空");
        }
        if (ymlBase.providers().containsKey(name)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "内置 provider " + name + " 不可删除");
        }
        boolean referenced = store.listTiers().stream().anyMatch(t -> name.equals(t.getProvider()));
        if (referenced) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "provider " + name + " 正被档位引用，请先调整档位配置");
        }
        store.deleteProvider(name);
        refreshRouter();
        return getConfig();
    }

    /** 连通性测试：用请求体配置（不落库）临时合并后对各档位发起最小调用。 */
    public ModelConfigTestResult test(SaveModelConfigCommand command) {
        AiModelProperties trial = mergeTrial(command);
        Map<String, OpenAiApi> apis = modelHandleFactory.buildApis(trial);
        Map<ModelTier, ModelHandle> handles = modelHandleFactory.buildHandles(trial, apis);

        List<ModelConfigTestResult.TierResult> results = new ArrayList<>();
        handles.forEach(
                (tier, handle) -> {
                    long start = System.currentTimeMillis();
                    try {
                        if (handle.embeddingModel() != null) {
                            handle.embeddingModel().embed("ping");
                        } else {
                            handle.chatClient().prompt().user("ping").call().content();
                        }
                        results.add(
                                new ModelConfigTestResult.TierResult(
                                        tier.name(),
                                        true,
                                        System.currentTimeMillis() - start,
                                        null));
                    } catch (Exception e) {
                        log.warn("模型连通性测试失败 tier={} error={}", tier, e.toString());
                        results.add(
                                new ModelConfigTestResult.TierResult(
                                        tier.name(),
                                        false,
                                        System.currentTimeMillis() - start,
                                        safeMessage(e)));
                    }
                });
        return new ModelConfigTestResult(results);
    }

    // ------------------------------------------------------------------
    // 保存逻辑
    // ------------------------------------------------------------------

    private void applyProviderSave(SaveModelConfigCommand.ProviderItem item, String operator) {
        if (item.name() == null || item.name().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "provider name 不能为空");
        }
        if (item.baseUrl() == null || item.baseUrl().isBlank()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "provider " + item.name() + " 的 baseUrl 不能为空");
        }
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setName(item.name());
        entity.setBaseUrl(item.baseUrl());
        entity.setMaxConcurrency(item.maxConcurrency());
        entity.setUpdatedBy(operator);
        if (item.apiKey() != null) {
            if (item.apiKey().isBlank()) {
                store.clearProviderApiKey(item.name()); // 空串：清除 DB 覆盖，回退 yml
                return;
            }
            entity.setApiKeyEnc(apiKeyCrypto.encrypt(item.apiKey()));
        } else {
            // 未传：保留 DB 旧值（upsert 的 COALESCE 不覆盖 NULL）
        }
        store.upsertProvider(entity);
    }

    private void applyTierSave(SaveModelConfigCommand.TierItem item, String operator) {
        if (item.tier() == null || item.tier().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "tier 不能为空");
        }
        AiTierConfigEntity entity = new AiTierConfigEntity();
        entity.setTier(item.tier());
        entity.setProvider(item.provider());
        entity.setModel(item.model());
        entity.setTemperature(
                item.temperature() != null
                        ? java.math.BigDecimal.valueOf(item.temperature())
                        : null);
        entity.setMaxTokens(item.maxTokens());
        entity.setDimensions(item.dimensions());
        entity.setFallback(item.fallback());
        entity.setThinking(item.thinking());
        entity.setReasoningEffort(item.reasoningEffort());
        entity.setOverrideBaseUrl(item.overrideBaseUrl());
        entity.setUpdatedBy(operator);

        if (item.overrideApiKey() != null) {
            if (item.overrideApiKey().isBlank()) {
                store.clearTierOverrideApiKey(item.tier()); // 空串：清除 override key
            } else {
                entity.setOverrideApiKeyEnc(apiKeyCrypto.encrypt(item.overrideApiKey()));
            }
        }
        if (item.overrideBaseUrl() != null && item.overrideBaseUrl().isBlank()) {
            store.clearTierOverrideBaseUrl(item.tier()); // 空串：清除 override url
            entity.setOverrideBaseUrl(null);
        }
        store.upsertTier(entity);
    }

    // ------------------------------------------------------------------
    // 合并逻辑
    // ------------------------------------------------------------------

    /** 用 DB 覆盖层合并 yml，得到当前生效配置。 */
    private AiModelProperties mergeEffective() {
        Map<String, AiProviderConfigEntity> dbProviders =
                toMap(store.listProviders(), AiProviderConfigEntity::getName);
        Map<String, AiTierConfigEntity> dbTiers =
                toMap(store.listTiers(), AiTierConfigEntity::getTier);
        return merge(dbProviders, dbTiers);
    }

    /** 用请求体配置（不落库）临时合并，用于连通性测试。 */
    private AiModelProperties mergeTrial(SaveModelConfigCommand command) {
        // 先以 DB 覆盖为基准，再用请求体非空字段继续覆盖
        Map<String, AiProviderConfigEntity> dbProviders =
                toMap(store.listProviders(), AiProviderConfigEntity::getName);
        Map<String, AiTierConfigEntity> dbTiers =
                toMap(store.listTiers(), AiTierConfigEntity::getTier);

        Map<String, AiProviderConfigEntity> trialProviders = new LinkedHashMap<>(dbProviders);
        if (command.providers() != null) {
            for (SaveModelConfigCommand.ProviderItem item : command.providers()) {
                if (item.name() == null) continue;
                AiProviderConfigEntity e =
                        trialProviders.computeIfAbsent(item.name(), k -> newEntityProvider(k));
                if (item.baseUrl() != null && !item.baseUrl().isBlank())
                    e.setBaseUrl(item.baseUrl());
                if (item.apiKey() != null) {
                    if (item.apiKey().isBlank()) {
                        trialProviders.remove(item.name()); // 空串：清除覆盖回退 yml
                    } else {
                        e.setApiKeyEnc(apiKeyCrypto.encrypt(item.apiKey()));
                    }
                }
            }
        }
        Map<String, AiTierConfigEntity> trialTiers = new LinkedHashMap<>(dbTiers);
        if (command.tiers() != null) {
            for (SaveModelConfigCommand.TierItem item : command.tiers()) {
                if (item.tier() == null) continue;
                AiTierConfigEntity e =
                        trialTiers.computeIfAbsent(item.tier(), k -> newEntityTier(k));
                if (item.provider() != null) e.setProvider(item.provider());
                if (item.model() != null) e.setModel(item.model());
                if (item.temperature() != null)
                    e.setTemperature(java.math.BigDecimal.valueOf(item.temperature()));
                if (item.maxTokens() != null) e.setMaxTokens(item.maxTokens());
                if (item.dimensions() != null) e.setDimensions(item.dimensions());
                if (item.fallback() != null) e.setFallback(item.fallback());
                if (item.thinking() != null) e.setThinking(item.thinking());
                if (item.reasoningEffort() != null) e.setReasoningEffort(item.reasoningEffort());
                if (item.overrideBaseUrl() != null) {
                    if (item.overrideBaseUrl().isBlank()) e.setOverrideBaseUrl(null);
                    else e.setOverrideBaseUrl(item.overrideBaseUrl());
                }
                if (item.overrideApiKey() != null) {
                    if (item.overrideApiKey().isBlank()) e.setOverrideApiKeyEnc(null);
                    else e.setOverrideApiKeyEnc(apiKeyCrypto.encrypt(item.overrideApiKey()));
                }
            }
        }
        // 基准上应用 trial 覆盖
        return merge(trialProviders, trialTiers);
    }

    /** yml 为基准 + DB 覆盖层 → 生效 AiModelProperties。 */
    private AiModelProperties merge(
            Map<String, AiProviderConfigEntity> dbProviders,
            Map<String, AiTierConfigEntity> dbTiers) {
        Map<String, AiModelProperties.ProviderConfig> providers = mergeProviders(dbProviders);

        Map<ModelTier, AiModelProperties.TierConfig> tiers = new EnumMap<>(ModelTier.class);
        ymlBase.tiers()
                .forEach(
                        (tier, cfg) ->
                                tiers.put(
                                        tier,
                                        mergeTier(tier, cfg, dbTiers.get(tier.name()), providers)));

        return new AiModelProperties(
                ymlBase.defaultTier(), providers, tiers, ymlBase.pricing(), ymlBase.retry());
    }

    /** 合并 provider 集合：yml 全量（含 DB 覆盖）+ DB 独有的自定义 provider。 */
    private Map<String, AiModelProperties.ProviderConfig> mergeProviders(
            Map<String, AiProviderConfigEntity> dbProviders) {
        Map<String, AiModelProperties.ProviderConfig> providers = new LinkedHashMap<>();
        ymlBase.providers()
                .forEach(
                        (name, cfg) ->
                                providers.put(name, mergeProvider(cfg, dbProviders.get(name))));
        // 追加 DB 独有的自定义 provider（yml 中不存在，完全由 DB 提供 url/key）
        dbProviders.forEach(
                (name, db) -> {
                    if (!providers.containsKey(name)) {
                        providers.put(name, customProviderFromDb(db));
                    }
                });
        return providers;
    }

    /** DB 独有的自定义 provider：无 yml 兜底，baseUrl 与 apiKey 必须来自 DB。 */
    private AiModelProperties.ProviderConfig customProviderFromDb(AiProviderConfigEntity db) {
        if (db.getBaseUrl() == null || db.getBaseUrl().isBlank()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "自定义 provider " + db.getName() + " 缺少 baseUrl");
        }
        String apiKey = db.getApiKeyEnc() != null ? apiKeyCrypto.decrypt(db.getApiKeyEnc()) : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID, "自定义 provider " + db.getName() + " 未配置 API Key");
        }
        return new AiModelProperties.ProviderConfig(
                db.getBaseUrl(), apiKey, null, db.getMaxConcurrency());
    }

    private AiModelProperties.ProviderConfig mergeProvider(
            AiModelProperties.ProviderConfig cfg, AiProviderConfigEntity db) {
        if (db == null) {
            return cfg;
        }
        String baseUrl = db.getBaseUrl() != null ? db.getBaseUrl() : cfg.baseUrl();
        String apiKey;
        List<String> apiKeys;
        if (db.getApiKeyEnc() != null) {
            apiKey = apiKeyCrypto.decrypt(db.getApiKeyEnc());
            apiKeys = null; // DB 覆盖时忽略 yml 的 apiKeys 轮询列表，保证 effectiveApiKey 走新 key
        } else {
            apiKey = cfg.apiKey();
            apiKeys = cfg.apiKeys();
        }
        Integer maxConcurrency =
                db.getMaxConcurrency() != null ? db.getMaxConcurrency() : cfg.maxConcurrency();
        return new AiModelProperties.ProviderConfig(baseUrl, apiKey, apiKeys, maxConcurrency);
    }

    private AiModelProperties.TierConfig mergeTier(
            ModelTier tier,
            AiModelProperties.TierConfig cfg,
            AiTierConfigEntity db,
            Map<String, AiModelProperties.ProviderConfig> providers) {
        if (db == null) {
            return cfg;
        }
        String provider = firstNonNull(db.getProvider(), cfg.provider());
        String model = firstNonNull(db.getModel(), cfg.model());
        // 避免三元表达式原始类型/包装类型混用导致的自动拆箱 NPE（cfg.temperature() 可为 null，如 EMBEDDING 档位）
        Double temperature = cfg.temperature();
        if (db.getTemperature() != null) {
            temperature = db.getTemperature().doubleValue();
        }
        Integer maxTokens = firstNonNull(db.getMaxTokens(), cfg.maxTokens());
        Integer dimensions = firstNonNull(db.getDimensions(), cfg.dimensions());
        String fallback = firstNonNull(db.getFallback(), cfg.fallback());
        String thinking =
                db.getThinking() != null
                        ? (db.getThinking() ? "enabled" : "disabled")
                        : cfg.thinking();
        String reasoningEffort = firstNonNull(db.getReasoningEffort(), cfg.reasoningEffort());

        // 档位级 override url/key：创建专属 provider，并让档位指向它
        if (db.getOverrideBaseUrl() != null || db.getOverrideApiKeyEnc() != null) {
            AiModelProperties.ProviderConfig baseProvider = providers.get(provider);
            if (baseProvider == null) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID, "tier " + tier + " 的 provider 未配置: " + provider);
            }
            String overrideProviderName = tier.name() + "@override";
            String overrideBaseUrl =
                    db.getOverrideBaseUrl() != null
                            ? db.getOverrideBaseUrl()
                            : baseProvider.baseUrl();
            String overrideApiKey;
            List<String> overrideApiKeys;
            if (db.getOverrideApiKeyEnc() != null) {
                overrideApiKey = apiKeyCrypto.decrypt(db.getOverrideApiKeyEnc());
                overrideApiKeys = null;
            } else {
                overrideApiKey = baseProvider.apiKey();
                overrideApiKeys = baseProvider.apiKeys();
            }
            providers.put(
                    overrideProviderName,
                    new AiModelProperties.ProviderConfig(
                            overrideBaseUrl,
                            overrideApiKey,
                            overrideApiKeys,
                            baseProvider.maxConcurrency()));
            provider = overrideProviderName;
        }

        return new AiModelProperties.TierConfig(
                provider,
                model,
                temperature,
                maxTokens,
                dimensions,
                fallback,
                thinking,
                reasoningEffort);
    }

    // ------------------------------------------------------------------
    // 响应视图
    // ------------------------------------------------------------------

    private ModelConfigView.ProviderView toProviderView(
            String name, AiModelProperties.ProviderConfig cfg, AiProviderConfigEntity db) {
        return new ModelConfigView.ProviderView(
                name,
                cfg.baseUrl(),
                apiKeyCrypto.mask(cfg.effectiveApiKey()),
                db != null ? "db" : "yml",
                cfg.maxConcurrency(),
                ymlBase.providers().containsKey(name));
    }

    private ModelConfigView.TierView toTierView(
            ModelTier tier, AiModelProperties.TierConfig cfg, AiTierConfigEntity db) {
        String overrideApiKeyMasked = null;
        if (db != null && db.getOverrideApiKeyEnc() != null) {
            String plain = apiKeyCrypto.decrypt(db.getOverrideApiKeyEnc());
            overrideApiKeyMasked = apiKeyCrypto.mask(plain);
        }
        return new ModelConfigView.TierView(
                tier.name(),
                cfg.provider(),
                cfg.model(),
                cfg.temperature(),
                cfg.maxTokens(),
                cfg.dimensions(),
                cfg.fallback(),
                cfg.thinking() == null ? null : "enabled".equals(cfg.thinking()),
                cfg.reasoningEffort(),
                db != null ? db.getOverrideBaseUrl() : null,
                overrideApiKeyMasked,
                db != null ? "db" : "yml");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private void refreshRouter() {
        modelRouter.refresh(mergeEffective());
        log.info("AI 模型配置已保存并热刷新生效");
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    private static <T> Integer firstNonNull(Integer a, Integer b) {
        return a != null ? a : b;
    }

    private static String safeMessage(Exception e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    private static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyFn) {
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static AiProviderConfigEntity newEntityProvider(String name) {
        AiProviderConfigEntity e = new AiProviderConfigEntity();
        e.setName(name);
        return e;
    }

    private static AiTierConfigEntity newEntityTier(String tier) {
        AiTierConfigEntity e = new AiTierConfigEntity();
        e.setTier(tier);
        return e;
    }
}
