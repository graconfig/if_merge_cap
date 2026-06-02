package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.merge.model.FieldPair;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.model.SimilarityMode;
import com.handjapan.ifmerge.domain.merge.model.SimilarityPair;

import java.util.*;

/**
 * 相似度計算。
 * 原 Python：IFmerge/ebs_merger/similarity_calculator.py:10-137。
 *
 * <p>計算式：A = IF1 の fieldPairs, B = IF2 の fieldPairs, C = A ∩ B</p>
 * <ul>
 *   <li>MAX モード: |C| / min(|A|, |B|)</li>
 *   <li>AVG モード: (|C|/|A| + |C|/|B|) / 2</li>
 * </ul>
 */
public class SimilarityCalculator {

    /**
     * 二つの IF の相似度を計算する。
     */
    public double calculate(IFInfo if1, IFInfo if2, SimilarityMode mode) {
        Objects.requireNonNull(if1, "if1");
        Objects.requireNonNull(if2, "if2");
        Objects.requireNonNull(mode, "mode");

        Set<FieldPair> a = if1.fieldPairs();
        Set<FieldPair> b = if2.fieldPairs();
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<FieldPair> common = new HashSet<>(a);
        common.retainAll(b);
        int commonCount = common.size();
        int aCount = a.size();
        int bCount = b.size();

        double similarity = switch (mode) {
            case MAX -> {
                int min = Math.min(aCount, bCount);
                yield (min == 0) ? 0.0 : (double) commonCount / min;
            }
            case AVG -> ((double) commonCount / aCount + (double) commonCount / bCount) / 2.0;
        };

        return Math.max(0.0, Math.min(1.0, similarity));
    }

    /**
     * 閾値超のペアのみ返す（分組用）。
     */
    public List<SimilarityPair> buildMatrix(Collection<IFInfo> ifs,
                                            double threshold,
                                            SimilarityMode mode) {
        List<IFInfo> list = new ArrayList<>(ifs);
        list.sort(Comparator.comparing(IFInfo::ifName));
        List<SimilarityPair> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                double sim = calculate(list.get(i), list.get(j), mode);
                if (sim >= threshold) {
                    result.add(new SimilarityPair(list.get(i).ifName(), list.get(j).ifName(), sim));
                }
            }
        }
        return result;
    }

    /**
     * 全ペアを返す（マトリックス出力用）。
     */
    public List<SimilarityPair> buildFullMatrix(Collection<IFInfo> ifs, SimilarityMode mode) {
        List<IFInfo> list = new ArrayList<>(ifs);
        list.sort(Comparator.comparing(IFInfo::ifName));
        List<SimilarityPair> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                double sim = calculate(list.get(i), list.get(j), mode);
                result.add(new SimilarityPair(list.get(i).ifName(), list.get(j).ifName(), sim));
            }
        }
        return result;
    }
}
