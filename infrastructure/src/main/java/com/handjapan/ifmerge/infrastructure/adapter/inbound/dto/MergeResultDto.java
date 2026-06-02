package com.handjapan.ifmerge.infrastructure.adapter.inbound.dto;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/merges/{id}/result のレスポンス。
 */
public record MergeResultDto(
        UUID jobId,
        String status,
        Summary summary,
        List<MergeGroupDto> groups,
        List<ModuleSimilarityMatrixDto> similarityMatrices
) {

    public record Summary(
            Integer totalInterfaces,
            Integer totalGroups,
            Integer mergeableGroups,
            Integer savedInterfaceCount,
            List<String> modules
    ) {
    }

    public record MergeGroupDto(
            String groupingId,
            String module,
            String scenario,
            List<GroupMemberDto> members,
            Boolean mergeRequired,
            String mergedIfName,
            Integer mergedFieldCount,
            List<MergedFieldDto> mergedFields
    ) {
    }

    public record GroupMemberDto(
            String ifName,
            String documentNumber,
            Integer itemCount,
            String ifSummary,
            List<String> representativeItems,
            String groupingReason
    ) {
    }

    public record MergedFieldDto(
            Integer no,
            String ebsTableId,
            String ebsTableName,
            String itemId,
            String itemName
    ) {
    }

    public record ModuleSimilarityMatrixDto(
            String module,
            List<ScenarioMatrixDto> scenarios
    ) {
    }

    public record ScenarioMatrixDto(
            String scenario,
            MatrixAxisDto axis,
            double[][] maxSimilarity,
            DirectionalValueDto[][] directionalSimilarity
    ) {
    }

    public record MatrixAxisDto(
            List<String> ifNames,
            List<String> documentNumbers
    ) {
    }

    public record DirectionalValueDto(
            Double rowToCol,
            Double colToRow
    ) {
    }
}
