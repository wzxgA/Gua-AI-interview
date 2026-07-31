package com.aims.gateway.config;

import com.aims.infra.persistence.entity.SysUserEntity;
import com.aims.infra.persistence.mapper.SysUserMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 初始化管理员账号：首次启动时自动创建 admin/admin123（仅当 sys_user 表为空时）。 */
@Configuration
public class AdminUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Bean
    ApplicationRunner initAdminUser(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        return args -> {
            Long count = sysUserMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            SysUserEntity admin = new SysUserEntity();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setDisplayName("系统管理员");
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setCreatedAt(Instant.now());
            admin.setUpdatedAt(Instant.now());
            sysUserMapper.insert(admin);
            log.info("初始管理员账号已创建: admin/admin123");
        };
    }
}
