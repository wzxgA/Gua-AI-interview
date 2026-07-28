package com.aims.infra.persistence.service;

import com.aims.core.question.Question;
import com.aims.infra.persistence.entity.QuestionSearchResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/**
 * 题库服务：CRUD + 批量导入 + 向量化 ETL。
 *
 * <p>向量化通过 {@link com.aims.ai.router.ModelRouter} 完成，create/update 后同步 embed， batchImport 后异步
 * embedBatch。
 */
public interface QuestionService {

    /** 创建题目（保存后同步向量化）。 */
    Question create(Question question);

    /** 更新题目（content 变化时重新向量化）。 */
    Question update(Long id, Question question);

    /** 按 ID 查询题目，不存在抛 {@link com.aims.core.common.exception.BizException}。 */
    Question getById(Long id);

    /** 分页查询题目列表，支持 category/difficulty/topic 过滤。 */
    IPage<Question> page(int pageNum, int size, String category, String difficulty, String topic);

    /** 删除题目。 */
    void delete(Long id);

    /** 批量导入题目（落库后异步向量化）。 */
    List<Question> batchImport(List<Question> questions);

    /** 单条向量化。 */
    void embed(Long id);

    /** 存量补齐 embedding（查询 embedding 为空的题目，批量向量化）。 */
    int embedAll();

    /** 向量相似度检索。 */
    List<QuestionSearchResult> searchByVector(String query, int topK);
}
