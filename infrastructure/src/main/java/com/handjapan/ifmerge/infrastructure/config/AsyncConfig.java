package com.handjapan.ifmerge.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 非同期 Job 用のスレッドプール。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private final IfmergeProperties props;

    public AsyncConfig(IfmergeProperties props) {
        this.props = props;
    }

    @Bean(name = "jobExecutor")
    public TaskExecutor jobExecutor() {
        // 同時実行 Job 上限は ifmerge.job.maxConcurrent から。
        int maxConcurrent = props.job().maxConcurrentOrDefault();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(Math.min(5, maxConcurrent));
        exec.setMaxPoolSize(maxConcurrent);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("job-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
