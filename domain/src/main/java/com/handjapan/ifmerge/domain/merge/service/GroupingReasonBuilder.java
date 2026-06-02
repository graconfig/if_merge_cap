package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.merge.model.SimilarityPair;

import java.util.ArrayList;
import java.util.List;

/**
 * グルーピングの根拠説明文を生成する。
 * 原 Python：IFmerge/ebs_merger/result_generator.py:195-226。
 */
public class GroupingReasonBuilder {

    public String build(String currentIfName,
                        List<String> groupMembers,
                        List<SimilarityPair> similarPairs) {
        if (groupMembers.size() == 1) {
            return "独立IF、マージ不要";
        }

        List<String> reasons = new ArrayList<>();
        for (SimilarityPair pair : similarPairs) {
            String other = null;
            if (pair.if1Name().equals(currentIfName) && groupMembers.contains(pair.if2Name())) {
                other = pair.if2Name();
            } else if (pair.if2Name().equals(currentIfName) && groupMembers.contains(pair.if1Name())) {
                other = pair.if1Name();
            }
            if (other != null) {
                reasons.add(String.format("「%s」と類似度%.1f%%", other, pair.similarity() * 100.0));
            }
        }

        if (reasons.isEmpty()) {
            return "推移性によるマージ";
        }
        return String.join("、", reasons);
    }
}
