package com.handjapan.ifmerge.domain.merge.model;

import java.util.List;

/**
 * 合并組（Union-Find の結果）。
 * 原 Python：cli.py:225-228 のグルーピングID単位。
 *
 * <p>メンバーは {@link MergeMember} として IF 単位のメタ情報（文書管理番号・
 * 項目数・IF概要・代表項目名・根拠）を保持する。
 */
public record MergeGroup(
        String groupingId,
        String module,
        String scenario,
        List<MergeMember> members,
        String mergedIfName,
        List<MergedField> mergedFields
) {

    public boolean isMergeRequired() {
        return members.size() > 1;
    }

    public int mergedFieldCount() {
        return mergedFields.size();
    }

    /** メンバー IF 名のリスト（既存呼び出し互換用）。 */
    public List<String> memberIfNames() {
        return members.stream().map(MergeMember::ifName).toList();
    }
}
