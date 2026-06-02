package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Job の JSON 表現。
 * すべての /analyze と /merge の即時レスポンス + getJob の結果。
 */
public record JobDto(
        UUID id,
        String type,        // ANALYSIS / MERGE
        String status,      // PENDING / RUNNING / SUCCEEDED / FAILED
        Integer progress,
        String phase,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        ErrorInfoDto error
) {

    public record ErrorInfoDto(
            String code,
            String message,
            Instant occurredAt
    ) {
    }
}
