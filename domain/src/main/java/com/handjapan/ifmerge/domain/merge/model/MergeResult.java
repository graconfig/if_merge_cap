package com.handjapan.ifmerge.domain.merge.model;

import java.util.List;
import java.util.Set;

/**
 * 合并処理全体の結果。
 */
public record MergeResult(
        Summary summary,
        List<MergeGroup> groups,
        List<ModuleSimilarityMatrix> similarityMatrices
) {

    public record Summary(
            int totalInterfaces,
            int totalGroups,
            int mergeableGroups,
            int savedInterfaceCount,
            Set<String> modules
    ) {
    }

    public record ModuleSimilarityMatrix(
            String module,
            List<ScenarioMatrix> scenarios
    ) {
    }

    public record ScenarioMatrix(
            String scenario,
            Axis axis,
            double[][] maxSimilarity,
            DirectionalValue[][] directionalSimilarity
    ) {
    }

    public record Axis(
            List<String> ifNames,
            List<String> documentNumbers
    ) {
    }

    public record DirectionalValue(double rowToCol, double colToRow) {
    }
}
