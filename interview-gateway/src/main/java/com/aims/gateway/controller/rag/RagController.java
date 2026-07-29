package com.aims.gateway.controller.rag;

import com.aims.core.common.Result;
import com.aims.infra.persistence.dto.QuestionFilter;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.entity.ResumeSearchResult;
import com.aims.infra.persistence.service.QuestionRagService;
import com.aims.infra.persistence.service.ResumeRagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RAG 检索验证接口（仅 local/dev 环境加载）。 */
@Profile({"local", "dev"})
@Validated
@RestController
@RequestMapping("/api/v1/rag")
@Tag(name = "RAG 检索")
public class RagController {

    private final QuestionRagService questionRagService;
    private final ResumeRagService resumeRagService;

    public RagController(QuestionRagService questionRagService, ResumeRagService resumeRagService) {
        this.questionRagService = questionRagService;
        this.resumeRagService = resumeRagService;
    }

    @Operation(summary = "题库检索", description = "按查询文本语义检索 Top-K 相关题目，支持按分类/难度过滤")
    @GetMapping("/questions")
    public Result<List<QuestionSearchResult>> questions(
            @RequestParam @NotBlank(message = "query 不能为空") String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int topK,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty) {
        return Result.ok(
                questionRagService.search(query, new QuestionFilter(category, difficulty), topK));
    }

    @Operation(
            summary = "简历检索",
            description = "按查询文本语义检索 Top-K 相关简历，支持 minScore 过滤和 resumeId 指定候选人")
    @GetMapping("/resumes")
    public Result<List<ResumeSearchResult>> resumes(
            @RequestParam @NotBlank(message = "query 不能为空") String query,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) int topK,
            @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double minScore,
            @RequestParam(required = false) Long resumeId) {
        return Result.ok(resumeRagService.search(query, resumeId, topK, minScore));
    }
}
