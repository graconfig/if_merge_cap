package com.handjapan.ifmerge.application.job;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Job 永続化ポート。
 * デフォルト実装は ConcurrentHashMap ベースのインメモリ（infrastructure 層）。
 * 将来 Redis 等に切替可能。
 */
public interface JobStore {

    void save(Job job);

    Optional<Job> findById(UUID id);

    /**
     * 原子的に更新する。findById ＋ save の競合を避ける。
     */
    void update(UUID id, UnaryOperator<Job> updater);

    /**
     * TTL 経過の Job を evict する。
     *
     * @return evict 件数
     */
    int evictExpired(Duration ttl);

    /**
     * runningTimeout を超えてなお PENDING / RUNNING のままの Job を
     * 強制的に FAILED（code=JOB_TIMEOUT）にする。完了時刻が入るため、
     * 以降は {@link #evictExpired(Duration)} の対象になる（メモリリーク防止）。
     *
     * @return FAILED に変更した件数
     */
    int failStaleJobs(Duration runningTimeout);
}
