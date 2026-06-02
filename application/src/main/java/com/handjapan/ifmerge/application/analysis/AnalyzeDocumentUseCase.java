package com.handjapan.ifmerge.application.analysis;

import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobManager;
import com.handjapan.ifmerge.application.job.JobType;
import com.handjapan.ifmerge.domain.analysis.model.AnalysisResult;
import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 解析ユースケース：Phase1（構造識別）→ Phase2（字段抽取）を編排する。
 *
 * <p>原 Python: {@code IFmerge_1/analyzer/ai_analyzer.py:378-493 analyze_file()}
 * に対応。
 *
 * <p>進捗の刻み：
 * <ul>
 *   <li>0% — PENDING</li>
 *   <li>10% — PHASE1_RUNNING</li>
 *   <li>20% — PHASE1_COMPLETED</li>
 *   <li>20 + (75/N × i)% — PHASE2_CHUNK_i_OF_N</li>
 *   <li>95% — BUILDING_RESULT</li>
 *   <li>100% — COMPLETED</li>
 * </ul>
 */
@Service
public class AnalyzeDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeDocumentUseCase.class);

    private final JobManager jobManager;
    private final AnalysisAiGateway aiGateway;

    public AnalyzeDocumentUseCase(JobManager jobManager, AnalysisAiGateway aiGateway) {
        this.jobManager = jobManager;
        this.aiGateway = aiGateway;
    }

    public Job submit(AnalyzeDocumentCommand cmd) {
        Job job = jobManager.create(JobType.ANALYSIS);
        runAsync(job.id(), cmd);
        return job;
    }

    @Async("jobExecutor")
    public void runAsync(UUID jobId, AnalyzeDocumentCommand cmd) {
        try {
            // ────────── Phase 1: 構造識別 ──────────
            jobManager.markRunning(jobId, "PHASE1_RUNNING");
            log.info("Job {}: Phase1 開始（fileName={}）", jobId, cmd.fileName());

            AnalysisAiGateway.Phase1Result phase1 = aiGateway.analyzePhase1(
                    cmd.fileName(),
                    cmd.sheets(),
                    cmd.options().phase1HeadRows()
            );
            jobManager.updateProgress(jobId, 20, "PHASE1_COMPLETED");
            log.info("Job {}: Phase1 完了: doc={}, if={}, dataSheets={}",
                    jobId,
                    phase1.documentNumber(),
                    phase1.ifName(),
                    phase1.dataSheets().size());

            // ────────── Phase 2: 字段抽取（chunk 循環） ──────────
            // data_sheets を sheet 名で検索可能にする
            Map<String, AnalysisAiGateway.DataSheetMeta> sheetMetaByName = new HashMap<>();
            for (AnalysisAiGateway.DataSheetMeta meta : phase1.dataSheets()) {
                sheetMetaByName.put(meta.sheetName(), meta);
            }

            // chunk 総数を先に算出（progress 計算用）
            int totalChunks = countTotalChunks(cmd, sheetMetaByName);
            log.info("Job {}: Phase2 chunk 総数 = {}", jobId, totalChunks);

            List<InterfaceRecord> allRecords = new ArrayList<>();
            List<AnalysisResult.DataSheetInfo> dataSheetInfos = new ArrayList<>();
            int chunkIndex = 0;

            for (CleanedSheet sheet : cmd.sheets()) {
                AnalysisAiGateway.DataSheetMeta meta = sheetMetaByName.get(sheet.name());
                if (meta == null) {
                    log.debug("Job {}: sheet '{}' はデータシートでないためスキップ",
                            jobId, sheet.name());
                    continue;
                }

                // headers + rows を統合し、data_start_row 以降を取得
                List<List<String>> allRows = new ArrayList<>();
                if (sheet.headers() != null && !sheet.headers().isEmpty()) {
                    allRows.add(sheet.headers());
                }
                if (sheet.rows() != null) {
                    allRows.addAll(sheet.rows());
                }
                List<List<String>> dataRows = allRows.size() > meta.dataStartRow()
                        ? allRows.subList(meta.dataStartRow(), allRows.size())
                        : List.of();

                if (dataRows.isEmpty()) {
                    log.info("Job {}: sheet '{}' のデータ行が空", jobId, sheet.name());
                    dataSheetInfos.add(new AnalysisResult.DataSheetInfo(
                            sheet.name(), meta.dataStartRow(), 0));
                    continue;
                }

                // chunk 分割
                int chunkSize = cmd.options().maxChunkRows();
                List<List<List<String>>> chunks = splitIntoChunks(dataRows, chunkSize);
                log.info("Job {}: sheet '{}' — {} 行を {} chunk に分割",
                        jobId, sheet.name(), dataRows.size(), chunks.size());

                int sheetRecordCount = 0;
                for (List<List<String>> chunk : chunks) {
                    chunkIndex++;
                    int progress = 20 + (int) (75.0 * chunkIndex / Math.max(1, totalChunks));
                    String phase = String.format("PHASE2_CHUNK_%d_OF_%d", chunkIndex, totalChunks);
                    jobManager.updateProgress(jobId, progress, phase);

                    List<InterfaceRecord> records = aiGateway.analyzePhase2(
                            cmd.fileName(),
                            phase1.documentNumber(),
                            phase1.ifName(),
                            chunk,
                            meta.columnMapping()
                    );
                    allRecords.addAll(records);
                    sheetRecordCount += records.size();
                }

                dataSheetInfos.add(new AnalysisResult.DataSheetInfo(
                        sheet.name(), meta.dataStartRow(), sheetRecordCount));
            }

            // ────────── 結果組立 ──────────
            jobManager.updateProgress(jobId, 95, "BUILDING_RESULT");
            // 連番 no を振り直す
            List<InterfaceRecord> numbered = new ArrayList<>(allRecords.size());
            int no = 1;
            for (InterfaceRecord r : allRecords) {
                numbered.add(new InterfaceRecord(
                        no++,
                        r.documentNumber(), r.ifName(),
                        r.ebsTableName(), r.ebsTableId(),
                        r.itemId(), r.itemName(), r.digitCount(),
                        r.itemDescription(), r.dataType(), r.digitDecimal(),
                        r.devType(), r.isKey(), r.required(), r.remarks()
                ));
            }

            AnalysisResult result = new AnalysisResult(
                    phase1.documentNumber(),
                    phase1.ifName(),
                    dataSheetInfos,
                    numbered
            );

            jobManager.markSucceeded(jobId, result);
            log.info("Job {}: 解析完了（{} records）", jobId, numbered.size());

        } catch (Exception e) {
            log.error("Job {}: 解析失敗", jobId, e);
            jobManager.markFailed(jobId, "ANALYZE_FAILED", e.getMessage());
        }
    }

    private int countTotalChunks(AnalyzeDocumentCommand cmd,
                                 Map<String, AnalysisAiGateway.DataSheetMeta> sheetMetaByName) {
        int total = 0;
        int chunkSize = cmd.options().maxChunkRows();
        for (CleanedSheet sheet : cmd.sheets()) {
            AnalysisAiGateway.DataSheetMeta meta = sheetMetaByName.get(sheet.name());
            if (meta == null) continue;

            int allRowCount =
                    ((sheet.headers() != null && !sheet.headers().isEmpty()) ? 1 : 0)
                    + (sheet.rows() == null ? 0 : sheet.rows().size());
            int dataRowCount = Math.max(0, allRowCount - meta.dataStartRow());
            if (dataRowCount == 0) continue;
            total += (int) Math.ceil((double) dataRowCount / chunkSize);
        }
        return Math.max(1, total);   // 0 除算を防ぐ
    }

    private List<List<List<String>>> splitIntoChunks(List<List<String>> rows, int chunkSize) {
        if (rows.size() <= chunkSize) return List.of(rows);
        List<List<List<String>>> chunks = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += chunkSize) {
            chunks.add(rows.subList(i, Math.min(i + chunkSize, rows.size())));
        }
        return chunks;
    }
}
