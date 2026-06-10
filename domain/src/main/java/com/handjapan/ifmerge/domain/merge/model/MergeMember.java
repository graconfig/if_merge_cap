package com.handjapan.ifmerge.domain.merge.model;

import java.util.List;

/**
 * 合并組のメンバー1件（IF 単位）のメタ情報。
 *
 * <p>原 Python {@code IFmerge/ebs_merger/result_generator.py:134-147} の
 * {@code OutputRow} のうち IF 単位フィールド（文書管理番号・項目数・IF概要・
 * 代表項目名・グルーピングの根拠）に対応する。
 */
public record MergeMember(
        String ifName,
        String docNumber,
        int itemCount,
        String ifSummary,
        List<String> representativeItems,
        String groupingReason
) {
}
