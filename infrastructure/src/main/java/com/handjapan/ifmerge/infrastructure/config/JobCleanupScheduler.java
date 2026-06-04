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
    private final Duration runningTimeout;

    public JobCleanupScheduler(JobStore jobStore,
                               @Value("${ifmerge.job.ttl:15m}") Duration ttl,
                               @Value("${ifmerge.job.runningTimeout:10m}") Duration runningTimeout) {
        this.jobStore = jobStore;
        this.ttl = ttl;
        this.runningTimeout = runningTimeout;
    }

    @Scheduled(fixedDelayString = "${ifmerge.job.cleanupInterval:600000}")
    public void cleanup() {
        // ① 先にハングした RUNNING/PENDING Job を FAILED 化（completedAt が入る）
        int failed = jobStore.failStaleJobs(runningTimeout);
        if (failed > 0) {
            log.warn("JobCleanup: {} 件のハング Job を FAILED 化 (runningTimeout={})",
                    failed, runningTimeout);
        }
        // ② TTL 経過の完了済み Job を evict
        int evicted = jobStore.evictExpired(ttl);
        if (evicted > 0) {
            log.info("JobCleanup: evicted {} expired jobs (ttl={})", evicted, ttl);
        }
    }
}
