package com.handjapan.ifmerge.domain.merge.model;

/**
 * 合并後の出力用フィールド（重複排除済み）。
 *
 * <p>相似度計算の基本単位 {@link FieldPair} は (tableId, itemId) のみ保持するが、
 * テンプレート（IF_Template.xlsm）出力では「テーブル名 (テーブルID)」「項目名」も
 * 必要なため、本型でテーブル名・項目名も保持する。
 *
 * <p>原 Python：{@code IFmerge/ebs_merger/template_filler.py:131-249} の
 * merged_data 各行（EBSテーブル名 / EBSテーブルID / 項目ID / 項目名）に対応。
 */
public record MergedField(
        String tableId,
        String tableName,
        String itemId,
        String itemName
) {
}
