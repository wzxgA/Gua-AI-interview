package com.aims.gateway.controller.auth;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.Result;
import com.aims.core.common.exception.BizException;
import com.aims.gateway.security.JwtUtil;
import com.aims.gateway.security.RefreshTokenService;
import com.aims.infra.persistence.entity.SysUserEntity;
import com.aims.infra.persistence.service.SysUserService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证 Controller：登录 / 刷新 / 登出 / 当前用户。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            SysUserService sysUserService,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            RefreshTokenService refreshTokenService) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        SysUserEntity user = sysUserService.findByUsername(req.username());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        String accessToken =
                jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        refreshTokenService.save(refreshToken, user.getUsername());
        return Result.ok(
                new LoginResponse(
                        accessToken,
                        refreshToken,
                        new LoginResponse.UserInfo(
                                user.getId(),
                                user.getUsername(),
                                user.getDisplayName(),
                                user.getRole())));
    }

    @PostMapping("/refresh")
    public Result<RefreshResponse> refresh(@RequestBody RefreshRequest req) {
        String username = refreshTokenService.validate(req.refreshToken());
        if (username == null) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        SysUserEntity user = sysUserService.findByUsername(username);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 旧 refreshToken 失效，签发新的
        refreshTokenService.revoke(req.refreshToken());
        String accessToken =
                jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
        refreshTokenService.save(newRefreshToken, username);
        return Result.ok(new RefreshResponse(accessToken, newRefreshToken));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody RefreshRequest req) {
        refreshTokenService.revoke(req.refreshToken());
        return Result.ok(null);
    }

    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me() {
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        SysUserEntity user = sysUserService.findByUsername(auth.getName());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return Result.ok(
                new LoginResponse.UserInfo(
                        user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    }
}
