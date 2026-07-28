package com.aims.gateway.controller.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量导入题目请求。
 *
 * @param questions 待导入题目列表
 */
public record QuestionImportRequest(
        @NotEmpty(message = "questions 不能为空") @Valid List<CreateQuestionRequest> questions) {}
