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
        Object outputObj = response.get("output");
        if (!(outputObj instanceof Map<?, ?>)) return Map.of();
        Map<String, Object> output = (Map<String, Object>) outputObj;

        Object messageObj = output.get("message");
        if (!(messageObj instanceof Map<?, ?>)) return Map.of();
        Map<String, Object> message = (Map<String, Object>) messageObj;

        Object contentObj = message.get("content");
        if (!(contentObj instanceof List<?>)) return Map.of();
        List<Object> content = (List<Object>) contentObj;

        for (Object blockObj : content) {
            if (!(blockObj instanceof Map<?, ?>)) continue;
            Map<String, Object> block = (Map<String, Object>) blockObj;

            Object tuObj = block.get("toolUse");
            if (!(tuObj instanceof Map<?, ?>)) continue;
            Map<String, Object> tu = (Map<String, Object>) tuObj;

            if (toolName.equals(tu.get("name"))) {
                Object inputObj = tu.get("input");
                if (inputObj instanceof Map<?, ?>) {
                    return (Map<String, Object>) inputObj;
                }
                return Map.of();
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
        Object dsObj = input.get("data_sheets");
        if (dsObj instanceof List<?>) {
            List<Object> arr = (List<Object>) dsObj;
            for (Object dsItem : arr) {
                if (!(dsItem instanceof Map<?, ?>)) continue;
                Map<String, Object> ds = (Map<String, Object>) dsItem;

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
        Object itemsObj = input.get("interfaces");
        List<InterfaceRecord> records = new ArrayList<>();
        if (!(itemsObj instanceof List<?>)) {
            return records;
        }
        List<Object> items = (List<Object>) itemsObj;
        int idx = 1;
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?>)) continue;
            Map<String, Object> item = (Map<String, Object>) itemObj;

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
