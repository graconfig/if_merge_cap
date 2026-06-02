package com.handjapan.ifmerge.infrastructure.config;

import com.handjapan.ifmerge.application.job.JobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 期限切れ Job のクリーンアップ（既定 10 分毎）。
 */
@Component
public class JobCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobCleanupScheduler.class);

    private final JobStore jobStore;
    private final Duration ttl;

    public JobCleanupScheduler(JobStore jobStore,
                               @Value("${ifmerge.job.ttl:15m}") Duration ttl) {
        this.jobStore = jobStore;
        this.ttl = ttl;
    }

    @Scheduled(fixedDelayString = "${ifmerge.job.cleanupInterval:600000}")
    public void cleanup() {
        int evicted = jobStore.evictExpired(ttl);
        if (evicted > 0) {
            log.info("JobCleanup: evicted {} expired jobs (ttl={})", evicted, ttl);
        }
    }
}
