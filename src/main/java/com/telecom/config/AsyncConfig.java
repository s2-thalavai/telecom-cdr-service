package com.telecom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bounded on purpose: when workers and queue are full, submit throws TaskRejectedException,
     * which the API turns into 503 + Retry-After (backpressure instead of unbounded disk/memory growth).
     */
    @Bean(name = "ingestionExecutor")
    public ThreadPoolTaskExecutor ingestionExecutor(PayloadProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.asyncWorkers());
        executor.setMaxPoolSize(properties.asyncWorkers());
        executor.setQueueCapacity(properties.asyncQueueCapacity());
        executor.setThreadNamePrefix("cdr-ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
