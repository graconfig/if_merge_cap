package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.util.List;

/**
 * POST /api/v1/analyze の入力 JSON。
 *
 * <p>GUI 端で読込・清洗済みの sheets を受け取る。
 */
public record AnalyzeRequestDto(
        String fileName,
        List<CleanedSheetDto> sheets,
        AnalyzeOptionsDto options
) {

    public record CleanedSheetDto(
            String name,
            List<String> headers,
            List<List<String>> rows
    ) {
    }

    public record AnalyzeOptionsDto(
            Integer phase1HeadRows,
            Integer maxChunkRows
    ) {
    }
}
