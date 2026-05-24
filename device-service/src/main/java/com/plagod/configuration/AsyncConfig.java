package com.plagod.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * 给监控规则评估用的专用线程池。命名 monitorEvalExecutor，被 TrafficRuleEvaluator
     * 的 @Async 显式引用，跟 spring 默认线程池 / 其他业务 @Async 隔离开。
     *
     * MQTT 接收线程拿到 traffic event 后只负责落库 t_traffic_log / 累加 session，
     * 调评估 + 动作派发立刻丢进这个池里 fire-and-forget，不阻塞 MQTT 消费速度。
     */
    @Bean("monitorEvalExecutor")
    public Executor monitorEvalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("monitor-eval-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
