package com.handjapan.ifmerge.infrastructure.adapter.outbound.storage;

import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * メモリ内 Job 永続化（ConcurrentHashMap）。
 * 単一インスタンス前提。複数インスタンス対応は将来 Redis 等への切替で対応。
 */
@Component
public class InMemoryJobStore implements JobStore {

    private final Map<UUID, Job> store = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        store.put(job.id(), job);
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void update(UUID id, UnaryOperator<Job> updater) {
        store.compute(id, (k, v) -> v == null ? null : updater.apply(v));
    }

    @Override
    public int evictExpired(Duration ttl) {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.values().removeIf(j ->
                j.completedAt() != null && j.completedAt().isBefore(cutoff)
        );
        return before - store.size();
    }
}
