package com.handjapan.ifmerge.infrastructure.adapter.inbound.mapper;

import com.handjapan.ifmerge.application.analysis.AnalyzeDocumentCommand;
import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.domain.analysis.model.AnalysisResult;
import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.AnalysisResultDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.AnalyzeRequestDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.InterfaceRecordDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.JobDto;
import com.handjapan.ifmerge.infrastructure.config.IfmergeProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DTO ↔ domain / application モデルの変換。
 */
@Component
public class AnalysisMapper {

    private final IfmergeProperties props;

    public AnalysisMapper(IfmergeProperties props) {
        this.props = props;
    }

    // ────────── REQUEST ──────────

    public AnalyzeDocumentCommand toCommand(AnalyzeRequestDto req) {
        List<CleanedSheet> sheets = req.sheets() == null ? List.of()
                : req.sheets().stream()
                .map(s -> new CleanedSheet(
                        s.name(),
                        s.headers() == null ? List.of() : s.headers(),
                        s.rows() == null ? List.of() : s.rows()
                ))
                .toList();

        // 既定値は ifmerge.analysis.* から（リクエストで明示された値が優先）。
        int defP1 = props.analysis().phase1HeadRowsOrDefault();
        int defChunk = props.analysis().maxChunkRowsOrDefault();
        AnalyzeDocumentCommand.Options options;
        if (req.options() == null) {
            options = new AnalyzeDocumentCommand.Options(defP1, defChunk);
        } else {
            int p1 = req.options().phase1HeadRows() == null ? defP1 : req.options().phase1HeadRows();
            int chunk = req.options().maxChunkRows() == null ? defChunk : req.options().maxChunkRows();
            options = new AnalyzeDocumentCommand.Options(p1, chunk);
        }

        return new AnalyzeDocumentCommand(req.fileName(), sheets, options);
    }

    // ────────── RESPONSE ──────────

    public JobDto toJobDto(Job job) {
        return new JobDto(
                job.id(),
                job.type().name(),
                job.status().name(),
                job.progress(),
                job.phase(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.error() == null ? null : new JobDto.ErrorInfoDto(
                        job.error().code(),
                        job.error().message(),
                        job.error().occurredAt()
                )
        );
    }

    public AnalysisResultDto toResultDto(Job job, String fileName) {
        AnalysisResult result = (AnalysisResult) job.result();
        if (result == null) {
            return new AnalysisResultDto(job.id(), job.status().name(), fileName, null);
        }

        List<AnalysisResultDto.DataSheetInfoDto> sheetInfos = result.dataSheets().stream()
                .map(ds -> new AnalysisResultDto.DataSheetInfoDto(
                        ds.sheetName(), ds.dataStartRow(), ds.recordCount()
                ))
                .toList();

        List<InterfaceRecordDto> records = result.records().stream()
                .map(this::toRecordDto)
                .toList();

        AnalysisResultDto.AnalysisResultBody body = new AnalysisResultDto.AnalysisResultBody(
                result.documentNumber(),
                result.ifName(),
                sheetInfos,
                records,
                records.size()
        );
        return new AnalysisResultDto(job.id(), job.status().name(), fileName, body);
    }

    public InterfaceRecordDto toRecordDto(InterfaceRecord r) {
        return new InterfaceRecordDto(
                r.no(),
                r.documentNumber(),
                r.ifName(),
                r.ebsTableName(),
                r.ebsTableId(),
                r.itemId(),
                r.itemName(),
                r.digitCount(),
                r.itemDescription(),
                r.dataType(),
                r.digitDecimal(),
                r.devType(),
                r.isKey(),
                r.required(),
                r.remarks()
        );
    }
}
