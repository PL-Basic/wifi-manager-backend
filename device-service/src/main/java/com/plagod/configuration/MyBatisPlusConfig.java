package com.plagod.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


// 分页拦截器
@Configuration
public class MyBatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 注册分页拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 指定数据库类型
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 请求超过最后一页时不自动跳回第一页
        pagination.setOverflow(false);
        // 单次查询最多返回100条，作为持久层保护
        pagination.setMaxLimit(100L);

        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
