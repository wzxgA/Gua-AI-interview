package com.aims.gateway.controller.position;

import com.aims.core.common.PageQuery;
import com.aims.core.common.Result;
import com.aims.infra.persistence.dto.CreatePositionRequest;
import com.aims.infra.persistence.dto.UpdatePositionRequest;
import com.aims.infra.persistence.entity.PositionEntity;
import com.aims.infra.persistence.service.PositionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 岗位管理 REST API。 */
@RestController
@RequestMapping("/api/v1/positions")
@Tag(name = "岗位管理")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @Operation(summary = "创建岗位", description = "创建新岗位，状态默认为 ACTIVE")
    @PostMapping("")
    public Result<PositionResponse> create(@Valid @RequestBody CreatePositionRequest req) {
        PositionEntity entity = positionService.create(req);
        return Result.ok(PositionResponse.from(entity));
    }

    @Operation(summary = "更新岗位", description = "更新岗位信息，仅更新非 null 字段")
    @PutMapping("/{id}")
    public Result<PositionResponse> update(
            @PathVariable Long id, @Valid @RequestBody UpdatePositionRequest req) {
        PositionEntity entity = positionService.update(id, req);
        return Result.ok(PositionResponse.from(entity));
    }

    @Operation(summary = "查询岗位详情", description = "根据 ID 查询岗位详情")
    @GetMapping("/{id}")
    public Result<PositionResponse> getById(@PathVariable Long id) {
        PositionEntity entity = positionService.getById(id);
        return Result.ok(PositionResponse.from(entity));
    }

    @Operation(summary = "分页查询岗位列表", description = "支持按名称模糊搜索和部门精确过滤")
    @GetMapping("")
    public Result<IPage<PositionEntity>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String department) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return Result.ok(positionService.page(pageQuery, title, department));
    }

    @Operation(summary = "删除岗位", description = "软删除，将状态置为 INACTIVE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.ok(null);
    }

    @Operation(summary = "触发 JD 向量化", description = "调用模型将岗位 JD 文本向量化并存储")
    @PostMapping("/{id}/embed")
    public Result<Void> embed(@PathVariable Long id) {
        positionService.embed(id);
        return Result.ok(null);
    }
}
