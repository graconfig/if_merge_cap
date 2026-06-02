package com.handjapan.ifmerge.domain.analysis.model;

import java.util.List;

/**
 * 解析全体の結果。
 * 1 ファイル = 1 AnalysisResult（複数 sheet の records を含む）。
 */
public record AnalysisResult(
        String documentNumber,
        String ifName,
        List<DataSheetInfo> dataSheets,
        List<InterfaceRecord> records
) {

    public record DataSheetInfo(
            String sheetName,
            int dataStartRow,
            int recordCount
    ) {
    }
}
