package com.handjapan.ifmerge.infrastructure.adapter.inbound.mapper;

import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.merge.MergeInterfacesCommand;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.MergeGroup;
import com.handjapan.ifmerge.domain.merge.model.MergeResult;
import com.handjapan.ifmerge.domain.merge.model.SimilarityMode;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.InterfaceRecordDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.MergeRequestDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.MergeResultDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Merge 関連の DTO ↔ domain マッピング。
 */
@Component
public class MergeMapper {

    // ────────── REQUEST ──────────

    public MergeInterfacesCommand toCommand(MergeRequestDto req) {
        List<InterfaceRecord> records = req.records() == null ? List.of()
                : req.records().stream().map(this::toRecord).toList();

        MergeInterfacesCommand.Options options;
        if (req.options() == null) {
            options = MergeInterfacesCommand.Options.defaults();
        } else {
            BigDecimal th = req.options().threshold();
            double threshold = th == null ? 0.80 : th.doubleValue();
            SimilarityMode mode = SimilarityMode.fromString(req.options().mode());
            options = new MergeInterfacesCommand.Options(threshold, mode);
        }

        return new MergeInterfacesCommand(records, options);
    }

    private InterfaceRecord toRecord(InterfaceRecordDto d) {
        return new InterfaceRecord(
                d.no() == null ? 0 : d.no(),
                nullSafe(d.documentNumber()),
                nullSafe(d.ifName()),
                nullSafe(d.ebsTableName()),
                nullSafe(d.ebsTableId()),
                nullSafe(d.itemId()),
                nullSafe(d.itemName()),
                nullSafe(d.digitCount()),
                nullSafe(d.itemDescription()),
                nullSafe(d.dataType()),
                nullSafe(d.digitDecimal()),
                nullSafe(d.devType()),
                nullSafe(d.isKey()),
                nullSafe(d.required()),
                nullSafe(d.remarks())
        );
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // ────────── RESPONSE ──────────

    public MergeResultDto toResultDto(Job job) {
        MergeResult result = (MergeResult) job.result();
        if (result == null) {
            return new MergeResultDto(job.id(), job.status().name(), null, List.of(), List.of());
        }

        MergeResultDto.Summary summary = new MergeResultDto.Summary(
                result.summary().totalInterfaces(),
                result.summary().totalGroups(),
                result.summary().mergeableGroups(),
                result.summary().savedInterfaceCount(),
                new ArrayList<>(result.summary().modules())
        );

        List<MergeResultDto.MergeGroupDto> groups = result.groups().stream()
                .map(this::toGroupDto)
                .toList();

        List<MergeResultDto.ModuleSimilarityMatrixDto> matrices = result.similarityMatrices().stream()
                .map(this::toMatrixDto)
                .toList();

        return new MergeResultDto(job.id(), job.status().name(), summary, groups, matrices);
    }

    private MergeResultDto.MergeGroupDto toGroupDto(MergeGroup g) {
        // 当前 domain MergeGroup の memberIfNames は List<String> なので、
        // メンバー詳細（itemCount、ifSummary 等）は UseCase 側で別途構築する必要がある。
        // 暫定的に空の members リストを返す。完整実装は UseCase で MergeGroup を拡張して情報を持たせる。
        List<MergeResultDto.GroupMemberDto> members = g.memberIfNames().stream()
                .map(name -> new MergeResultDto.GroupMemberDto(
                        name, "", 0, "", List.of(), g.groupingReason()
                ))
                .collect(Collectors.toList());

        List<MergeResultDto.MergedFieldDto> fields = new ArrayList<>();
        int no = 1;
        for (var pair : g.mergedFields()) {
            fields.add(new MergeResultDto.MergedFieldDto(
                    no++, pair.tableId(), "", pair.itemId(), ""
            ));
        }

        return new MergeResultDto.MergeGroupDto(
                g.groupingId(),
                g.module(),
                g.scenario(),
                members,
                g.isMergeRequired(),
                g.mergedIfName(),
                g.mergedFieldCount(),
                fields
        );
    }

    private MergeResultDto.ModuleSimilarityMatrixDto toMatrixDto(MergeResult.ModuleSimilarityMatrix m) {
        List<MergeResultDto.ScenarioMatrixDto> scenarios = m.scenarios().stream()
                .map(s -> {
                    MergeResultDto.MatrixAxisDto axis = new MergeResultDto.MatrixAxisDto(
                            s.axis().ifNames(),
                            s.axis().documentNumbers()
                    );
                    MergeResultDto.DirectionalValueDto[][] dirVals = null;
                    if (s.directionalSimilarity() != null) {
                        int n = s.directionalSimilarity().length;
                        dirVals = new MergeResultDto.DirectionalValueDto[n][];
                        for (int i = 0; i < n; i++) {
                            dirVals[i] = new MergeResultDto.DirectionalValueDto[s.directionalSimilarity()[i].length];
                            for (int j = 0; j < s.directionalSimilarity()[i].length; j++) {
                                var v = s.directionalSimilarity()[i][j];
                                dirVals[i][j] = v == null ? null :
                                        new MergeResultDto.DirectionalValueDto(v.rowToCol(), v.colToRow());
                            }
                        }
                    }
                    return new MergeResultDto.ScenarioMatrixDto(
                            s.scenario(), axis, s.maxSimilarity(), dirVals
                    );
                })
                .toList();
        return new MergeResultDto.ModuleSimilarityMatrixDto(m.module(), scenarios);
    }
}
