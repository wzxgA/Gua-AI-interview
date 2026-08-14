package com.aims.gateway.security;

import com.aims.infra.persistence.entity.SysUserEntity;
import com.aims.infra.persistence.service.SysUserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT 认证过滤器：从 Authorization 头提取 token → 校验 → 校验账号状态 → 写入 SecurityContext。 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SysUserService sysUserService;

    public JwtAuthFilter(JwtUtil jwtUtil, SysUserService sysUserService) {
        this.jwtUtil = jwtUtil;
        this.sysUserService = sysUserService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                // GUEST：候选人 guestToken，跳过用户表校验，认证上下文绑定 sid
                if ("GUEST".equals(claims.get("role", String.class))) {
                    Long sid = GuestTokenService.extractSessionId(claims);
                    UsernamePasswordAuthenticationToken guestAuth =
                            new UsernamePasswordAuthenticationToken(
                                    "guest",
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_GUEST")));
                    guestAuth.setDetails(sid);
                    SecurityContextHolder.getContext().setAuthentication(guestAuth);
                    chain.doFilter(req, res);
                    return;
                }
                // 管理员/面试官：校验账号是否仍处于启用状态
                SysUserEntity user = sysUserService.findByUsername(claims.getSubject());
                if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(req, res);
                    return;
                }
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                claims.getSubject(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // 无效 token 不设置认证上下文，由 Security 链后续处理为 401
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
