package com.aims.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件 + Mapper 扫描。
 *
 * <p>Mapper 接口统一放在 com.aims.infra.persistence.mapper 包下。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.aims.infra.persistence.mapper")
public class MybatisPlusConfig {

    /** 分页插件：PostgreSQL 方言。 */
    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
