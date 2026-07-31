package com.aims.gateway.controller.resume;

import com.aims.core.common.PageQuery;
import com.aims.core.common.Result;
import com.aims.core.resume.ParsedResume;
import com.aims.infra.persistence.dto.BatchReembedTask;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.aims.infra.persistence.service.ResumeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 简历管理 REST API。 */
@RestController
@RequestMapping("/api/v1/resumes")
@Tag(name = "简历管理")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    /** rawText 摘要最大长度。 */
    private static final int RAW_TEXT_SUMMARY_MAX = 200;

    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    public ResumeController(ResumeService resumeService, ObjectMapper objectMapper) {
        this.resumeService = resumeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传简历", description = "上传简历文件（PDF/TXT），自动抽取文本并异步触发结构化解析")
    public Result<ResumeResponse> upload(
            @RequestParam MultipartFile file,
            @RequestParam String candidateName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email) {
        ResumeEntity entity = resumeService.upload(file, candidateName, phone, email);
        return Result.ok(toResponse(entity));
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "触发解析", description = "手动触发简历结构化解析（AI 抽取为结构化 JSON）")
    public Result<ResumeResponse> parse(@PathVariable Long id) {
        ResumeEntity entity = resumeService.parse(id);
        return Result.ok(toResponse(entity));
    }

    @PatchMapping("/{id}/parsed-resume")
    @Operation(summary = "人工修改解析结果", description = "修改解析后的结构化简历，自动失效旧向量并异步重新向量化")
    public Result<ResumeResponse> updateParsed(
            @PathVariable Long id, @RequestBody ParsedResume parsed) {
        ResumeEntity entity = resumeService.updateParsedResume(id, parsed);
        return Result.ok(toResponse(entity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询简历", description = "查询单条简历详情")
    public Result<ResumeResponse> get(@PathVariable Long id) {
        ResumeEntity entity = resumeService.getById(id);
        return Result.ok(toResponse(entity));
    }

    @GetMapping
    @Operation(summary = "分页列表", description = "按候选人姓名模糊查询简历分页列表")
    public Result<IPage<ResumeResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String candidateName) {
        PageQuery pageQuery = PageQuery.of(page, size);
        IPage<ResumeEntity> entityPage = resumeService.page(pageQuery, candidateName);
        IPage<ResumeResponse> responsePage = entityPage.convert(this::toResponse);
        return Result.ok(responsePage);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除简历", description = "同步删除 MinIO 对象与数据库记录")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/embed")
    @Operation(summary = "触发向量化", description = "将简历结构化内容向量化并写入 embedding 列")
    public Result<Void> embed(@PathVariable Long id) {
        resumeService.embed(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/reembed")
    @Operation(summary = "重新向量化", description = "使旧向量失效并重新生成")
    public Result<Void> reembed(@PathVariable Long id) {
        resumeService.reembed(id);
        return Result.ok(null);
    }

    @PostMapping("/reembed-batch")
    @Operation(summary = "批量重新向量化", description = "异步处理所有已解析但无向量的简历，返回任务 ID")
    public Result<BatchReembedTask> reembedBatch(@RequestParam(defaultValue = "20") int batchSize) {
        String taskId = resumeService.reembedBatch(batchSize);
        BatchReembedTask task = new BatchReembedTask(taskId, "RUNNING", 0, 0, 0, null, null, null);
        return Result.ok(task);
    }

    @GetMapping("/reembed-batch/{taskId}")
    @Operation(summary = "查询批量向量化任务状态", description = "轮询异步批量重新向量化任务的进度")
    public Result<BatchReembedTask> getBatchReembedStatus(@PathVariable String taskId) {
        BatchReembedTask task = resumeService.getBatchReembedStatus(taskId);
        if (task == null) {
            return Result.ok(new BatchReembedTask(taskId, "NOT_FOUND", 0, 0, 0, null, null, null));
        }
        return Result.ok(task);
    }

    /**
     * ResumeEntity -> ResumeResponse 转换。
     *
     * <p>解析 parsedJson 为 ParsedResume；rawText 截断为摘要；检查 hasEmbedding。
     */
    private ResumeResponse toResponse(ResumeEntity entity) {
        ParsedResume parsedResume = null;
        if (entity.getParsedJson() != null) {
            try {
                parsedResume = objectMapper.readValue(entity.getParsedJson(), ParsedResume.class);
            } catch (Exception e) {
                log.warn("解析 parsedJson 失败 id={}", entity.getId(), e);
            }
        }

        String rawText = entity.getRawText();
        if (rawText != null && rawText.length() > RAW_TEXT_SUMMARY_MAX) {
            rawText = rawText.substring(0, RAW_TEXT_SUMMARY_MAX) + "...";
        }

        boolean hasEmbedding = resumeService.hasEmbedding(entity.getId());

        String embeddingStatus = entity.getEmbeddingStatus();
        if (embeddingStatus == null) {
            embeddingStatus = hasEmbedding ? "COMPLETED" : "PENDING";
        }

        return new ResumeResponse(
                entity.getId(),
                entity.getCandidateName(),
                entity.getPhone(),
                entity.getEmail(),
                rawText,
                entity.getParseStatus(),
                entity.getParseError(),
                entity.getParsedAt(),
                embeddingStatus,
                entity.getEmbeddingError(),
                entity.getEmbeddedAt(),
                entity.getEmbeddingModel(),
                entity.getEmbeddingDimension(),
                parsedResume,
                entity.getFileUrl(),
                hasEmbedding,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
