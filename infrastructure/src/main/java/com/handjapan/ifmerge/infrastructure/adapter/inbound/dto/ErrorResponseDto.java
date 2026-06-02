package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.time.Instant;

/**
 * HTTP エラーレスポンス（4xx / 5xx）。
 */
public record ErrorResponseDto(
        String code,
        String message,
        Instant occurredAt
) {
    public static ErrorResponseDto of(String code, String message) {
        return new ErrorResponseDto(code, message, Instant.now());
    }
}
