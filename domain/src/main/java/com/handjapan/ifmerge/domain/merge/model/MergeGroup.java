package com.handjapan.ifmerge.domain.merge.model;

import java.util.List;
import java.util.Set;

/**
 * 合并組（Union-Find の結果）。
 * 原 Python：cli.py:225-228 のグルーピングID単位。
 */
public record MergeGroup(
        String groupingId,
        String module,
        String scenario,
        List<String> memberIfNames,
        String mergedIfName,
        String groupingReason,
        Set<FieldPair> mergedFields
) {

    public boolean isMergeRequired() {
        return memberIfNames.size() > 1;
    }

    public int mergedFieldCount() {
        return mergedFields.size();
    }
}
