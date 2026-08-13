package com.aims.gateway.controller.system;

import com.aims.ai.config.AiModelProperties;
import com.aims.core.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统配置 REST API。 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "系统配置")
public class SystemController {

    private final AiModelProperties aiModelProperties;

    public SystemController(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    @Operation(summary = "模型档位配置", description = "返回当前生效的模型档位配置（提供商/模型/参数）")
    @GetMapping("/model-tiers")
    public Result<ModelTiersResponse> modelTiers() {
        return Result.ok(ModelTiersResponse.from(aiModelProperties));
    }
}
