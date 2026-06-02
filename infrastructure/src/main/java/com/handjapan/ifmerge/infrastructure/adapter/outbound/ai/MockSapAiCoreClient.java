package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.port.ClassificationAiGateway;
import com.handjapan.ifmerge.domain.merge.port.NamingAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>モック実装</strong>：SAP AI Core を呼ばずに固定の偽データを返す。
 *
 * <p>有効化条件：{@code application.yaml} で
 * <pre>
 * ifmerge.ai.mock: true   # デフォルト true（開発期）
 * </pre>
 * 本番環境では {@code ifmerge.ai.mock=false} に設定して {@link SapAiCoreClient}（本物）を使う。
 *
 * <p>用途：
 * <ul>
 *   <li>HTTP リクエスト → UseCase → Job 状態機 のフレーム動作確認</li>
 *   <li>JSON DTO のシリアライズ確認</li>
 *   <li>GUI 側との結合テスト（実 LLM 呼ばず）</li>
 * </ul>
 */
@Component
@Primary
@ConditionalOnProperty(name = "ifmerge.ai.mock", havingValue = "true", matchIfMissing = true)
public class MockSapAiCoreClient implements AnalysisAiGateway, ClassificationAiGateway, NamingAiGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSapAiCoreClient.class);

    // ============================================================
    // AnalysisAiGateway — Phase 1 / Phase 2
    // ============================================================

    @Override
    public Phase1Result analyzePhase1(String fileName, List<CleanedSheet> sheets, int phase1HeadRows) {
        log.info("[MOCK] Phase1: fileName={}, sheets={}", fileName, sheets.size());

        // 「データシート」として「エクスポート項目」「インポート項目」等を含むシートを採用
        List<DataSheetMeta> dataSheets = new ArrayList<>();
        for (CleanedSheet sheet : sheets) {
            String name = sheet.name();
            if (name == null) continue;
            if (name.contains("項目") || name.contains("データ")
                    || name.equalsIgnoreCase("Sheet1")) {
                dataSheets.add(new DataSheetMeta(
                        name,
                        1,  // data_start_row（headers の次から）
                        new ColumnMapping(1, 2, 3, 5)
                ));
            }
        }

        return new Phase1Result(
                deriveDocumentNumber(fileName),
                deriveIfName(fileName, sheets),
                dataSheets
        );
    }

    @Override
    public List<InterfaceRecord> analyzePhase2(String fileName,
                                               String docNumber,
                                               String ifName,
                                               List<List<String>> chunk,
                                               ColumnMapping columnMapping) {
        log.info("[MOCK] Phase2: fileName={}, chunkRows={}", fileName, chunk.size());

        List<InterfaceRecord> records = new ArrayList<>();
        int idx = 1;
        for (List<String> row : chunk) {
            // 簡易抽出：列マッピングを使って必要な値を取り出す
            String tableName = readCell(row, columnMapping.colTableName());
            String tableId = readCell(row, columnMapping.colTableId());
            String itemId = readCell(row, columnMapping.colItemId());
            String digit = readCell(row, columnMapping.colDigit());

            // 値がほぼないなら飛ばす
            if (itemId.isEmpty() && tableId.isEmpty()) continue;

            records.add(new InterfaceRecord(
                    idx++,
                    docNumber,
                    ifName,
                    tableName,
                    tableId,
                    itemId,
                    "",     // itemName — 実装時に AI が抽出する
                    digit,
                    "", "", "", "", "", "", ""
            ));
        }
        return records;
    }

    // ============================================================
    // ClassificationAiGateway
    // ============================================================

    @Override
    public Map<String, CategoryInfo> classify(List<IFInfo> ifInfos) {
        log.info("[MOCK] classify: {} IFs", ifInfos.size());
        // 全 IF を単一カテゴリに割り当てる
        Map<String, CategoryInfo> result = new LinkedHashMap<>();
        List<String> names = ifInfos.stream().map(IFInfo::ifName).toList();
        result.put("その他_未分類", new CategoryInfo("その他", "未分類", "Mock 分類", names));
        return result;
    }

    // ============================================================
    // NamingAiGateway
    // ============================================================

    @Override
    public Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos) {
        log.info("[MOCK] generateAllIfInfo: {} IFs", ifInfos.size());
        Map<String, IFSummary> result = new HashMap<>();
        for (IFInfo info : ifInfos) {
            result.put(info.ifName(),
                    new IFSummary("Mock 概要：" + info.ifName(), info.representativeItem()));
        }
        return result;
    }

    @Override
    public String generateMergedIfName(List<String> groupMemberIfNames, List<IFInfo> ifInfos) {
        log.info("[MOCK] generateMergedIfName: {} members", groupMemberIfNames.size());
        if (groupMemberIfNames.size() == 1) return groupMemberIfNames.get(0);
        return String.join("_", groupMemberIfNames) + "_統合連携IF";
    }

    // ============================================================
    // helpers
    // ============================================================

    private String readCell(List<String> row, int col) {
        if (col < 0 || col >= row.size()) return "";
        String v = row.get(col);
        return v == null ? "" : v;
    }

    private String deriveDocumentNumber(String fileName) {
        if (fileName == null) return "BDN-MOCK-001";
        // BDN-XXX-XX-NNN プレフィックスを抽出
        var matcher = java.util.regex.Pattern
                .compile("^(BDN-[A-Z]+-[A-Z]+-[\\w]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(fileName);
        if (matcher.find()) return matcher.group(1).toUpperCase();
        return fileName.replaceAll("\\.[^.]+$", "");  // 拡張子を取り除く
    }

    private String deriveIfName(String fileName, List<CleanedSheet> sheets) {
        // ファイル名から推定。実装時は AI が判断
        return "Mock-IF-" + (fileName == null ? "Unknown" : fileName.replaceAll("\\.[^.]+$", ""));
    }
}
