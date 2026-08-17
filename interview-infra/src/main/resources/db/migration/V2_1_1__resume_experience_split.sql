-- v1.1-C 简历经历结构化拆表
-- candidate 候选人主表 + 经历落表 + resume.candidate_id 关联（TD2 建列，语义归位由代码层推进）
-- 存量数据回填：resume.parsed_json 中的 workExperiences / projectExperiences 拆行入库（保留 parsed_json 兜底展示）

-- 1) candidate 候选人主表（简历与候选人解耦，支持多人多简历）
CREATE TABLE IF NOT EXISTS candidate (
    id             BIGSERIAL PRIMARY KEY,
    candidate_name VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    email          VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2) resume 增加 candidate_id（TD2：指向 candidate 表；存量按 candidate_name 唯一性回填）
ALTER TABLE resume ADD COLUMN IF NOT EXISTS candidate_id BIGINT REFERENCES candidate (id);
CREATE INDEX IF NOT EXISTS idx_resume_candidate_id ON resume (candidate_id);

-- 3) 工作经历表（对齐 WorkExperience record：type/company/title/period/description）
CREATE TABLE IF NOT EXISTS resume_work_experience (
    id          BIGSERIAL PRIMARY KEY,
    resume_id   BIGINT      NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    exp_type    VARCHAR(20),          -- 工作/实习（WorkExperience.type）
    company     VARCHAR(200),
    position    VARCHAR(200),         -- WorkExperience.title
    start_date  VARCHAR(20),
    end_date    VARCHAR(20),
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_work_resume ON resume_work_experience (resume_id);
CREATE INDEX IF NOT EXISTS idx_work_company ON resume_work_experience (company);

-- 4) 项目经历表（对齐 ProjectExperience record：name/role/period/description/highlights）
CREATE TABLE IF NOT EXISTS resume_project_experience (
    id          BIGSERIAL PRIMARY KEY,
    resume_id   BIGINT      NOT NULL REFERENCES resume (id) ON DELETE CASCADE,
    name        VARCHAR(200),
    role        VARCHAR(100),
    start_date  VARCHAR(20),
    end_date    VARCHAR(20),
    description TEXT,
    tech_stack  JSONB,                -- 技术栈（预留，存量回填暂置空）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_project_resume ON resume_project_experience (resume_id);
CREATE INDEX IF NOT EXISTS idx_project_name ON resume_project_experience (name);

-- 5) 项目亮点表（ProjectExperience.highlights 展开；可选，父表关联）
CREATE TABLE IF NOT EXISTS resume_project_highlight (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT      NOT NULL REFERENCES resume_project_experience (id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_highlight_project ON resume_project_highlight (project_id);

-- ─── 存量数据回填 ───

-- 5.1) candidate：按 candidate_name 去重回填（首次出现建候选人，后续简历挂同一候选人）
INSERT INTO candidate (candidate_name, phone, email)
SELECT r.candidate_name, r.phone, r.email
FROM resume r
WHERE r.candidate_name IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM candidate c WHERE c.candidate_name = r.candidate_name
  );

-- 5.2) resume.candidate_id 回填：按 candidate_name 关联
UPDATE resume r
SET candidate_id = c.id,
    updated_at   = now()
FROM candidate c
WHERE c.candidate_name = r.candidate_name;

-- 5.3) 工作经历拆行：parsed_json->'workExperiences' 数组 → 每行一条
--      period 形如 "2020.06 - 2022.05"，按 '-' 拆 start/end（首段/末段去空格）
INSERT INTO resume_work_experience (resume_id, exp_type, company, position, start_date, end_date, description)
SELECT r.id,
       we ->> 'type',
       we ->> 'company',
       we ->> 'title',
       trim(split_part(we ->> 'period', '-', 1)),
       CASE WHEN we ->> 'period' LIKE '%-%' THEN trim(split_part(we ->> 'period', '-', 2)) END,
       we ->> 'description'
FROM resume r
         CROSS JOIN LATERAL jsonb_array_elements(
                 CASE WHEN jsonb_typeof(r.parsed_json -> 'workExperiences') = 'array'
                      THEN r.parsed_json -> 'workExperiences' ELSE '[]'::jsonb END
             ) we
WHERE r.parsed_json IS NOT NULL
  AND jsonb_typeof(r.parsed_json -> 'workExperiences') = 'array';

-- 5.4) 项目经历拆行
INSERT INTO resume_project_experience (resume_id, name, role, start_date, end_date, description, tech_stack)
SELECT r.id,
       pe ->> 'name',
       pe ->> 'role',
       trim(split_part(pe ->> 'period', '-', 1)),
       CASE WHEN pe ->> 'period' LIKE '%-%' THEN trim(split_part(pe ->> 'period', '-', 2)) END,
       pe ->> 'description',
       NULL
FROM resume r
         CROSS JOIN LATERAL jsonb_array_elements(
                 CASE WHEN jsonb_typeof(r.parsed_json -> 'projectExperiences') = 'array'
                      THEN r.parsed_json -> 'projectExperiences' ELSE '[]'::jsonb END
             ) pe
WHERE r.parsed_json IS NOT NULL
  AND jsonb_typeof(r.parsed_json -> 'projectExperiences') = 'array';

-- 注：项目亮点（highlights）存量不回填——由解析/人工修改的代码层拆行写入（可选增强，父表关联）
