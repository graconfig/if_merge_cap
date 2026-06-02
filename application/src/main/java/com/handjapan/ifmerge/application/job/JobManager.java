package com.handjapan.ifmerge.application.job;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Job のライフサイクル管理。
 * UseCase はこのサービス経由で Job 状態を更新する（直接 JobStore を叩かない）。
 */
@Service
public class JobManager {

    private final JobStore store;

    public JobManager(JobStore store) {
        this.store = store;
    }

    public Job create(JobType type) {
        Job job = Job.createPending(type);
        store.save(job);
        return job;
    }

    public void markRunning(UUID id, String phase) {
        store.update(id, j -> j.withStatus(JobStatus.RUNNING, phase));
    }

    public void updateProgress(UUID id, int progress, String phase) {
        store.update(id, j -> j.withProgress(progress, phase));
    }

    public void markSucceeded(UUID id, Object result) {
        store.update(id, j -> j.withResult(result));
    }

    public void markFailed(UUID id, String code, String message) {
        store.update(id, j -> j.withError(Job.ErrorInfo.of(code, message)));
    }

    public Optional<Job> findById(UUID id) {
        return store.findById(id);
    }
}
