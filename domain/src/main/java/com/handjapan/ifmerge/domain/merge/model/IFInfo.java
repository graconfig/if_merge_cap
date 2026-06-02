package com.handjapan.ifmerge.domain.merge.model;

import java.util.Set;

/**
 * IF のメタ情報と特徴。
 * 原 Python IFInfo に対応（IFmerge/ebs_merger/if_grouper.py:11-18）。
 */
public record IFInfo(
        String ifName,
        String docNumber,
        Set<FieldPair> fieldPairs,
        String representativeItem
) {
    public int itemCount() {
        return fieldPairs.size();
    }
}
