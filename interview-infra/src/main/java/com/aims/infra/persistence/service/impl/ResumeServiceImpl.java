package com.aims.infra.persistence.service.impl;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.PageQuery;
import com.aims.core.common.exception.BizException;
import com.aims.core.resume.ParsedResume;
import com.aims.core.resume.ResumeStatus;
import com.aims.core.resume.WorkExperience;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.mapper.ResumeMapper;
import com.aims.infra.persistence.service.ResumeService;
import com.aims.infra.storage.ResumeTextExtractor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务实现。
 *
 * <p>upload 流程：先入库拿 ID -> MinIO 上传 -> 文本抽取 -> 更新原文 -> 虚拟线程异步解析。 parse 流程：取 rawText -> 调
 * AiChatFacade.callForEntity(ECONOMY) -> 序列化为 JSON 存入 parsed_json -> PARSED / FAILED。 embed 流程：
 * 拼接结构化文本 -> modelRouter.embed -> mapper.updateEmbedding（pgvector 字符串）。
 */
@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);

    /** MinIO 简历 bucket（docker-compose minio-init 已预建）。 */
    private static final String BUCKET = "aims-resume";

    /** 简历解析系统提示词。 */
    private static final String PARSE_SYSTEM_PROMPT =
            "你是简历解析专家。请将输入的简历文本解析为结构化 JSON。字段说明：candidateName(姓名), phone(电话), email(邮箱),"
                + " yearsOfExperience(工作年限), education(学历), currentTitle(当前职位), skills(技能列表),"
                + " workExperiences(工作或实习经历列表，含 type/company/title/period/description，type 只能为 WORK"
                + " 或 INTERNSHIP), projectExperiences(项目经历列表，含"
                + " name/role/period/description/highlights，highlights"
                + " 为当前项目对应的项目亮点列表)。没有对应数据时返回空数组。只输出 JSON，不要额外说明。";

    private final ResumeMapper resumeMapper;
    private final MinioClient minioClient;
    private final AiChatFacade aiChatFacade;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public ResumeServiceImpl(
            ResumeMapper resumeMapper,
            MinioClient minioClient,
            AiChatFacade aiChatFacade,
            ModelRouter modelRouter,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.minioClient = minioClient;
        this.aiChatFacade = aiChatFacade;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeEntity upload(
            MultipartFile file, String candidateName, String phone, String email) {
        // 1. 先入库拿 ID
        ResumeEntity entity = new ResumeEntity();
        entity.setCandidateName(candidateName);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setParseStatus(ResumeStatus.PENDING.name());
        resumeMapper.insert(entity);
        Long id = entity.getId();

        String originalFilename =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume";
        String objectName = id + "/" + originalFilename;
        String fileUrl = BUCKET + "/" + objectName;

        // 2. 上传到 MinIO
        try (InputStream uploadStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(BUCKET).object(objectName).stream(
                                    uploadStream, file.getSize(), -1)
                            .contentType(
                                    file.getContentType() != null
                                            ? file.getContentType()
                                            : "application/octet-stream")
                            .build());
        } catch (Exception e) {
            throw new BizException(
                    ErrorCode.FILE_UPLOAD_FAILED, "MinIO 上传失败: " + originalFilename, e);
        }

        // 3. 抽取文本
        String rawText;
        try (InputStream extractStream = file.getInputStream()) {
            rawText = ResumeTextExtractor.extract(extractStream, originalFilename);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "文件读取失败: " + originalFilename, e);
        }

        // 4. 保存 rawText、fileUrl
        entity.setFileUrl(fileUrl);
        entity.setRawText(rawText);
        resumeMapper.updateById(entity);

        // 5. 异步触发解析（虚拟线程）
        Thread.startVirtualThread(
                () -> {
                    try {
                        parse(id);
                    } catch (Exception e) {
                        log.error("异步解析简历失败 id={}", id, e);
                    }
                });

        // 6. 返回 entity
        return entity;
    }

    @Override
    public ResumeEntity parse(Long id) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }

        String rawText = entity.getRawText();
        if (rawText == null || rawText.isBlank()) {
            entity.setParseStatus(ResumeStatus.FAILED.name());
            resumeMapper.updateById(entity);
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "简历原文为空，无法解析: " + id);
        }

        try {
            // 调 ECONOMY 档位进行结构化解析
            ParsedResume parsed =
                    aiChatFacade.callForEntity(
                            ModelTier.ECONOMY, PARSE_SYSTEM_PROMPT, rawText, ParsedResume.class);
            // 序列化为 JSON 存入 parsed_json
            String parsedJson = objectMapper.writeValueAsString(parsed);
            entity.setParsedJson(parsedJson);
            entity.setParseStatus(ResumeStatus.PARSED.name());
            resumeMapper.updateById(entity);
            return entity;
        } catch (Exception e) {
            log.error("简历解析失败 id={}", id, e);
            entity.setParseStatus(ResumeStatus.FAILED.name());
            try {
                resumeMapper.updateById(entity);
            } catch (Exception updateEx) {
                log.error("更新解析状态为 FAILED 失败 id={}", id, updateEx);
            }
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "简历解析失败: " + id, e);
        }
    }

    @Override
    public ResumeEntity getById(Long id) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }
        return entity;
    }

    @Override
    public IPage<ResumeEntity> page(PageQuery pageQuery, String candidateName) {
        Page<ResumeEntity> page = new Page<>(pageQuery.page(), pageQuery.size());
        LambdaQueryWrapper<ResumeEntity> wrapper = new LambdaQueryWrapper<>();
        if (candidateName != null && !candidateName.isBlank()) {
            wrapper.like(ResumeEntity::getCandidateName, candidateName);
        }
        wrapper.orderByDesc(ResumeEntity::getCreatedAt);
        return resumeMapper.selectPage(page, wrapper);
    }

    @Override
    public void delete(Long id) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }

        // 先删 MinIO 对象
        String fileUrl = entity.getFileUrl();
        if (fileUrl != null && fileUrl.startsWith(BUCKET + "/")) {
            String objectName = fileUrl.substring(BUCKET.length() + 1);
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(BUCKET).object(objectName).build());
            } catch (Exception e) {
                log.warn("MinIO 对象删除失败 fileUrl={} cause={}", fileUrl, e.toString());
            }
        }

        // 再删数据库记录
        resumeMapper.deleteById(id);
    }

    @Override
    public void embed(Long id) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }

        // 构造向量化输入文本：candidateName + skills + workExperiences + projectExperiences
        String text = buildEmbeddingText(entity);

        // 调 EMBEDDING 档位向量化
        float[] vector = modelRouter.embed(text);

        // 转为 pgvector 字符串写入 embedding 列
        String vectorString = PgVectorSupport.toVectorString(vector);
        resumeMapper.updateEmbedding(id, vectorString);
    }

    @Override
    public boolean hasEmbedding(Long id) {
        Boolean has = resumeMapper.hasEmbedding(id);
        return Boolean.TRUE.equals(has);
    }

    /**
     * 构造向量化输入文本：candidateName + skills + workExperiences + projectExperiences 拼接。
     *
     * <p>优先使用 parsedJson 中的结构化字段；若未解析则退化为 candidateName + rawText 摘要。
     */
    private String buildEmbeddingText(ResumeEntity entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getCandidateName() != null) {
            sb.append(entity.getCandidateName());
        }

        ParsedResume parsed = null;
        if (entity.getParsedJson() != null) {
            try {
                parsed = objectMapper.readValue(entity.getParsedJson(), ParsedResume.class);
            } catch (Exception e) {
                log.warn("解析 parsedJson 失败 id={}", entity.getId());
            }
        }

        if (parsed != null) {
            if (parsed.skills() != null) {
                sb.append(" ").append(String.join(" ", parsed.skills()));
            }
            if (parsed.workExperiences() != null) {
                for (WorkExperience we : parsed.workExperiences()) {
                    sb.append(" ").append(we.company()).append(" ").append(we.title());
                }
            }
            if (parsed.projectExperiences() != null) {
                for (var project : parsed.projectExperiences()) {
                    sb.append(" ").append(project.name());
                    if (project.role() != null) {
                        sb.append(" ").append(project.role());
                    }
                    if (project.description() != null) {
                        sb.append(" ").append(project.description());
                    }
                    if (project.highlights() != null) {
                        sb.append(" ").append(String.join(" ", project.highlights()));
                    }
                }
            }
        } else {
            // 未解析时退化为 rawText 摘要
            String rawText = entity.getRawText();
            if (rawText != null) {
                sb.append(" ").append(rawText.length() > 500 ? rawText.substring(0, 500) : rawText);
            }
        }

        return sb.toString();
    }
}
