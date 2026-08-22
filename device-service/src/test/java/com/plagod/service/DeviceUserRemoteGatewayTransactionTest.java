package com.plagod.service;

import com.plagod.client.UserEntitlementClient;
import com.plagod.client.UserPolicyClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.user.EntitlementLeaseRequest;
import com.plagod.vo.user.EntitlementLeaseResult;
import com.plagod.vo.user.UserConnectionPolicyVO;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceUserRemoteGatewayTransactionTest {

    @Test
    void portalAndRenewalFeignCallsRunOutsideDeviceTransaction()
            throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);

        UserEntitlementClient entitlementClient =
                mock(UserEntitlementClient.class);
        UserPolicyClient policyClient = mock(UserPolicyClient.class);

        when(entitlementClient.acquireLease(any()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(new EntitlementLeaseResult());
                });
        when(policyClient.getConnectionPolicy(7L))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    return ApiResponse.success(new UserConnectionPolicyVO());
                });

        DeviceUserRemoteGateway target = new DeviceUserRemoteGateway();
        ReflectionTestUtils.setField(
                target, "userEntitlementClient", entitlementClient);
        ReflectionTestUtils.setField(
                target, "userPolicyClient", policyClient);

        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager,
                new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        DeviceUserRemoteGateway gateway =
                (DeviceUserRemoteGateway) proxyFactory.getProxy();

        new TransactionTemplate(transactionManager).execute(status -> {
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            gateway.acquireLease(new EntitlementLeaseRequest());
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            gateway.getConnectionPolicy(7L);
            assertTrue(TransactionSynchronizationManager
                    .isActualTransactionActive());
            return null;
        });
    }
}
