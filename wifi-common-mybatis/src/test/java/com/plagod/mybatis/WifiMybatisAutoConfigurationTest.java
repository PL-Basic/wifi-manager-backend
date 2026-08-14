package com.plagod.mybatis;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WifiMybatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(AutoConfigurationProbe.class);

    @Test
    void providesBoundedMysqlPaginationByDefault() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("mybatisPlusInterceptor"));

            MybatisPlusInterceptor interceptor =
                    context.getBean(MybatisPlusInterceptor.class);
            SqlSessionFactory sqlSessionFactory =
                    context.getBean(SqlSessionFactory.class);
            assertTrue(sqlSessionFactory.getConfiguration()
                    .getInterceptors().contains(interceptor));

            List<InnerInterceptor> innerInterceptors =
                    interceptor.getInterceptors();
            assertEquals(1, innerInterceptors.size());
            assertTrue(innerInterceptors.get(0)
                    instanceof PaginationInnerInterceptor);

            PaginationInnerInterceptor pagination =
                    (PaginationInnerInterceptor) innerInterceptors.get(0);
            assertEquals(DbType.MYSQL, pagination.getDbType());
            assertFalse(pagination.isOverflow());
            assertEquals(Long.valueOf(100L), pagination.getMaxLimit());
        });
    }

    @Test
    void keepsConsumerProvidedInterceptor() {
        contextRunner.withUserConfiguration(CustomInterceptorConfig.class)
                .run(context -> {
                    assertEquals(
                            1,
                            context.getBeansOfType(
                                    MybatisPlusInterceptor.class).size());
                    assertSame(
                            CustomInterceptorConfig.CUSTOM_INTERCEPTOR,
                            context.getBean(MybatisPlusInterceptor.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class AutoConfigurationProbe {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomInterceptorConfig {

        private static final MybatisPlusInterceptor CUSTOM_INTERCEPTOR =
                new MybatisPlusInterceptor();

        @Bean
        MybatisPlusInterceptor customMybatisPlusInterceptor() {
            return CUSTOM_INTERCEPTOR;
        }
    }
}
