package com.plagod.configuration;

import com.plagod.AdminApplication;
import com.plagod.web.ApiErrorResponseFactory;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "wifi.internal.token=admin-context-test-internal-token",
                "wifi.security.enabled=false"
        })
class AdminPersistenceBoundaryContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void adminContextHasNoPersistenceCapability() throws Exception {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertFalse(ClassUtils.isPresent(
                "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                context.getClassLoader()));
        assertFalse(ClassUtils.isPresent(
                "org.mybatis.spring.mapper.MapperFactoryBean",
                context.getClassLoader()));
        assertEquals(0, new PathMatchingResourcePatternResolver(
                context.getClassLoader()).getResources(
                "classpath*:com/plagod/mapper/xml/*Mapper.xml").length);
        assertFalse(contextContainsType(context.getBeanDefinitionNames(),
                context,
                ".mapper."));
        assertFalse(contextContainsType(context.getBeanDefinitionNames(),
                context,
                ".entity."));
        assertFalse(contextContainsType(context.getBeanDefinitionNames(),
                context,
                "sqlsession"));
        assertFalse(contextContainsType(context.getBeanDefinitionNames(),
                context,
                "mybatis"));
    }

    private boolean contextContainsType(
            String[] beanNames,
            org.springframework.context.ApplicationContext context,
            String token) {
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        return Arrays.stream(beanNames)
                .map(name -> context.getType(name))
                .filter(type -> type != null)
                .map(Class::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.contains(normalizedToken));
    }

    @Test
    void adminConsumesSingleAdviceAndSafeHealthBaseline() {
        Map<String, Object> adviceBeans =
                context.getBeansWithAnnotation(
                        RestControllerAdvice.class);

        assertEquals(1, adviceBeans.size());
        assertTrue(adviceBeans.values().iterator().next()
                instanceof GlobalExceptionHandler);
        assertEquals(1, context.getBeansOfType(
                ApiErrorResponseFactory.class).size());
        assertEquals(1, context.getBeansOfType(
                ErrorDecoder.class).size());
        assertTrue(context.getBean(ErrorDecoder.class)
                instanceof AdminFeignErrorDecoder);
        assertEquals(1, context.getBeansOfType(
                HealthEndpoint.class).size());

        assertEquals(
                "health,info",
                environment.getProperty(
                        "management.endpoints.web.exposure.include"));
        assertEquals(
                "never",
                environment.getProperty(
                        "management.endpoint.health.show-details"));
        assertEquals(
                "true",
                environment.getProperty(
                        "management.endpoint.health.probes.enabled"));
        assertEquals(
                "livenessState",
                environment.getProperty(
                        "management.endpoint.health.group.liveness.include"));
        assertEquals(
                "readinessState",
                environment.getProperty(
                        "management.endpoint.health.group.readiness.include"));
    }
}
