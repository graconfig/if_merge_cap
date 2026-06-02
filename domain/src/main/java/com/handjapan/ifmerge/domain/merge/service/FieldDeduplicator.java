package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.FieldPair;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 合并組のメンバー全 IF の字段を統合し、(EBSテーブルID, 項目ID) で重複排除する。
 * 原 Python：IFmerge/ebs_merger/template_filler.py:131-148。
 */
public class FieldDeduplicator {

    public Set<FieldPair> dedupe(List<InterfaceRecord> records) {
        Set<FieldPair> result = new LinkedHashSet<>();
        for (InterfaceRecord r : records) {
            String tableId = r.ebsTableId();
            String itemId = r.itemId();
            if (tableId == null || tableId.isBlank()) continue;
            if (itemId == null || itemId.isBlank()) continue;
            result.add(new FieldPair(tableId.strip(), itemId.strip()));
        }
        return result;
    }
}
