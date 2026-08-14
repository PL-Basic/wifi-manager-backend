package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.AuditLog;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogMapperBoundaryTest {

    private static final String NAMESPACE =
            AuditLogMapper.class.getName() + ".";
    private static final String MAPPER_RESOURCE =
            "com/plagod/mapper/xml/AuditLogMapper.xml";

    @Test
    void exposesOnlyTheExistingAuditReadOperations() {
        assertFalse(BaseMapper.class.isAssignableFrom(AuditLogMapper.class));

        Method[] methods = AuditLogMapper.class.getDeclaredMethods();
        assertEquals(2, methods.length);

        Method pageMethod = declaredMethod(
                "selectAuditPage",
                Page.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class);
        assertEquals(Page.class, pageMethod.getReturnType());
        assertParamNames(
                pageMethod,
                null,
                "action",
                "operatorName",
                "target",
                "startTime",
                "endTime");

        Method byIdMethod = declaredMethod("selectAuditById", Long.class);
        assertEquals(AuditLog.class, byIdMethod.getReturnType());
        assertParamNames(byIdMethod, "id");
    }

    @Test
    void loadsOnlySelectStatementsFromMonitorMapperXml() throws Exception {
        Configuration configuration = mapperConfiguration();
        Set<String> statementIds = configuration.getMappedStatementNames()
                .stream()
                .filter(name -> name.startsWith(NAMESPACE))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(
                new LinkedHashSet<>(Arrays.asList(
                        NAMESPACE + "selectAuditPage",
                        NAMESPACE + "selectAuditById")),
                statementIds);
        statementIds.forEach(statementId -> {
            MappedStatement statement =
                    configuration.getMappedStatement(statementId, false);
            assertEquals(SqlCommandType.SELECT,
                    statement.getSqlCommandType(), statementId);
            assertTrue(
                    statement.getResource().contains("AuditLogMapper.xml"),
                    statementId + " loaded from " + statement.getResource());
            assertEquals(
                    NAMESPACE + "auditLogResultMap",
                    statement.getResultMaps().get(0).getId(),
                    statementId);
        });
        assertFalse(statementIds.stream()
                .map(id -> configuration.getMappedStatement(id, false))
                .anyMatch(statement ->
                        statement.getSqlCommandType() == SqlCommandType.INSERT
                                || statement.getSqlCommandType()
                                == SqlCommandType.UPDATE
                                || statement.getSqlCommandType()
                                == SqlCommandType.DELETE));
    }

    @Test
    void mapsEveryAuditColumnForQueries() throws Exception {
        ResultMap resultMap = mapperConfiguration().getResultMap(
                NAMESPACE + "auditLogResultMap");
        Map<String, String> columnToProperty = resultMap.getResultMappings()
                .stream()
                .collect(Collectors.toMap(
                        ResultMapping::getColumn,
                        ResultMapping::getProperty,
                        (left, right) -> {
                            throw new AssertionError(
                                    "Duplicate mapped column " + left);
                        },
                        LinkedHashMap::new));

        assertEquals(
                expectedColumnMappings(),
                columnToProperty);
    }

    private Method declaredMethod(String name, Class<?>... parameterTypes) {
        try {
            return AuditLogMapper.class.getDeclaredMethod(
                    name,
                    parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Missing Mapper method " + name,
                    exception);
        }
    }

    private void assertParamNames(Method method, String... expectedNames) {
        String[] actualNames = Arrays.stream(method.getParameters())
                .map(this::paramName)
                .toArray(String[]::new);
        assertTrue(Arrays.equals(expectedNames, actualNames));
    }

    private String paramName(Parameter parameter) {
        org.apache.ibatis.annotations.Param annotation =
                parameter.getAnnotation(
                        org.apache.ibatis.annotations.Param.class);
        return annotation == null ? null : annotation.value();
    }

    private Map<String, String> expectedColumnMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("id", "id");
        mappings.put("tenant_id", "tenantId");
        mappings.put("scope_type", "scopeType");
        mappings.put("operator_id", "operatorId");
        mappings.put("operator_name", "operatorName");
        mappings.put("action", "action");
        mappings.put("target", "target");
        mappings.put("detail", "detail");
        mappings.put("ip", "ip");
        mappings.put("create_time", "createTime");
        return mappings;
    }

    private Configuration mapperConfiguration() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(
                MAPPER_RESOURCE)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    MAPPER_RESOURCE,
                    configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }
}
