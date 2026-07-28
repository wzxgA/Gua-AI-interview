package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import com.aims.core.question.Question;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.entity.QuestionEntity;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.aims.infra.persistence.mapper.QuestionMapper;
import com.aims.infra.persistence.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    private final QuestionMapper baseMapper;
    private final ModelRouter modelRouter;

    public QuestionServiceImpl(QuestionMapper questionMapper, ModelRouter modelRouter) {
        this.baseMapper = questionMapper;
        this.modelRouter = modelRouter;
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
