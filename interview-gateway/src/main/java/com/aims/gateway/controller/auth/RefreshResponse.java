package com.aims.gateway.controller.auth;

/** 刷新令牌响应。 */
public record RefreshResponse(String accessToken, String refreshToken) {}
