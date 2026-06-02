package com.handjapan.ifmerge.domain.merge.model;

/**
 * 相似度計算モード。
 * 原 Python：mode="max" / "avg"（IFmerge/ebs_merger/similarity_calculator.py:13）。
 */
public enum SimilarityMode {

    /** |C| / min(|A|, |B|) — 厳格 */
    MAX,

    /** (|C|/|A| + |C|/|B|) / 2 — 双方向一致率の平均 */
    AVG;

    public static SimilarityMode fromString(String value) {
        if (value == null) return MAX;
        return switch (value.toLowerCase()) {
            case "max" -> MAX;
            case "avg" -> AVG;
            default -> throw new IllegalArgumentException("Unknown SimilarityMode: " + value);
        };
    }
}
