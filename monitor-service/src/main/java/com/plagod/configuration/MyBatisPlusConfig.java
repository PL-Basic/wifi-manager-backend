package com.plagod.configuration;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Monitor 服务的 MyBatis-Plus 分页配置
@Configuration
public class MyBatisPlusConfig {
   @Bean
   public MybatisPlusInterceptor mybatisPlusInterceptor() {
       MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

       // 为 MYSQL 注册分页和总数统计能力
       PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);

       // 请求超过最后一页时返回空 records，不自动跳回第一页。
       pagination.setOverflow(false);
       // 防止调用方一次查询过多数据，单页最多返回 100 条。
       pagination.setMaxLimit(100L);

       interceptor.addInnerInterceptor(pagination);
       return interceptor;
   }
}