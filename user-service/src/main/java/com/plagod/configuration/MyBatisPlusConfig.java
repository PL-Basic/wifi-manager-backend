package com.plagod.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service 的 MyBatis-Plus 插件配置。
 *
 * 该配置会同时作用于用户分页和用户操作申请分页。
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();

        // 指定当前数据库为 MySQL，使分页插件生成正确的 COUNT 和 LIMIT SQL。
        PaginationInnerInterceptor paginationInterceptor =
                new PaginationInnerInterceptor(DbType.MYSQL);

        // 请求超过最后一页时返回空 records，不自动跳回第一页。
        paginationInterceptor.setOverflow(false);

        // 防止调用方一次查询过多数据，单页最多返回 100 条。
        paginationInterceptor.setMaxLimit(100L);

        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}