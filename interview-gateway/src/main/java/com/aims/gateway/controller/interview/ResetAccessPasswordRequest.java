package com.aims.gateway.controller.interview;

/** 重置候选人访问密码请求（generate 复用，proctor 仅生成链接时使用）。 */
public record ResetAccessPasswordRequest(String password, ProctorConfig proctor) {}
