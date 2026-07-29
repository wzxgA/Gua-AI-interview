package com.aims.infra.persistence.service;

import com.aims.core.common.PageQuery;
import com.aims.infra.persistence.entity.ResumeEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历服务：上传 + MinIO 存储 + PDF 文本抽取 + 结构化解析 + 向量化。
 *
 * <p>upload 完成后异步触发解析（虚拟线程），parse / embed 可由调用方手动触发。
 */
public interface ResumeService {

    /**
     * 上传简历：保存记录获取 ID -> 上传 MinIO -> 抽取文本 -> 异步触发解析。
     *
     * @param file 简历文件（PDF / TXT）
     * @param candidateName 候选人姓名
     * @param phone 联系电话（可选）
     * @param email 邮箱（可选）
     * @return 简历实体（含 ID 与 fileUrl，解析状态为 PENDING）
     */
    ResumeEntity upload(MultipartFile file, String candidateName, String phone, String email);

    /**
     * 触发结构化解析：取 rawText -> 调 AI -> 存 parsedJson -> 更新状态。
     *
     * @param id 简历 ID
     * @return 更新后的简历实体
     */
    ResumeEntity parse(Long id);

    /**
     * 查询单条简历。
     *
     * @param id 简历 ID
     * @return 简历实体
     */
    ResumeEntity getById(Long id);

    /**
     * 分页查询简历列表。
     *
     * @param pageQuery 分页参数
     * @param candidateName 候选人姓名模糊匹配（可选）
     * @return 分页结果
     */
    IPage<ResumeEntity> page(PageQuery pageQuery, String candidateName);

    /**
     * 删除简历：先删 MinIO 对象，再删数据库记录。
     *
     * @param id 简历 ID
     */
    void delete(Long id);

    /**
     * 触发向量化：拼接结构化文本 -> 调 EMBEDDING 档位 -> 写入 embedding 列。
     *
     * <p>仅 PARSED 且向量状态为 PENDING/FAILED 的简历允许执行。
     *
     * @param id 简历 ID
     */
    void embed(Long id);

    /**
     * 使当前向量失效并重新触发向量化。
     *
     * @param id 简历 ID
     */
    void reembed(Long id);

    /**
     * 检查简历是否已生成向量。
     *
     * @param id 简历 ID
     * @return true=已生成
     */
    boolean hasEmbedding(Long id);
}
