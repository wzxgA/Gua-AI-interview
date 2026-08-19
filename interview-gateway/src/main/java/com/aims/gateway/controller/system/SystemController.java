package com.aims.gateway.controller.system;

import com.aims.core.common.Result;
import com.aims.gateway.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统配置 REST API。 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "系统配置")
public class SystemController {

    private final ModelConfigService modelConfigService;

    public SystemController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @Operation(summary = "模型档位配置", description = "返回当前生效的模型档位配置（提供商/模型/参数，yml 与 DB 合并）")
    @GetMapping("/model-tiers")
    public Result<ModelTiersResponse> modelTiers() {
        return Result.ok(ModelTiersResponse.from(modelConfigService.effectiveProperties()));
    }

    @Operation(
            summary = "AI 模型配置（读写）",
            description =
                    "返回 yml 与 DB 合并后的生效配置：provider（url/掩码 key/来源）+ tier（参数 + 档位级 override url/掩码"
                            + " key）")
    @GetMapping("/model-config")
    public Result<ModelConfigView> modelConfig() {
        return Result.ok(modelConfigService.getConfig());
    }

    @Operation(
            summary = "保存 AI 模型配置",
            description =
                    "全量保存 provider 与 tier 配置（UPSERT），保存成功后立即热刷新模型路由。apiKey 语义：不传=保留，空串=清除覆盖回退"
                            + " yml，非空=覆盖。")
    @PutMapping("/model-config")
    public Result<ModelConfigView> saveModelConfig(@RequestBody SaveModelConfigCommand command) {
        return Result.ok(modelConfigService.save(command));
    }

    @Operation(summary = "AI 配置连通性测试", description = "用请求体中的配置（不保存）对各档位发起最小调用，返回每个档位的成功/失败与耗时")
    @PostMapping("/model-config/test")
    public Result<ModelConfigTestResult> testModelConfig(
            @RequestBody SaveModelConfigCommand command) {
        return Result.ok(modelConfigService.test(command));
    }

    @Operation(summary = "恢复 AI 模型配置默认", description = "清空 DB 中的覆盖配置，整体回退 yml 并热刷新")
    @PostMapping("/model-config/reset")
    public Result<ModelConfigView> resetModelConfig() {
        return Result.ok(modelConfigService.reset());
    }

    @Operation(
            summary = "删除自定义 provider",
            description = "仅允许删除 DB 中新增的自定义 provider；yml 内置 provider 或正被档位引用的 provider 不可删")
    @DeleteMapping("/model-config/provider/{name}")
    public Result<ModelConfigView> deleteProvider(@PathVariable String name) {
        return Result.ok(modelConfigService.deleteProvider(name));
    }
}
