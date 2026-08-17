package com.plagod.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class SupportReviewSchedulingConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock supportClock() {
        return Clock.system(StableUnits.ASIA_SHANGHAI);
    }
}
