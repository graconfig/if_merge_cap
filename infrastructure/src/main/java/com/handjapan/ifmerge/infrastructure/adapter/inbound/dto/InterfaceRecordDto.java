package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

/**
 * InterfaceRecord の JSON 表現。
 * /analyze の出力にも、/merge の入力にも使われる。
 *
 * <p>原 Python InterfaceRecord（parser.py:17-34）+ no（序号）の 15 字段。
 */
public record InterfaceRecordDto(
        Integer no,
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
