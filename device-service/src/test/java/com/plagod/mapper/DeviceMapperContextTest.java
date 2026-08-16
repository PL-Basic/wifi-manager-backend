package com.plagod.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.plagod.DeviceApplication;
import com.plagod.mqtt.MqttEventSubscriber;
import com.plagod.testkit.MapperContextAssertions;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

@SpringBootTest(
        classes = DeviceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.task.scheduling.enabled=false",
                "wifi.audit.enabled=false",
                "wifi.security.enabled=false",
                "wifi.internal.token=device-mapper-context-test-token",
                "wifi.command.scheduler-enabled=false",
                "wifi.portal.lease-scheduler-enabled=false",
                "wifi.device.heartbeat-scheduler-enabled=false"
        })
class DeviceMapperContextTest {

    @MockBean
    private DataSource dataSource;

    @MockBean
    private MqttEventSubscriber mqttEventSubscriber;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MybatisPlusProperties mybatisPlusProperties;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void realDeviceContextRegistersOnlyOwnedMappersAndLoadsXml()
            throws Exception {
        MapperContextAssertions.assertExactApplicationMapperBeans(
                applicationContext,
                sqlSessionFactory,
                ClientAccessGuardMapper.class,
                ClientSignalMapper.class,
                DeviceCommandRecordMapper.class,
                DeviceWifiConfigRecordMapper.class,
                Esp32NodeMapper.class,
                MacBlacklistMapper.class,
                SessionRecordMapper.class,
                SessionUserGuardMapper.class,
                TrafficLogMapper.class);
        MapperContextAssertions.assertPackagedMapperXmlStatementsLoaded(
                mybatisPlusProperties.resolveMapperLocations(),
                "classpath*:com/plagod/mapper/xml/*Mapper.xml",
                sqlSessionFactory,
                ClientSignalMapper.class.getName()
                        + ".selectTrendBuckets",
                Esp32NodeMapper.class.getName()
                        + ".selectByDeviceCodeIncludeDeleted",
                Esp32NodeMapper.class.getName()
                        + ".selectByDeviceCodeAndTenantIncludeDeleted",
                Esp32NodeMapper.class.getName()
                        + ".selectByNodeIdIncludeDeleted",
                Esp32NodeMapper.class.getName()
                        + ".selectByNodeIdAndTenantIncludeDeleted",
                Esp32NodeMapper.class.getName()
                        + ".restoreRetiredById",
                Esp32NodeMapper.class.getName()
                        + ".markTimedOutNodesOffline",
                TrafficLogMapper.class.getName()
                        + ".insertIgnore",
                TrafficLogMapper.class.getName()
                        + ".selectAnalyticsSummary",
                TrafficLogMapper.class.getName()
                        + ".selectAnalyticsTrend",
                TrafficLogMapper.class.getName()
                        + ".selectAnalyticsRanking");
    }
}
