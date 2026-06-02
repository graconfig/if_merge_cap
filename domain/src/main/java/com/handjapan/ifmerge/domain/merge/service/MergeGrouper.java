package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.merge.model.SimilarityPair;

import java.util.*;

/**
 * 相似度ペアから合并組を生成する。
 * 原 Python：IFmerge/ebs_merger/merge_grouper.py:75-133。
 */
public class MergeGrouper {

    /**
     * 与えられた IF 名集合と相似度ペアから、{代表元素 → メンバー} の組を生成する。
     */
    public Map<String, List<String>> groupSimilarIFs(Collection<String> ifNames,
                                                     List<SimilarityPair> similarPairs) {
        UnionFind uf = new UnionFind(ifNames);
        for (SimilarityPair pair : similarPairs) {
            uf.union(pair.if1Name(), pair.if2Name());
        }
        return uf.getGroups();
    }
}
