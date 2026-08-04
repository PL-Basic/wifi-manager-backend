package com.plagod.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@Slf4j
public class TenantMembershipOutboxAsyncConfiguration {

    @Bean(name = "tenantMembershipOutboxExecutor")
    public Executor tenantMembershipOutboxExecutor(
            @Value("${wifi.tenant-membership-outbox.executor.core-size:1}") int coreSize,
            @Value("${wifi.tenant-membership-outbox.executor.max-size:2}") int maxSize,
            @Value("${wifi.tenant-membership-outbox.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("tenant-outbox-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("默认租户成员首次投递队列已满，将由数据库Outbox扫描补偿：active={}，queued={}",
                        pool.getActiveCount(), pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
