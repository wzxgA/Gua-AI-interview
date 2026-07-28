package com.aims.infra.persistence.service;

import com.aims.core.common.PageQuery;
import com.aims.infra.persistence.dto.CreatePositionRequest;
import com.aims.infra.persistence.dto.UpdatePositionRequest;
import com.aims.infra.persistence.entity.PositionEntity;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** 岗位 CRUD 服务。 */
public interface PositionService {

    /** 创建岗位，状态默认 ACTIVE。 */
    PositionEntity create(CreatePositionRequest req);

    /** 更新岗位，仅更新非 null 字段；岗位不存在抛 BizException。 */
    PositionEntity update(Long id, UpdatePositionRequest req);

    /** 查询岗位详情，同时填充 hasEmbedding；不存在抛 BizException。 */
    PositionEntity getById(Long id);

    /** 分页查询岗位列表，支持按名称模糊搜索和部门精确过滤。 */
    IPage<PositionEntity> page(PageQuery pageQuery, String title, String department);

    /** 软删除：将状态置为 INACTIVE；岗位不存在抛 BizException。 */
    void delete(Long id);

    /** 触发 JD 向量化：调用模型生成向量并写入 embedding 列。 */
    void embed(Long id);
}
