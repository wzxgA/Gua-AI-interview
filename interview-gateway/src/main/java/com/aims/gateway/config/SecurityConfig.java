package com.aims.gateway.security;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Spring Security 配置：无状态 JWT，放行 /auth/**，其余需认证。 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {}) // 使用已有的 CorsConfig
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/v1/auth/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/access/**")
                                        .permitAll()
                                        .requestMatchers("/ws/**")
                                        .permitAll()
                                        .requestMatchers("/actuator/health")
                                        .permitAll()
                                        // Prometheus 抓取无法携带 JWT，指标仅含聚合数据，放行（内网使用）
                                        .requestMatchers("/actuator/prometheus")
                                        .permitAll()
                                        .requestMatchers("/actuator/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/api/smoke/**",
                                                "/api/v1/audio/**",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(this::writeUnauthorized)
                                        .accessDeniedHandler(this::writeAccessDenied))
                .addFilterBefore(
                        jwtAuthFilter,
                        org.springframework.security.web.authentication
                                .UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 未认证（401）→ 返回统一 Result JSON。 */
    private void writeUnauthorized(
            HttpServletRequest req,
            HttpServletResponse res,
            org.springframework.security.core.AuthenticationException e)
            throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
    }

    /** 无权限（403）→ 返回统一 Result JSON。 */
    private void writeAccessDenied(
            HttpServletRequest req, HttpServletResponse res, AccessDeniedException e)
            throws IOException {
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter()
                .write(objectMapper.writeValueAsString(Result.fail(ErrorCode.ACCESS_DENIED)));
    }
}
