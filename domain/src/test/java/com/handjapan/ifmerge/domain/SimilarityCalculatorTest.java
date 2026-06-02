package com.handjapan.ifmerge.domain;

import com.handjapan.ifmerge.domain.merge.model.FieldPair;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.model.SimilarityMode;
import com.handjapan.ifmerge.domain.merge.service.SimilarityCalculator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityCalculatorTest {

    private final SimilarityCalculator calc = new SimilarityCalculator();

    @Test
    void maxMode_identicalSets_returnsOne() {
        IFInfo if1 = newInfo("IF_A", "TABLE1", "F1", "F2", "F3");
        IFInfo if2 = newInfo("IF_B", "TABLE1", "F1", "F2", "F3");

        assertThat(calc.calculate(if1, if2, SimilarityMode.MAX)).isEqualTo(1.0);
    }

    @Test
    void maxMode_disjointSets_returnsZero() {
        IFInfo if1 = newInfo("IF_A", "TABLE1", "F1", "F2");
        IFInfo if2 = newInfo("IF_B", "TABLE2", "G1", "G2");

        assertThat(calc.calculate(if1, if2, SimilarityMode.MAX)).isEqualTo(0.0);
    }

    @Test
    void maxMode_subsetSets_returnsOne() {
        // 原 Python: |C| / min(|A|,|B|) — subset 関係なら必ず 1.0
        IFInfo if1 = newInfo("IF_A", "TABLE1", "F1", "F2");                  // 2 件
        IFInfo if2 = newInfo("IF_B", "TABLE1", "F1", "F2", "F3", "F4");      // 4 件、IF_A は IF_B の subset

        assertThat(calc.calculate(if1, if2, SimilarityMode.MAX)).isEqualTo(1.0);
    }

    @Test
    void avgMode_subsetSets_returnsAverageOfDirections() {
        IFInfo if1 = newInfo("IF_A", "TABLE1", "F1", "F2");                  // 2 件
        IFInfo if2 = newInfo("IF_B", "TABLE1", "F1", "F2", "F3", "F4");      // 4 件
        // C = 2, |A| = 2, |B| = 4 → (2/2 + 2/4) / 2 = 0.75
        assertThat(calc.calculate(if1, if2, SimilarityMode.AVG)).isEqualTo(0.75);
    }

    @Test
    void emptySet_returnsZero() {
        IFInfo if1 = newInfo("IF_A", "TABLE1");
        IFInfo if2 = newInfo("IF_B", "TABLE1", "F1");

        assertThat(calc.calculate(if1, if2, SimilarityMode.MAX)).isEqualTo(0.0);
    }

    private IFInfo newInfo(String name, String tableId, String... itemIds) {
        Set<FieldPair> pairs = new LinkedHashSet<>();
        for (String itemId : itemIds) {
            pairs.add(new FieldPair(tableId, itemId));
        }
        return new IFInfo(name, "BDN-001", pairs, itemIds.length > 0 ? itemIds[0] : "");
    }
}
