-- P2 核心表结构：7 张业务表 + 索引
-- 对齐技术方案第七章数据模型设计

-- 岗位
CREATE TABLE position (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    department        VARCHAR(100),
    jd_text           TEXT         NOT NULL,
    requirements_json JSONB,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    embedding         vector(1024),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_position_status ON position (status);

-- 题库
CREATE TABLE question_bank (
    id              BIGSERIAL PRIMARY KEY,
    category        VARCHAR(50)  NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    difficulty      VARCHAR(10)  NOT NULL,
    content         TEXT         NOT NULL,
    standard_answer TEXT,
    tags            TEXT[],
    embedding       vector(1024),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_category   ON question_bank (category);
CREATE INDEX idx_question_difficulty ON question_bank (difficulty);

-- 简历
CREATE TABLE resume (
    id             BIGSERIAL PRIMARY KEY,
    candidate_name VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(100),
    raw_text       TEXT,
    parsed_json    JSONB,
    file_url       VARCHAR(500),
    parse_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    embedding      vector(1024),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_candidate ON resume (candidate_name);
CREATE INDEX idx_resume_status    ON resume (parse_status);

-- 面试会话（P3 业务，P2 仅建表）
CREATE TABLE interview_session (
    id           BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT,
    position_id  BIGINT REFERENCES position (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    plan_json    JSONB,
    started_at   TIMESTAMPTZ,
    ended_at     TIMESTAMPTZ,
    total_score  NUMERIC(5, 2),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_status ON interview_session (status);

-- 面试轮次（P3 业务，P2 仅建表）
CREATE TABLE interview_round (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES interview_session (id),
    seq            INT    NOT NULL,
    question       TEXT   NOT NULL,
    answer         TEXT,
    follow_up_type VARCHAR(20),
    audio_url      VARCHAR(500),
    duration_ms    INT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_round_session ON interview_round (session_id);

-- 评估结果（P4 业务，P2 仅建表）
CREATE TABLE interview_evaluation (
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT NOT NULL REFERENCES interview_session (id),
    round_id       BIGINT REFERENCES interview_round (id),
    dimension      VARCHAR(30) NOT NULL,
    score          INT     NOT NULL,
    comment        TEXT,
    evidence_quote TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_eval_session ON interview_evaluation (session_id);

-- 面试报告（P4 业务，P2 仅建表）
CREATE TABLE interview_report (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL REFERENCES interview_session (id),
    summary         TEXT,
    dimensions_json JSONB,
    recommendation  VARCHAR(20),
    report_pdf_url  VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_session ON interview_report (session_id);
