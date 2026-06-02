package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converse API の {@code tool_use} 応答を domain object に変換する。
 *
 * <p>SAP AI Core / Anthropic Converse API 応答构造：
 * <pre>
 * {
 *   "output": {
 *     "message": {
 *       "content": [
 *         { "toolUse": { "name": "extract_doc_meta", "input": { ... } } }
 *       ]
 *     }
 *   },
 *   "usage": { "inputTokens": ..., "outputTokens": ... }
 * }
 * </pre>
 */
public final class ResponseParser {

    private ResponseParser() {}

    /** 指定 tool 名の tool_use.input を抽出。複数あれば最初の 1 つ。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractToolInput(Map<String, Object> response, String toolName) {
        Map<String, Object> output = (Map<String, Object>) response.getOrDefault("output", Map.of());
        Map<String, Object> message = (Map<String, Object>) output.getOrDefault("message", Map.of());
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.getOrDefault("content", List.of());
        for (Map<String, Object> block : content) {
            Object tu = block.get("toolUse");
            if (tu instanceof Map<?, ?> m) {
                if (toolName.equals(m.get("name"))) {
                    return (Map<String, Object>) m.getOrDefault("input", Map.of());
                }
            }
        }
        return Map.of();
    }

    // =====================================================================
    // Phase 1: extract_doc_meta
    // =====================================================================

    @SuppressWarnings("unchecked")
    public static AnalysisAiGateway.Phase1Result toPhase1Result(Map<String, Object> response) {
        Map<String, Object> input = extractToolInput(response, "extract_doc_meta");

        String docNumber = stringOrEmpty(input.get("document_number"));
        String ifName = stringOrEmpty(input.get("if_name"));

        List<AnalysisAiGateway.DataSheetMeta> dataSheets = new ArrayList<>();
        List<Map<String, Object>> arr = (List<Map<String, Object>>) input.getOrDefault("data_sheets", List.of());
        for (Map<String, Object> ds : arr) {
            String sheetName = stringOrEmpty(ds.get("sheet_name"));
            int dataStartRow = intOrDefault(ds.get("data_start_row"), 0);
            int colTableName = intOrDefault(ds.get("col_table_name"), -1);
            int colTableId = intOrDefault(ds.get("col_table_id"), -1);
            int colItemId = intOrDefault(ds.get("col_item_id"), -1);
            int colDigit = intOrDefault(ds.get("col_digit"), -1);

            dataSheets.add(new AnalysisAiGateway.DataSheetMeta(
                    sheetName,
                    dataStartRow,
                    new AnalysisAiGateway.ColumnMapping(colTableName, colTableId, colItemId, colDigit)
            ));
        }

        return new AnalysisAiGateway.Phase1Result(docNumber, ifName, dataSheets);
    }

    // =====================================================================
    // Phase 2: extract_interface_info
    // =====================================================================

    @SuppressWarnings("unchecked")
    public static List<InterfaceRecord> toInterfaceRecords(Map<String, Object> response,
                                                           String fallbackDocNumber,
                                                           String fallbackIfName) {
        Map<String, Object> input = extractToolInput(response, "extract_interface_info");
        List<Map<String, Object>> items = (List<Map<String, Object>>) input.getOrDefault("interfaces", List.of());
        List<InterfaceRecord> records = new ArrayList<>(items.size());
        int idx = 1;
        for (Map<String, Object> item : items) {
            String docNum = stringOrEmpty(item.get("document_number"));
            if (docNum.isEmpty()) docNum = fallbackDocNumber;
            String ifName = stringOrEmpty(item.get("if_name"));
            if (ifName.isEmpty()) ifName = fallbackIfName;

            records.add(new InterfaceRecord(
                    idx++,
                    docNum,
                    ifName,
                    stringOrEmpty(item.get("ebs_table_name")),
                    stringOrEmpty(item.get("ebs_table_id")),
                    stringOrEmpty(item.get("item_id")),
                    stringOrEmpty(item.get("item_name")),
                    stringOrEmpty(item.get("digit_count")),
                    stringOrEmpty(item.get("item_description")),
                    stringOrEmpty(item.get("data_type")),
                    stringOrEmpty(item.get("digit_decimal")),
                    stringOrEmpty(item.get("dev_type")),
                    stringOrEmpty(item.get("is_key")),
                    stringOrEmpty(item.get("required")),
                    stringOrEmpty(item.get("remarks"))
            ));
        }
        return records;
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static String stringOrEmpty(Object v) {
        if (v == null) return "";
        return String.valueOf(v);
    }

    private static int intOrDefault(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) { return def; }
        }
        return def;
    }
}
