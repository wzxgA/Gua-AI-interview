package com.aims.infra.persistence.service.impl;

import com.aims.ai.router.ModelRouter;
import com.aims.core.common.ErrorCode;
import com.aims.core.common.PageQuery;
import com.aims.core.common.exception.BizException;
import com.aims.core.position.PositionStatus;
import com.aims.infra.persistence.PgVectorSupport;
import com.aims.infra.persistence.dto.CreatePositionRequest;
import com.aims.infra.persistence.dto.UpdatePositionRequest;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.mapper.PositionMapper;
import com.aims.infra.persistence.service.PositionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 岗位 CRUD 服务实现。
 *
 * <p>embedding 字段为 pgvector 类型，不参与 MyBatis-Plus 自动映射， 通过 {@link PositionMapper#updateEmbedding} 自定义
 * SQL 写入，通过 {@link PositionMapper#existsEmbedding} 判断是否存在。
 */
@Service
public class PositionServiceImpl extends ServiceImpl<PositionMapper, PositionEntity>
        implements PositionService {

    private final ModelRouter modelRouter;

    public PositionServiceImpl(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @Override
    public PositionEntity create(CreatePositionRequest req) {
        PositionEntity entity = new PositionEntity();
        entity.setTitle(req.title());
        entity.setDepartment(req.department());
        entity.setJdText(req.jdText());
        entity.setRequirementsJson(req.requirementsJson());
        entity.setStatus(PositionStatus.ACTIVE.name());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        save(entity);
        return entity;
    }

    @Override
    public PositionEntity update(Long id, UpdatePositionRequest req) {
        PositionEntity entity = getById(id);
        if (req.title() != null) {
            entity.setTitle(req.title());
        }
        if (req.department() != null) {
            entity.setDepartment(req.department());
        }
        if (req.jdText() != null) {
            entity.setJdText(req.jdText());
        }
        if (req.requirementsJson() != null) {
            entity.setRequirementsJson(req.requirementsJson());
        }
        if (req.status() != null) {
            entity.setStatus(req.status());
        }
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
        return entity;
    }

    @Override
    public PositionEntity getById(Long id) {
        PositionEntity entity = baseMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在: " + id);
        }
        entity.setHasEmbedding(baseMapper.existsEmbedding(id));
        return entity;
    }

    @Override
    public IPage<PositionEntity> page(PageQuery pageQuery, String title, String department) {
        Page<PositionEntity> page = new Page<>(pageQuery.page(), pageQuery.size());
        LambdaQueryWrapper<PositionEntity> wrapper = Wrappers.lambdaQuery(PositionEntity.class);
        if (title != null && !title.isBlank()) {
            wrapper.like(PositionEntity::getTitle, title);
        }
        if (department != null && !department.isBlank()) {
            wrapper.eq(PositionEntity::getDepartment, department);
        }
        wrapper.orderByDesc(PositionEntity::getCreatedAt);
        IPage<PositionEntity> result = baseMapper.selectPage(page, wrapper);
        result.getRecords()
                .forEach(
                        entity ->
                                entity.setHasEmbedding(baseMapper.existsEmbedding(entity.getId())));
        return result;
    }

    @Override
    public void delete(Long id) {
        PositionEntity entity = getById(id);
        entity.setStatus(PositionStatus.INACTIVE.name());
        entity.setUpdatedAt(Instant.now());
        updateById(entity);
    }

    @Override
    public void embed(Long id) {
        PositionEntity entity = getById(id);
        String jdText = entity.getJdText();
        if (jdText == null || jdText.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位 JD 为空，无法向量化: " + id);
        }
        float[] embedding = modelRouter.embed(jdText);
        baseMapper.updateEmbedding(id, PgVectorSupport.toVectorString(embedding));
    }
}
