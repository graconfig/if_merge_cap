package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import java.util.List;
import java.util.Map;

/**
 * SAP AI Core Converse API 用 Tool Schema 定义。
 *
 * <p>与原 Python {@code ai_analyzer.py:77-137 / 202-286} 一致的 JSON schema。
 */
public final class ToolSchemas {

    private ToolSchemas() {}

    // ============================================================
    // Phase 1: extract_doc_meta
    // ============================================================

    public static Map<String, Object> extractDocMeta() {
        return Map.of("toolSpec", Map.of(
                "name", "extract_doc_meta",
                "description", "設計書の固定情報・データシート・列構造を識別する",
                "inputSchema", Map.of("json", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "document_number", Map.of(
                                        "type", "string",
                                        "description", "文書管理番号"
                                ),
                                "if_name", Map.of(
                                        "type", "string",
                                        "description", "IF名"
                                ),
                                "data_sheets", Map.of(
                                        "type", "array",
                                        "description", "データ項目を含むシートのリスト",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "sheet_name", Map.of("type", "string", "description", "シート名"),
                                                        "data_start_row", Map.of("type", "integer", "description", "データ行開始行番号（0始まり）"),
                                                        "col_table_name", Map.of("type", "integer", "description", "EBSテーブル名（日本語）列番号（不明は-1）"),
                                                        "col_table_id", Map.of("type", "integer", "description", "EBSテーブルID列番号"),
                                                        "col_item_id", Map.of("type", "integer", "description", "項目ID列番号"),
                                                        "col_digit", Map.of("type", "integer", "description", "桁数列番号（不明は-1）")
                                                ),
                                                "required", List.of("sheet_name", "data_start_row", "col_table_id", "col_item_id")
                                        )
                                )
                        ),
                        "required", List.of("document_number", "if_name", "data_sheets")
                ))
        ));
    }

    // ============================================================
    // Phase 2: extract_interface_info
    // ============================================================

    public static Map<String, Object> extractInterfaceInfo() {
        return Map.of("toolSpec", Map.of(
                "name", "extract_interface_info",
                "description", "EBSテーブルの各項目を抽出する",
                "inputSchema", Map.of("json", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "interfaces", Map.of(
                                        "type", "array",
                                        "description", "抽出された項目リスト",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.ofEntries(
                                                        Map.entry("document_number", Map.of("type", "string", "description", "文書管理番号")),
                                                        Map.entry("if_name", Map.of("type", "string", "description", "IF名")),
                                                        Map.entry("ebs_table_name", Map.of("type", "string", "description", "EBSテーブル名")),
                                                        Map.entry("ebs_table_id", Map.of("type", "string", "description", "EBSテーブルID")),
                                                        Map.entry("item_id", Map.of("type", "string", "description", "項目ID")),
                                                        Map.entry("item_name", Map.of("type", "string", "description", "項目名（日本語）")),
                                                        Map.entry("item_description", Map.of("type", "string", "description", "項目説明")),
                                                        Map.entry("digit_count", Map.of("type", "string", "description", "桁数")),
                                                        Map.entry("data_type", Map.of("type", "string", "description", "データ型")),
                                                        Map.entry("digit_decimal", Map.of("type", "string", "description", "桁数(小数点以下)")),
                                                        Map.entry("dev_type", Map.of("type", "string", "description", "標準/追加開発")),
                                                        Map.entry("is_key", Map.of("type", "string", "description", "キー項目")),
                                                        Map.entry("required", Map.of("type", "string", "description", "必須/任意")),
                                                        Map.entry("remarks", Map.of("type", "string", "description", "備考"))
                                                ),
                                                "required", List.of("document_number", "if_name")
                                        )
                                )
                        ),
                        "required", List.of("interfaces")
                ))
        ));
    }

    // ============================================================
    // Merge: classify_interfaces  (原 Python ai_classifier.py:93-137)
    // ============================================================

    public static Map<String, Object> classifyInterfaces() {
        return Map.of("toolSpec", Map.of(
                "name", "classify_interfaces",
                "description", "SAPモジュールと業務シナリオに基づいてインターフェースを分類",
                "inputSchema", Map.of("json", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "categories", Map.of(
                                        "type", "array",
                                        "description", "分類結果のリスト",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "module", Map.of(
                                                                "type", "string",
                                                                "description", "SAPモジュールコード（単一モジュールのみ。例：SD、MM、PP、WM、FI、CO、HR）"
                                                        ),
                                                        "scenario", Map.of(
                                                                "type", "string",
                                                                "description", "業務シナリオ（例：受注処理、在庫管理）"
                                                        ),
                                                        "category_description", Map.of(
                                                                "type", "string",
                                                                "description", "分類の説明"
                                                        ),
                                                        "if_names", Map.of(
                                                                "type", "array",
                                                                "items", Map.of("type", "string"),
                                                                "description", "この分類に属する IF 名"
                                                        )
                                                ),
                                                "required", List.of("module", "scenario", "category_description", "if_names")
                                        )
                                )
                        ),
                        "required", List.of("categories")
                ))
        ));
    }

    // ============================================================
    // Merge: generate_all_if_info  (原 Python ai_generator.py:303-341)
    // ============================================================

    public static Map<String, Object> generateAllIfInfo() {
        return Map.of("toolSpec", Map.of(
                "name", "generate_all_if_info",
                "description", "すべてのインターフェースの概要と代表項目名を生成",
                "inputSchema", Map.of("json", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "interfaces", Map.of(
                                        "type", "array",
                                        "description", "すべての IF 情報リスト",
                                        "items", Map.of(
                                                "type", "object",
                                                "properties", Map.of(
                                                        "if_name", Map.of("type", "string", "description", "IF 名"),
                                                        "summary", Map.of("type", "string", "description", "概要（日本語、30-50 文字）"),
                                                        "representative_item", Map.of("type", "string", "description", "代表項目名（カンマ区切り）")
                                                ),
                                                "required", List.of("if_name", "summary", "representative_item")
                                        )
                                )
                        ),
                        "required", List.of("interfaces")
                ))
        ));
    }

    // ============================================================
    // Merge: generate_merged_name  (原 Python ai_generator.py:427-446)
    // ============================================================

    public static Map<String, Object> generateMergedName() {
        return Map.of("toolSpec", Map.of(
                "name", "generate_merged_name",
                "description", "マージ後のインターフェース名を生成",
                "inputSchema", Map.of("json", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "merged_name", Map.of(
                                        "type", "string",
                                        "description", "マージ後のインターフェース名（日本語、20-40 文字）"
                                )
                        ),
                        "required", List.of("merged_name")
                ))
        ));
    }
}
