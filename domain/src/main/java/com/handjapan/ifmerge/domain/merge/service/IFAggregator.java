package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.FieldPair;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;

import java.util.*;

/**
 * GUI から渡された InterfaceRecord リストを IF 単位に集約し、IFInfo マップを構築する。
 * 原 Python：IFmerge/ebs_merger/if_grouper.py:24-91 の groupby('IF名') 相当。
 */
public class IFAggregator {

    public Map<String, IFInfo> aggregate(List<InterfaceRecord> records) {
        Map<String, List<InterfaceRecord>> grouped = new LinkedHashMap<>();
        for (InterfaceRecord r : records) {
            grouped.computeIfAbsent(r.ifName(), k -> new ArrayList<>()).add(r);
        }

        Map<String, IFInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<InterfaceRecord>> entry : grouped.entrySet()) {
            String ifName = entry.getKey();
            List<InterfaceRecord> ifRecords = entry.getValue();

            Set<FieldPair> fieldPairs = new LinkedHashSet<>();
            for (InterfaceRecord r : ifRecords) {
                String tableId = r.ebsTableId();
                String itemId = r.itemId();
                if (tableId == null || tableId.isBlank()) continue;
                if (itemId == null || itemId.isBlank()) continue;
                fieldPairs.add(new FieldPair(tableId.strip(), itemId.strip()));
            }

            String docNumber = ifRecords.isEmpty() ? "" : ifRecords.get(0).documentNumber();
            String representative = ifRecords.stream()
                    .map(InterfaceRecord::itemName)
                    .filter(name -> name != null && !name.isBlank())
                    .findFirst()
                    .orElse("");

            result.put(ifName, new IFInfo(ifName, docNumber, fieldPairs, representative));
        }
        return result;
    }
}
