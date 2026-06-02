package com.handjapan.ifmerge.domain.merge.model;

/**
 * (EBSテーブルID, 項目ID) のペア。
 * IF 間の相似度計算の基本単位。
 * 原 Python：tuple(table_id, item_id)（IFmerge/ebs_merger/if_grouper.py:74-88）。
 */
public record FieldPair(String tableId, String itemId) {

    public FieldPair {
        if (tableId == null || tableId.isBlank()) {
            throw new IllegalArgumentException("tableId must not be blank");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
    }
}
