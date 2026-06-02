package com.handjapan.ifmerge.domain.merge.model;

/**
 * IF ペア間の相似度。
 */
public record SimilarityPair(String if1Name, String if2Name, double similarity) {

    public SimilarityPair {
        if (similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException("similarity must be in [0,1], got: " + similarity);
        }
    }
}
