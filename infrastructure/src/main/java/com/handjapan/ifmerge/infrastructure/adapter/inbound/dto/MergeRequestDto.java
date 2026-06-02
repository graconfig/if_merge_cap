package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /api/v1/merge の入力 JSON。
 *
 * <p>GUI 端で複数 /analyze の出力を集約した records を直接受け取る（stateless）。
 */
public record MergeRequestDto(
        List<InterfaceRecordDto> records,
        MergeOptionsDto options
) {

    public record MergeOptionsDto(
            BigDecimal threshold,
            String mode      // "max" or "avg"
    ) {
    }
}
