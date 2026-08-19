-- AI 模型配置中心化（v1.2 D 方案）：
-- DB 作为 application.yml(aims.ai) 的增量覆盖层，NULL/缺行表示沿用 yml 默认值。

-- provider 级配置（每 provider 一行）
CREATE TABLE ai_provider_config (
    name            VARCHAR(64)  PRIMARY KEY,
    base_url        VARCHAR(512) NOT NULL,
    -- AES-GCM 加密后的 API Key；NULL 表示沿用 yml（环境变量注入的 key）
    api_key_enc     VARCHAR(512),
    -- 并发上限；NULL 沿用 yml
    max_concurrency INT,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_provider_config IS 'AI provider 配置（DB 覆盖 yml aims.ai.providers）';

-- tier 级配置（每档位一行）
CREATE TABLE ai_tier_config (
    tier                 VARCHAR(32)   PRIMARY KEY,
    provider             VARCHAR(64),
    model                VARCHAR(128),
    temperature          NUMERIC(3,2),
    max_tokens           INT,
    dimensions           INT,
    fallback             VARCHAR(64),
    thinking             BOOLEAN,
    reasoning_effort     VARCHAR(16),
    -- 本档位独立 baseUrl（优先于 provider 的 baseUrl）
    override_base_url    VARCHAR(512),
    -- 本档位独立 apiKey（AES-GCM 加密，优先于 provider 的 apiKey）
    override_api_key_enc VARCHAR(512),
    updated_by           VARCHAR(64),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE ai_tier_config IS 'AI 档位独立配置（DB 覆盖 yml aims.ai.tiers，override_* 为档位级 url/apiKey）';
