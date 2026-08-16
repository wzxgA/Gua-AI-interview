package com.aims.gateway.controller.question;

import com.aims.core.common.Result;
import com.aims.core.question.ParsedQuestion;
import com.aims.core.question.Question;
import com.aims.infra.persistence.dto.InterviewNoteParseTask;
import com.aims.infra.persistence.dto.QuestionParseResult;
import com.aims.infra.persistence.service.QuestionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 题库管理 REST API。 */
@RestController
@RequestMapping("/api/v1/questions")
@Tag(name = "题库管理")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "创建题目", description = "保存题干后同步向量化，向量化失败不阻断创建")
    @PostMapping
    public Result<QuestionResponse> create(@Valid @RequestBody CreateQuestionRequest request) {
        Question question = toDomain(request);
        Question saved = questionService.create(question);
        return Result.ok(toResponse(saved));
    }

    @Operation(summary = "更新题目", description = "按非空字段更新，content 变化时自动重新向量化")
    @PutMapping("/{id}")
    public Result<QuestionResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdateQuestionRequest request) {
        Question question = toDomain(request);
        Question updated = questionService.update(id, question);
        return Result.ok(toResponse(updated));
    }

    @Operation(summary = "查询题目详情")
    @GetMapping("/{id}")
    public Result<QuestionResponse> getById(@PathVariable Long id) {
        Question question = questionService.getById(id);
        return Result.ok(toResponse(question));
    }

    @Operation(summary = "分页查询题目列表", description = "支持 category/difficulty/topic 过滤")
    @GetMapping
    public Result<IPage<QuestionResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String topic) {
        IPage<Question> result = questionService.page(page, size, category, difficulty, topic);
        IPage<QuestionResponse> responsePage = result.convert(this::toResponse);
        return Result.ok(responsePage);
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return Result.ok(null);
    }

    @Operation(summary = "批量导入题目", description = "逐条落库（部分失败不影响其余），成功后异步批量向量化")
    @PostMapping("/import")
    public Result<List<QuestionResponse>> importQuestions(
            @Valid @RequestBody QuestionImportRequest request) {
        List<Question> questions = request.questions().stream().map(this::toDomain).toList();
        List<Question> saved = questionService.batchImport(questions);
        return Result.ok(saved.stream().map(this::toResponse).toList());
    }

    @Operation(summary = "存量补齐 embedding", description = "查询 embedding 为空的题目，分批向量化")
    @PostMapping("/reembed")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Integer> reembed() {
        int count = questionService.embedAll();
        return Result.ok(count);
    }

    @Operation(
            summary = "解析面经为题目（异步）",
            description = "提交后立即返回任务 ID，结果通过 GET /interview-notes/parse/{taskId} 轮询获取（不落库）")
    @PostMapping("/interview-notes/parse")
    public Result<InterviewNoteParseTaskResponse> submitParseInterviewNote(
            @Valid @RequestBody InterviewNoteParseRequest request) {
        String taskId =
                questionService.parseInterviewNoteAsync(request.text(), request.categoryHint());
        return Result.ok(new InterviewNoteParseTaskResponse(taskId, "RUNNING", null, null));
    }

    @Operation(summary = "查询面经解析任务", description = "轮询异步面经解析任务状态与结果（预览编辑后复用 /import 入库）")
    @GetMapping("/interview-notes/parse/{taskId}")
    public Result<InterviewNoteParseTaskResponse> getNoteParseTask(@PathVariable String taskId) {
        InterviewNoteParseTask task = questionService.getNoteParseTask(taskId);
        if (task == null) {
            return Result.ok(
                    new InterviewNoteParseTaskResponse(taskId, "NOT_FOUND", "任务不存在", null));
        }
        List<ParsedQuestionResponse> results =
                task.results() == null
                        ? null
                        : task.results().stream().map(this::toParsedResponse).toList();
        return Result.ok(
                new InterviewNoteParseTaskResponse(
                        task.taskId(), task.status(), task.message(), results));
    }

    // ---- DTO <-> 领域模型转换 ----

    private Question toDomain(CreateQuestionRequest req) {
        return new Question(
                null,
                req.category(),
                req.topic(),
                req.difficulty(),
                req.content(),
                req.standardAnswer(),
                req.tags(),
                null,
                null,
                null);
    }

    private Question toDomain(UpdateQuestionRequest req) {
        return new Question(
                null,
                req.category(),
                req.topic(),
                req.difficulty(),
                req.content(),
                req.standardAnswer(),
                req.tags(),
                null,
                null,
                null);
    }

    private QuestionResponse toResponse(Question q) {
        return new QuestionResponse(
                q.id(),
                q.category(),
                q.topic(),
                q.difficulty(),
                q.content(),
                q.standardAnswer(),
                q.tags(),
                q.embedding() != null,
                q.createdAt(),
                q.updatedAt());
    }

    private ParsedQuestionResponse toParsedResponse(QuestionParseResult result) {
        ParsedQuestion parsed = result.parsed();
        return new ParsedQuestionResponse(
                parsed.category(),
                parsed.topic(),
                parsed.difficulty(),
                parsed.content(),
                parsed.standardAnswer(),
                parsed.tags(),
                result.matchedExistingId());
    }
}
