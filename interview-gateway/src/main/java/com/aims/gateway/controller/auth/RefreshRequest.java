package com.aims.gateway.controller.auth;

import jakarta.validation.constraints.NotBlank;

/** 刷新令牌请求。 */
public record RefreshRequest(@NotBlank(message = "refreshToken 不能为空") String refreshToken) {}
