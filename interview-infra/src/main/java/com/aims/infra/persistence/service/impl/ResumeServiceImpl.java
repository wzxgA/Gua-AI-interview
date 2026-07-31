package com.aims.infra.persistence.service.impl;

import com.aims.agent.ResumePromptBuilder;
import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.PageQuery;
import com.aims.core.common.exception.BizException;
import com.aims.core.resume.EmbeddingStatus;
import com.aims.core.resume.ParsedResume;
import com.aims.core.resume.ResumeStatus;
import com.aims.core.resume.WorkExperience;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.BatchReembedTask;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务实现。
 *
 * <p>upload 流程：先入库拿 ID -> MinIO 上传 -> 文本抽取 -> 更新原文 -> 虚拟线程异步解析。 parse 流程：条件抢占 -> 调
 * AiChatFacade.callForEntity(ECONOMY) -> 序列化为 JSON 存入 parsed_json -> PARSED / FAILED，成功后自动清空旧向量。
 * embed 流程：条件抢占 -> 拼接结构化文本 -> modelRouter.embed -> mapper.markEmbedded（pgvector 字符串）。
 */
@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);

    /** MinIO 简历 bucket（docker-compose minio-init 已预建）。 */
    private static final String BUCKET = "aims-resume";

    /** 期望的 Embedding 向量维度（text-embedding-v4, 2048 维）。 */
    private static final int EXPECTED_EMBEDDING_DIMENSION = 2048;

    /** 最大上传文件大小：10 MB。 */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 批量重新向量化任务状态（内存级，应用重启后丢失）。 */
    private final ConcurrentHashMap<String, BatchReembedTask> batchTasks =
            new ConcurrentHashMap<>();

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
        validateUploadFile(file);

        // 1. 先入库拿 ID
        ResumeEntity entity = new ResumeEntity();
        entity.setCandidateName(candidateName);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setParseStatus(ResumeStatus.PENDING.name());
        entity.setEmbeddingStatus(EmbeddingStatus.PENDING.name());
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
        // 3. 抢占解析任务，避免自动触发和手动触发重复调用 AI
        if (resumeMapper.claimParse(id) == 0) {
            ResumeEntity existing = resumeMapper.selectById(id);
            if (existing == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
            }
            return existing;
        }

        ResumeEntity entity = resumeMapper.selectById(id);
        String rawText = entity.getRawText();
        if (rawText == null || rawText.isBlank()) {
            resumeMapper.markParseFailed(id, "简历原文为空，无法解析");
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "简历原文为空，无法解析: " + id);
        }

        try {
            // 调 ECONOMY 档位进行结构化解析
            ParsedResume parsed =
                    aiChatFacade.callForEntity(
                            ModelTier.ECONOMY,
                            ResumePromptBuilder.parseSystem(),
                            rawText,
                            ParsedResume.class);
            validateParsedResume(parsed);
            String parsedJson = objectMapper.writeValueAsString(parsed);
            resumeMapper.markParsed(id, parsedJson);
            resumeMapper.invalidateEmbedding(id);
            // 解析成功后自动异步触发向量化
            Thread.startVirtualThread(
                    () -> {
                        try {
                            embed(id);
                        } catch (Exception e) {
                            log.error("解析后自动向量化失败 id={}", id, e);
                        }
                    });
            return resumeMapper.selectById(id);
        } catch (Exception e) {
            log.error("简历解析失败 id={}", id, e);
            resumeMapper.markParseFailed(id, errorSummary(e));
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "简历解析失败: " + id, e);
        }
    }

    @Override
    public ResumeEntity updateParsedResume(Long id, ParsedResume parsed) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }
        if (!ResumeStatus.PARSED.name().equals(entity.getParseStatus())) {
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "仅解析成功的简历可编辑: " + id);
        }
        validateParsedResume(parsed);
        try {
            String parsedJson = objectMapper.writeValueAsString(parsed);
            resumeMapper.updateParsedJson(id, parsedJson);
            resumeMapper.invalidateEmbedding(id);
            Thread.startVirtualThread(
                    () -> {
                        try {
                            embed(id);
                        } catch (Exception e) {
                            log.error("人工修改后自动向量化失败 id={}", id, e);
                        }
                    });
            return resumeMapper.selectById(id);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("人工修改简历失败 id={}", id, e);
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "人工修改简历失败: " + id, e);
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
        if (resumeMapper.claimEmbedding(id) == 0) {
            return;
        }

        try {
            String text = buildEmbeddingText(entity);
            float[] vector = modelRouter.embed(text);
            if (vector.length != EXPECTED_EMBEDDING_DIMENSION) {
                throw new BizException(
                        ErrorCode.EMBEDDING_FAILED,
                        "向量维度不匹配: expected="
                                + EXPECTED_EMBEDDING_DIMENSION
                                + " actual="
                                + vector.length);
            }
            String vectorString = PgVectorSupport.toVectorString(vector);
            String modelName = modelRouter.resolve(ModelTier.EMBEDDING).config().model();
            resumeMapper.markEmbedded(id, vectorString, modelName, vector.length);
        } catch (Exception e) {
            log.error("简历向量化失败 id={}", id, e);
            resumeMapper.markEmbeddingFailed(id, errorSummary(e));
            throw new BizException(ErrorCode.EMBEDDING_FAILED, "简历向量化失败: " + id, e);
        }
    }

    @Override
    public void reembed(Long id) {
        ResumeEntity entity = resumeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "简历不存在: " + id);
        }
        if (!ResumeStatus.PARSED.name().equals(entity.getParseStatus())) {
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "简历尚未解析成功，无法向量化: " + id);
        }
        resumeMapper.invalidateEmbedding(id);
        embed(id);
    }

    @Override
    public String reembedBatch(int batchSize) {
        int total = resumeMapper.countNeedingReembed();
        String taskId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();

        BatchReembedTask initial =
                new BatchReembedTask(taskId, "RUNNING", total, 0, 0, startedAt, null, null);
        batchTasks.put(taskId, initial);

        Thread.startVirtualThread(
                () -> {
                    int success = 0;
                    int failed = 0;
                    int offset = 0;
                    try {
                        while (true) {
                            List<Long> ids =
                                    resumeMapper.selectIdsNeedingReembed(batchSize, offset);
                            if (ids.isEmpty()) {
                                break;
                            }
                            for (Long id : ids) {
                                try {
                                    embed(id);
                                    success++;
                                } catch (Exception e) {
                                    log.warn("批量重新向量化失败 id={}", id, e);
                                    failed++;
                                }
                            }
                            offset += batchSize;
                        }
                        BatchReembedTask completed =
                                new BatchReembedTask(
                                        taskId,
                                        "COMPLETED",
                                        total,
                                        success,
                                        failed,
                                        startedAt,
                                        Instant.now(),
                                        null);
                        batchTasks.put(taskId, completed);
                    } catch (Exception e) {
                        log.error("批量重新向量化任务异常 taskId={}", taskId, e);
                        BatchReembedTask errorTask =
                                new BatchReembedTask(
                                        taskId,
                                        "FAILED",
                                        total,
                                        success,
                                        failed,
                                        startedAt,
                                        Instant.now(),
                                        e.getMessage());
                        batchTasks.put(taskId, errorTask);
                    }
                });

        log.info("批量重新向量化任务已启动 taskId={} total={}", taskId, total);
        return taskId;
    }

    @Override
    public BatchReembedTask getBatchReembedStatus(String taskId) {
        return batchTasks.get(taskId);
    }

    private String errorSummary(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /** 校验上传文件：非空、大小、文件名、扩展名。 */
    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(
                    ErrorCode.FILE_UPLOAD_FAILED,
                    "文件过大: " + file.getSize() + " bytes，最大 " + MAX_FILE_SIZE + " bytes");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "文件名不能为空");
        }
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".txt")) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "仅支持 PDF 和 TXT 文件: " + filename);
        }
    }

    /**
     * 校验解析结果，修正空列表并校验关键约束。
     *
     * <p>AI 返回的列表字段可能为 null，统一转为空列表；校验 workExperience.type 和 yearsOfExperience 的合法性。
     */
    private void validateParsedResume(ParsedResume parsed) {
        if (parsed == null) {
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "解析结果为空");
        }
        if (parsed.yearsOfExperience() != null && parsed.yearsOfExperience() < 0) {
            throw new BizException(
                    ErrorCode.RESUME_PARSE_FAILED, "工作年限不能为负数: " + parsed.yearsOfExperience());
        }
        if (parsed.workExperiences() != null) {
            for (WorkExperience we : parsed.workExperiences()) {
                if (we.type() != null
                        && !"WORK".equals(we.type())
                        && !"INTERNSHIP".equals(we.type())) {
                    throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "工作经历类型非法: " + we.type());
                }
            }
        }
    }

    @Override
    public boolean hasEmbedding(Long id) {
        Boolean has = resumeMapper.hasEmbedding(id);
        return Boolean.TRUE.equals(has);
    }

    /**
     * 构造向量化输入文本：结构化拼接候选人信息。
     *
     * <p>优先使用 parsedJson 中的结构化字段，格式化为可读的稳定文本； 若未解析则退化为 candidateName + rawText 摘要。
     */
    private String buildEmbeddingText(ResumeEntity entity) {
        ParsedResume parsed = null;
        if (entity.getParsedJson() != null) {
            try {
                parsed = objectMapper.readValue(entity.getParsedJson(), ParsedResume.class);
            } catch (Exception e) {
                log.warn("解析 parsedJson 失败 id={}", entity.getId());
            }
        }

        if (parsed == null) {
            // 未解析时退化为 candidateName + rawText 摘要
            StringBuilder fallback = new StringBuilder();
            if (entity.getCandidateName() != null) {
                fallback.append("候选人：").append(entity.getCandidateName());
            }
            String rawText = entity.getRawText();
            if (rawText != null && !rawText.isBlank()) {
                fallback.append("\n")
                        .append(rawText.length() > 500 ? rawText.substring(0, 500) : rawText);
            }
            return fallback.toString();
        }

        StringBuilder sb = new StringBuilder();
        appendField(sb, "候选人", parsed.candidateName());
        appendField(sb, "当前职位", parsed.currentTitle());
        if (parsed.yearsOfExperience() != null) {
            appendField(sb, "工作年限", parsed.yearsOfExperience() + "年");
        }
        appendField(sb, "学历", parsed.education());

        if (parsed.skills() != null && !parsed.skills().isEmpty()) {
            sb.append("技能：").append(String.join("、", parsed.skills())).append("\n");
        }

        if (parsed.workExperiences() != null && !parsed.workExperiences().isEmpty()) {
            sb.append("工作经历：\n");
            for (WorkExperience we : parsed.workExperiences()) {
                sb.append("- ");
                if (we.company() != null) {
                    sb.append(we.company());
                }
                if (we.title() != null) {
                    sb.append(" / ").append(we.title());
                }
                if (we.period() != null) {
                    sb.append("（").append(we.period()).append("）");
                }
                if (we.description() != null) {
                    sb.append("：").append(we.description());
                }
                sb.append("\n");
            }
        }

        if (parsed.projectExperiences() != null && !parsed.projectExperiences().isEmpty()) {
            sb.append("项目经历：\n");
            for (var project : parsed.projectExperiences()) {
                sb.append("- ");
                if (project.name() != null) {
                    sb.append(project.name());
                }
                if (project.role() != null) {
                    sb.append(" / ").append(project.role());
                }
                if (project.period() != null) {
                    sb.append("（").append(project.period()).append("）");
                }
                if (project.description() != null) {
                    sb.append("：").append(project.description());
                }
                if (project.highlights() != null && !project.highlights().isEmpty()) {
                    sb.append(" 亮点：").append(String.join("；", project.highlights()));
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value).append("\n");
        }
    }
}
