package com.plagod.configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.plagod.mybatis.WifiMybatisAutoConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MybatisAutoConfigurationContextTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withPropertyValues(
                            "spring.cloud.discovery.enabled=false",
                            "spring.cloud.service-registry.auto-registration.enabled=false",
                            "spring.cloud.nacos.discovery.enabled=false")
                    .withUserConfiguration(AutoConfigurationProbe.class);

    @Test
    void discoversSharedPaginationConfiguration() {
        contextRunner.run(context -> {
            assertEquals(
                    1,
                    context.getBeansOfType(MybatisPlusInterceptor.class).size());
            assertEquals(
                    1,
                    context.getBeansOfType(
                            WifiMybatisAutoConfiguration.class).size());
            assertEquals(
                    WifiMybatisAutoConfiguration.class.getName(),
                    context.getBeanFactory()
                            .getBeanDefinition("mybatisPlusInterceptor")
                            .getFactoryBeanName());

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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class AutoConfigurationProbe {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }
    }
}
