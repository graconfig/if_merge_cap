package com.handjapan.ifmerge.application.job;

import java.time.Instant;
import java.util.UUID;

/**
 * 非同期 Job のメタデータ + 結果。
 * メモリ内のみで保持され、TTL（既定 15 分）を超えると evict される。
 *
 * @param id          Job 一意 ID
 * @param type        ANALYSIS / MERGE
 * @param status      PENDING / RUNNING / SUCCEEDED / FAILED
 * @param progress    0-100
 * @param phase       現在フェーズ（PHASE1 / PHASE2_CHUNK_3 / ...）
 * @param createdAt   作成時刻
 * @param startedAt   開始時刻
 * @param completedAt 完了時刻
 * @param result      AnalysisResult / MergeResult / null
 * @param error       失敗時の情報
 */
public record Job(
        UUID id,
        JobType type,
        JobStatus status,
        int progress,
        String phase,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Object result,
        ErrorInfo error
) {

    public static Job createPending(JobType type) {
        return new Job(
                UUID.randomUUID(),
                type,
                JobStatus.PENDING,
                0,
                "PENDING",
                Instant.now(),
                null,
                null,
                null,
                null
        );
    }

    public Job withStatus(JobStatus newStatus, String newPhase) {
        Instant started = (newStatus == JobStatus.RUNNING && startedAt == null) ? Instant.now() : startedAt;
        Instant completed = (newStatus == JobStatus.SUCCEEDED || newStatus == JobStatus.FAILED) ? Instant.now() : completedAt;
        return new Job(id, type, newStatus, progress, newPhase, createdAt, started, completed, result, error);
    }

    public Job withProgress(int newProgress, String newPhase) {
        return new Job(id, type, status, newProgress, newPhase, createdAt, startedAt, completedAt, result, error);
    }

    public Job withResult(Object newResult) {
        return new Job(id, type, JobStatus.SUCCEEDED, 100, "COMPLETED", createdAt, startedAt, Instant.now(), newResult, error);
    }

    public Job withError(ErrorInfo newError) {
        return new Job(id, type, JobStatus.FAILED, progress, phase, createdAt, startedAt, Instant.now(), result, newError);
    }

    public record ErrorInfo(String code, String message, Instant occurredAt) {
        public static ErrorInfo of(String code, String message) {
            return new ErrorInfo(code, message, Instant.now());
        }
    }
}
