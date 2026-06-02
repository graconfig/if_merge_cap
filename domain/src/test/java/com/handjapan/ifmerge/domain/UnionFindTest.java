package com.handjapan.ifmerge.domain;

import com.handjapan.ifmerge.domain.merge.service.UnionFind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnionFindTest {

    @Test
    void initiallyEachElementIsItsOwnGroup() {
        UnionFind uf = new UnionFind(Arrays.asList("A", "B", "C"));
        Map<String, List<String>> groups = uf.getGroups();

        assertThat(groups).hasSize(3);
    }

    @Test
    void unionMergesGroups() {
        UnionFind uf = new UnionFind(Arrays.asList("A", "B", "C", "D"));
        uf.union("A", "B");
        uf.union("C", "D");

        Map<String, List<String>> groups = uf.getGroups();
        assertThat(groups).hasSize(2);
    }

    @Test
    void transitiveUnion_AllInSameGroup() {
        // A-B, B-C → A,B,C 全在一组（推移性）
        UnionFind uf = new UnionFind(Arrays.asList("A", "B", "C", "D"));
        uf.union("A", "B");
        uf.union("B", "C");

        Map<String, List<String>> groups = uf.getGroups();
        assertThat(groups).hasSize(2);  // {A,B,C} + {D}

        String rootA = uf.find("A");
        String rootC = uf.find("C");
        assertThat(rootA).isEqualTo(rootC);
    }

    @Test
    void unknownElement_throws() {
        UnionFind uf = new UnionFind(Arrays.asList("A"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> uf.find("X"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
