package com.aims.infra.persistence.service.impl;

import com.aims.agent.InterviewNotePromptBuilder;
import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelRouter;
import com.aims.ai.router.ModelTier;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.question.ParsedQuestion;
import com.aims.core.question.ParsedQuestionList;
import com.aims.core.question.Question;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.InterviewNoteParseTask;
import com.aims.infra.persistence.dto.QuestionParseResult;
import com.aims.infra.persistence.entity.QuestionEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.mapper.QuestionMapper;
import com.aims.infra.persistence.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 题库服务实现：CRUD + 批量导入 + 向量化 ETL。
 *
 * <p>向量化策略：
 *
 * <ul>
 *   <li>create：保存后同步 embed（失败不阻断，可后续 embedAll 补齐）
 *   <li>update：content 变化时重新 embed
 *   <li>batchImport：批量落库后异步 embedBatch（虚拟线程）
 *   <li>embedAll：批量查询 embedding 为空的记录，分批 embedBatch 补齐
 * </ul>
 */
@Service
public class QuestionServiceImpl implements QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionServiceImpl.class);

    /** embedAll 单批大小。 */
    private static final int EMBED_BATCH_SIZE = 100;

    /** 单次面经解析最大题目数（超出截断）。 */
    private static final int PARSE_MAX_QUESTIONS = 50;

    private static final Set<String> CATEGORIES = Set.of("TECHNICAL", "BEHAVIORAL", "PROJECT");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 面经解析异步任务表（内存，对齐 reembed-batch 模式；单实例场景足够，多实例需任务存储外置）。 */
    private final ConcurrentHashMap<String, InterviewNoteParseTask> noteParseTasks =
            new ConcurrentHashMap<>();

    /** 已完成任务保留时长（分钟），提交新任务时惰性清理。 */
    private static final long NOTE_TASK_RETENTION_MINUTES = 30;

    private final QuestionMapper baseMapper;
    private final ModelRouter modelRouter;
    private final AiChatFacade aiChatFacade;

    public QuestionServiceImpl(
            QuestionMapper questionMapper, ModelRouter modelRouter, AiChatFacade aiChatFacade) {
        this.baseMapper = questionMapper;
        this.modelRouter = modelRouter;
        this.aiChatFacade = aiChatFacade;
    }

    @Override
    public Question create(Question question) {
        QuestionEntity entity = toEntity(question);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        baseMapper.insert(entity);

        // 同步向量化（失败不阻断创建流程，可后续 embedAll 补齐）
        try {
            float[] vector = modelRouter.embed(entity.getContent());
            String vecStr = PgVectorSupport.toVectorString(vector);
            baseMapper.updateEmbedding(entity.getId(), vecStr);
        } catch (Exception e) {
            log.warn("创建题目后向量化失败 id={}", entity.getId(), e);
        }

        // 重新查询返回完整数据（含 DB 默认值与 embedding 状态）
        QuestionEntity saved = baseMapper.selectById(entity.getId());
        return toDomain(saved);
    }

    @Override
    public Question update(Long id, Question question) {
        QuestionEntity entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "题目不存在: " + id);
        }

        String oldContent = entity.getContent();

        // 按非空字段更新（null 表示不更新）
        if (question.category() != null) entity.setCategory(question.category());
        if (question.topic() != null) entity.setTopic(question.topic());
        if (question.difficulty() != null) entity.setDifficulty(question.difficulty());
        if (question.content() != null) entity.setContent(question.content());
        if (question.standardAnswer() != null) entity.setStandardAnswer(question.standardAnswer());
        if (question.tags() != null) entity.setTags(question.tags().toArray(String[]::new));
        entity.setUpdatedAt(Instant.now());

        baseMapper.updateById(entity);

        // content 变化时重新向量化
        if (question.content() != null && !question.content().equals(oldContent)) {
            try {
                float[] vector = modelRouter.embed(entity.getContent());
                String vecStr = PgVectorSupport.toVectorString(vector);
                baseMapper.updateEmbedding(id, vecStr);
            } catch (Exception e) {
                log.warn("更新题目后向量化失败 id={}", id, e);
            }
        }

        QuestionEntity updated = baseMapper.selectById(id);
        return toDomain(updated);
    }

    @Override
    public Question getById(Long id) {
        QuestionEntity entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "题目不存在: " + id);
        }
        return toDomain(entity);
    }

    @Override
    public IPage<Question> page(
            int pageNum, int size, String category, String difficulty, String topic) {
        Page<QuestionEntity> page = new Page<>(pageNum, size);
        LambdaQueryWrapper<QuestionEntity> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(QuestionEntity::getCategory, category);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            wrapper.eq(QuestionEntity::getDifficulty, difficulty);
        }
        if (topic != null && !topic.isBlank()) {
            wrapper.like(QuestionEntity::getTopic, topic);
        }
        wrapper.orderByDesc(QuestionEntity::getId);
        baseMapper.selectPage(page, wrapper);
        return page.convert(this::toDomain);
    }

    @Override
    public void delete(Long id) {
        int rows = baseMapper.deleteById(id);
        if (rows == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "题目不存在: " + id);
        }
    }

    @Override
    public List<Question> batchImport(List<Question> questions) {
        List<Question> saved = new ArrayList<>();
        int failed = 0;

        for (Question q : questions) {
            try {
                QuestionEntity entity = toEntity(q);
                Instant now = Instant.now();
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                baseMapper.insert(entity);
                QuestionEntity savedEntity = baseMapper.selectById(entity.getId());
                saved.add(toDomain(savedEntity));
            } catch (Exception e) {
                log.warn("批量导入单条失败 content={}", truncate(q.content(), 80), e);
                failed++;
            }
        }

        if (saved.isEmpty() && !questions.isEmpty()) {
            throw new BizException(ErrorCode.QUESTION_IMPORT_PARTIAL, "批量导入全部失败");
        }
        if (failed > 0) {
            log.warn("批量导入部分失败: 成功 {} 条, 失败 {} 条", saved.size(), failed);
        }

        // 异步批量向量化（虚拟线程）
        if (!saved.isEmpty()) {
            final List<Long> savedIds = saved.stream().map(Question::id).toList();
            final List<String> contents = saved.stream().map(Question::content).toList();
            Thread.startVirtualThread(
                    () -> {
                        try {
                            List<float[]> vectors = modelRouter.embedBatch(contents);
                            for (int i = 0; i < savedIds.size(); i++) {
                                String vecStr = PgVectorSupport.toVectorString(vectors.get(i));
                                baseMapper.updateEmbedding(savedIds.get(i), vecStr);
                            }
                            log.info("批量向量化完成 count={}", savedIds.size());
                        } catch (Exception e) {
                            log.error("批量向量化异步任务失败 count={}", savedIds.size(), e);
                        }
                    });
        }

        return saved;
    }

    @Override
    public String parseInterviewNoteAsync(String text, String categoryHint) {
        expireNoteParseTasks();
        String taskId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        noteParseTasks.put(
                taskId, new InterviewNoteParseTask(taskId, "RUNNING", null, null, startedAt, null));
        log.info("面经解析任务已启动 taskId={}", taskId);

        Thread.startVirtualThread(
                () -> {
                    try {
                        List<QuestionParseResult> results = parseInterviewNote(text, categoryHint);
                        noteParseTasks.put(
                                taskId,
                                new InterviewNoteParseTask(
                                        taskId,
                                        "SUCCESS",
                                        results,
                                        null,
                                        startedAt,
                                        Instant.now()));
                        log.info("面经解析任务完成 taskId={} count={}", taskId, results.size());
                    } catch (Exception e) {
                        log.warn("面经解析任务失败 taskId={}", taskId, e);
                        noteParseTasks.put(
                                taskId,
                                new InterviewNoteParseTask(
                                        taskId,
                                        "FAILED",
                                        null,
                                        e.getMessage(),
                                        startedAt,
                                        Instant.now()));
                    }
                });
        return taskId;
    }

    @Override
    public InterviewNoteParseTask getNoteParseTask(String taskId) {
        return noteParseTasks.get(taskId);
    }

    /** 惰性清理：移除已结束且超保留时长的任务。 */
    private void expireNoteParseTasks() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(NOTE_TASK_RETENTION_MINUTES));
        noteParseTasks
                .entrySet()
                .removeIf(
                        e -> {
                            Instant finished = e.getValue().finishedAt();
                            return finished != null && finished.isBefore(cutoff);
                        });
    }

    @Override
    public List<QuestionParseResult> parseInterviewNote(String text, String categoryHint) {
        // 手动获取原始输出再解析：可容忍 Markdown 代码块包裹等常见格式漂移，并在失败时记录原始文本便于诊断
        String raw =
                aiChatFacade.call(
                        ModelTier.STANDARD,
                        InterviewNotePromptBuilder.parseSystem(),
                        InterviewNotePromptBuilder.parseUser(text, categoryHint));
        ParsedQuestionList parsed = parseJsonOutput(raw);
        if (parsed == null || parsed.questions() == null || parsed.questions().isEmpty()) {
            throw new BizException(ErrorCode.MODEL_OUTPUT_PARSE_FAILED, "未从面经中解析出题目");
        }

        List<ParsedQuestion> normalized =
                parsed.questions().stream()
                        .limit(PARSE_MAX_QUESTIONS)
                        .map(this::normalize)
                        .filter(q -> q.content() != null && !q.content().isBlank())
                        .toList();
        if (normalized.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_OUTPUT_PARSE_FAILED, "未从面经中解析出有效题目");
        }
        return markDuplicates(normalized);
    }

    /** 宽容解析 AI 输出：剥离 Markdown 代码块、提取首个 JSON 对象，失败时记录原始输出。 */
    private ParsedQuestionList parseJsonOutput(String raw) {
        String cleaned = stripCodeFence(raw);
        try {
            return OBJECT_MAPPER.readValue(cleaned, ParsedQuestionList.class);
        } catch (Exception e) {
            log.warn("面经 AI 输出解析失败, 原始输出(截断): {}", truncate(cleaned, 500), e);
            throw new BizException(
                    ErrorCode.MODEL_OUTPUT_PARSE_FAILED, "面经解析失败：模型输出格式异常，请重试或调整面经文本");
        }
    }

    /** 剥离 ```json/``` 代码块包裹，并截取第一个 {@code {} 与最后一个 {@code }} 之间的内容。 */
    private static String stripCodeFence(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s.trim();
    }

    /** 字段归一化：枚举非法值回落默认，空 topic 取题干前缀，过滤空标签。 */
    private ParsedQuestion normalize(ParsedQuestion q) {
        String content = q.content() == null ? "" : q.content().trim();
        String category =
                q.category() != null && CATEGORIES.contains(q.category())
                        ? q.category()
                        : "TECHNICAL";
        String difficulty =
                q.difficulty() != null && DIFFICULTIES.contains(q.difficulty())
                        ? q.difficulty()
                        : "MEDIUM";
        String topic =
                q.topic() == null || q.topic().isBlank()
                        ? fallbackTopic(content)
                        : q.topic().trim();
        List<String> tags =
                q.tags() == null
                        ? List.of()
                        : q.tags().stream().filter(t -> t != null && !t.isBlank()).toList();
        return new ParsedQuestion(category, topic, difficulty, content, q.standardAnswer(), tags);
    }

    /** 与题库已有题目按题干精确去重，命中时回填 matchedExistingId。 */
    private List<QuestionParseResult> markDuplicates(List<ParsedQuestion> questions) {
        List<String> contents = questions.stream().map(ParsedQuestion::content).toList();
        Map<String, Long> existingByContent = new HashMap<>();
        if (!contents.isEmpty()) {
            List<QuestionEntity> existing =
                    baseMapper.selectList(
                            new LambdaQueryWrapper<QuestionEntity>()
                                    .in(QuestionEntity::getContent, contents));
            for (QuestionEntity e : existing) {
                existingByContent.putIfAbsent(e.getContent(), e.getId());
            }
        }
        return questions.stream()
                .map(q -> new QuestionParseResult(q, existingByContent.get(q.content())))
                .toList();
    }

    private static String fallbackTopic(String content) {
        if (content == null || content.isBlank()) {
            return "面经";
        }
        return content.length() <= 20 ? content : content.substring(0, 20);
    }

    @Override
    public void embed(Long id) {
        QuestionEntity entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "题目不存在: " + id);
        }
        float[] vector = modelRouter.embed(entity.getContent());
        String vecStr = PgVectorSupport.toVectorString(vector);
        baseMapper.updateEmbedding(id, vecStr);
    }

    @Override
    public int embedAll() {
        int total = 0;
        while (true) {
            List<QuestionEntity> batch = baseMapper.selectWithoutEmbedding(EMBED_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            try {
                List<String> contents = batch.stream().map(QuestionEntity::getContent).toList();
                List<float[]> vectors = modelRouter.embedBatch(contents);
                for (int i = 0; i < batch.size(); i++) {
                    String vecStr = PgVectorSupport.toVectorString(vectors.get(i));
                    baseMapper.updateEmbedding(batch.get(i).getId(), vecStr);
                }
                total += batch.size();
            } catch (Exception e) {
                log.error("批量向量化失败 batch size={}", batch.size(), e);
                break;
            }

            if (batch.size() < EMBED_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    @Override
    public List<QuestionSearchResult> searchByVector(String query, int topK) {
        float[] queryVector = modelRouter.embed(query);
        String vecStr = PgVectorSupport.toVectorString(queryVector);
        return baseMapper.searchByVector(vecStr, topK);
    }

    // ---- 领域模型 <-> 实体转换 ----

    private QuestionEntity toEntity(Question question) {
        QuestionEntity entity = new QuestionEntity();
        entity.setCategory(question.category());
        entity.setTopic(question.topic());
        entity.setDifficulty(question.difficulty());
        entity.setContent(question.content());
        entity.setStandardAnswer(question.standardAnswer());
        entity.setTags(question.tags() == null ? null : question.tags().toArray(String[]::new));
        return entity;
    }

    private Question toDomain(QuestionEntity entity) {
        return new Question(
                entity.getId(),
                entity.getCategory(),
                entity.getTopic(),
                entity.getDifficulty(),
                entity.getContent(),
                entity.getStandardAnswer(),
                entity.getTags() == null ? null : Arrays.asList(entity.getTags()),
                entity.getEmbedding(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
