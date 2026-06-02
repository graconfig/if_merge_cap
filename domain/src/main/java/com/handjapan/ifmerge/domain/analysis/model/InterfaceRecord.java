package com.handjapan.ifmerge.domain.analysis.model;

/**
 * Interface 設計書から抽出された 1 行分のレコード。
 * 原 Python InterfaceRecord に対応（IFmerge_1/analyzer/parser.py:17-34）。
 */
public record InterfaceRecord(
        int no,
        String documentNumber,
        String ifName,
        String ebsTableName,
        String ebsTableId,
        String itemId,
        String itemName,
        String digitCount,
        String itemDescription,
        String dataType,
        String digitDecimal,
        String devType,
        String isKey,
        String required,
        String remarks
) {
}
