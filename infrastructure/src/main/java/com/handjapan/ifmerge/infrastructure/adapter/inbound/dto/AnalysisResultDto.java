package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/analyses/{id}/result のレスポンス。
 */
public record AnalysisResultDto(
        UUID jobId,
        String status,
        String fileName,
        AnalysisResultBody result
) {

    public record AnalysisResultBody(
            String documentNumber,
            String ifName,
            List<DataSheetInfoDto> dataSheets,
            List<InterfaceRecordDto> records,
            Integer totalRecords
    ) {
    }

    public record DataSheetInfoDto(
            String sheetName,
            Integer dataStartRow,
            Integer recordCount
    ) {
    }
}
