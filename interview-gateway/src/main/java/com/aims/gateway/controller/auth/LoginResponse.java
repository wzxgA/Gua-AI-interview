package com.aims.gateway.controller.auth;

/** 登录响应。 */
public record LoginResponse(String accessToken, String refreshToken, UserInfo user) {

    public record UserInfo(Long id, String username, String displayName, String role) {}
}
