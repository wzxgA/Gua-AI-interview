-- TTS 连接配置中心化（v1.4-F）：
-- DB 作为 application.yml(aims.tts) 的增量覆盖层，NULL/缺行表示沿用 yml 默认值（含环境变量）。
-- 单行全局配置（id 恒为 1，可经设置页配置 url/apiKey/音色等，保存后立即生效）。

CREATE TABLE tts_config (
    id                 SMALLINT  PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled            BOOLEAN,             -- NULL=沿用 yml aims.tts.enabled
    provider           VARCHAR(32),         -- 引擎标识，默认 volc
    -- 接口地址；NULL=沿用 yml VOLC_TTS_BASE_URL
    base_url           VARCHAR(512),
    -- AES-GCM 加密后的 API Key；NULL 表示沿用 yml（环境变量注入的 key）
    api_key_enc        VARCHAR(512),
    -- 火山资源 ID；NULL=沿用 yml
    resource_id        VARCHAR(128),
    -- 默认音色；NULL=沿用 yml
    default_speaker    VARCHAR(64),
    format             VARCHAR(16),
    sample_rate        INT,
    speech_rate        INT,
    persona_voice_link BOOLEAN,             -- 人设联动音色
    updated_by         VARCHAR(64),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE tts_config IS 'TTS 连接配置（DB 覆盖 yml aims.tts，单行全局）';
COMMENT ON COLUMN tts_config.base_url IS 'TTS 接口地址；NULL 沿用 yml';
COMMENT ON COLUMN tts_config.api_key_enc IS 'AES-GCM 加密后的 API Key；NULL 沿用 yml';
COMMENT ON COLUMN tts_config.resource_id IS '火山资源 ID；NULL 沿用 yml';
COMMENT ON COLUMN tts_config.default_speaker IS '默认音色；NULL 沿用 yml';