package com.plagod.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.plagod.entity.monitor.AuditLog;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditLogMapperExecutionTest {

    private JdbcDataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:audit_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");
        createSchemaAndRecords();
        sqlSessionFactory = sqlSessionFactory();
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).execute("drop all objects");
    }

    @Test
    void executesFilteredPageAndIdLookupThroughTheRealMapper() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            AuditLogMapper mapper = session.getMapper(AuditLogMapper.class);

            Page<AuditLog> page = mapper.selectAuditPage(
                    new Page<>(1, 1),
                    "device.",
                    "operator",
                    null,
                    LocalDateTime.of(2026, 8, 12, 0, 0),
                    LocalDateTime.of(2026, 8, 14, 0, 0));

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getRecords().size());
            assertEquals(Long.valueOf(2L), page.getRecords().get(0).getId());
            assertEquals("TENANT",
                    page.getRecords().get(0).getScopeType());
            assertEquals("device.update",
                    page.getRecords().get(0).getAction());

            AuditLog detail = mapper.selectAuditById(1L);
            assertNotNull(detail);
            assertEquals(Long.valueOf(21L), detail.getTenantId());
            assertEquals("device.create", detail.getAction());
            assertEquals("node-1", detail.getTarget());
        }
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination =
                new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(false);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);

        MybatisSqlSessionFactoryBean factoryBean =
                new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(interceptor);
        factoryBean.setMapperLocations(
                new ClassPathResource(
                        "com/plagod/mapper/xml/AuditLogMapper.xml"));
        SqlSessionFactory result = factoryBean.getObject();
        assertNotNull(result);
        return result;
    }

    private void createSchemaAndRecords() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(
                "create table t_audit_log ("
                        + "id bigint auto_increment primary key,"
                        + "tenant_id bigint,"
                        + "scope_type varchar(16) not null,"
                        + "operator_id bigint,"
                        + "operator_name varchar(64),"
                        + "action varchar(64) not null,"
                        + "target varchar(255),"
                        + "detail varchar(1000),"
                        + "ip varchar(45),"
                        + "create_time timestamp not null)");
        jdbc.batchUpdate(
                "insert into t_audit_log "
                        + "(tenant_id, scope_type, operator_id, operator_name, "
                        + "action, target, detail, ip, create_time) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Arrays.asList(
                        new Object[]{
                                21L,
                                "TENANT",
                                7L,
                                "operator",
                                "device.create",
                                "node-1",
                                "{\"ok\":true}",
                                "127.0.0.1",
                                LocalDateTime.of(2026, 8, 12, 10, 0)
                        },
                        new Object[]{
                                21L,
                                "TENANT",
                                7L,
                                "operator",
                                "device.update",
                                "node-2",
                                "{\"ok\":true}",
                                "127.0.0.1",
                                LocalDateTime.of(2026, 8, 13, 10, 0)
                        }));
    }
}
