package com.aims.gateway.controller.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 面经解析请求。
 *
 * @param text 面经文本（1-20000 字符）
 * @param categoryHint 可选大方向提示（如"Java 后端"），辅助 AI 归纳主题
 */
public record InterviewNoteParseRequest(
        @NotBlank(message = "text 不能为空") @Size(max = 20000, message = "面经文本过长，请分段上传（最多 20000 字符）")
                String text,
        String categoryHint) {}
