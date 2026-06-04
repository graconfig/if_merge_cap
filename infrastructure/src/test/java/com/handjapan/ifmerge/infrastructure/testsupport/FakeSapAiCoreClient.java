package com.handjapan.ifmerge.infrastructure.testsupport;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.port.ClassificationAiGateway;
import com.handjapan.ifmerge.domain.merge.port.NamingAiGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>テスト専用</strong>の AI Gateway 偽実装（旧 MockSapAiCoreClient を test スコープへ降格）。
 *
 * <p>本番コードからは削除済み（{@link com.handjapan.ifmerge.infrastructure.adapter.outbound.ai.SapAiCoreClient}
 * が唯一の実装）。実 SAP AI Core を呼ばずに UseCase / Controller を検証するために使う。
 *
 * <p>使い方：
 * <ul>
 *   <li>純粋な UseCase 単体テスト：{@code new AnalyzeDocumentUseCase(jobManager, new FakeSapAiCoreClient(), executor)}</li>
 *   <li>{@code @SpringBootTest}：{@code @TestConfiguration} で {@code @Bean @Primary} として登録する</li>
 * </ul>
 *
 * <p>Spring アノテーションは付けない（本番コンテキストに混入しないため）。
 */
public class FakeSapAiCoreClient
        implements AnalysisAiGateway, ClassificationAiGateway, NamingAiGateway {

    // ── AnalysisAiGateway ──

    @Override
    public Phase1Result analyzePhase1(String fileName, List<CleanedSheet> sheets, int phase1HeadRows) {
        List<DataSheetMeta> dataSheets = new ArrayList<>();
        for (CleanedSheet sheet : sheets) {
            String name = sheet.name();
            if (name == null) continue;
            if (name.contains("項目") || name.contains("データ") || name.equalsIgnoreCase("Sheet1")) {
                dataSheets.add(new DataSheetMeta(name, 1, new ColumnMapping(1, 2, 3, 5)));
            }
        }
        return new Phase1Result(deriveDocumentNumber(fileName), deriveIfName(fileName), dataSheets);
    }

    @Override
    public List<InterfaceRecord> analyzePhase2(String fileName,
                                               String docNumber,
                                               String ifName,
                                               List<List<String>> chunk,
                                               ColumnMapping columnMapping) {
        List<InterfaceRecord> records = new ArrayList<>();
        int idx = 1;
        for (List<String> row : chunk) {
            String tableName = readCell(row, columnMapping.colTableName());
            String tableId = readCell(row, columnMapping.colTableId());
            String itemId = readCell(row, columnMapping.colItemId());
            String digit = readCell(row, columnMapping.colDigit());
            if (itemId.isEmpty() && tableId.isEmpty()) continue;
            records.add(new InterfaceRecord(
                    idx++, docNumber, ifName, tableName, tableId, itemId,
                    "", digit, "", "", "", "", "", "", ""));
        }
        return records;
    }

    // ── ClassificationAiGateway ──

    @Override
    public Map<String, CategoryInfo> classify(List<IFInfo> ifInfos,
                                              Map<String, List<InterfaceRecord>> recordsByIf) {
        Map<String, CategoryInfo> result = new LinkedHashMap<>();
        List<String> names = ifInfos.stream().map(IFInfo::ifName).toList();
        result.put("その他_未分類", new CategoryInfo("その他", "未分類", "Fake 分類", names));
        return result;
    }

    // ── NamingAiGateway ──

    @Override
    public Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos,
                                                    Map<String, List<InterfaceRecord>> recordsByIf) {
        Map<String, IFSummary> result = new HashMap<>();
        for (IFInfo info : ifInfos) {
            result.put(info.ifName(), new IFSummary("Fake 概要：" + info.ifName(), info.representativeItem()));
        }
        return result;
    }

    @Override
    public String generateMergedIfName(List<String> groupMemberIfNames,
                                        Map<String, List<InterfaceRecord>> recordsByIf) {
        if (groupMemberIfNames.size() == 1) return groupMemberIfNames.get(0);
        return String.join("_", groupMemberIfNames) + "_統合連携IF";
    }

    // ── helpers ──

    private String readCell(List<String> row, int col) {
        if (col < 0 || col >= row.size()) return "";
        String v = row.get(col);
        return v == null ? "" : v;
    }

    private String deriveDocumentNumber(String fileName) {
        if (fileName == null) return "BDN-FAKE-001";
        var matcher = java.util.regex.Pattern
                .compile("^(BDN-[A-Z]+-[A-Z]+-[\\w]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(fileName);
        if (matcher.find()) return matcher.group(1).toUpperCase();
        return fileName.replaceAll("\\.[^.]+$", "");
    }

    private String deriveIfName(String fileName) {
        return "Fake-IF-" + (fileName == null ? "Unknown" : fileName.replaceAll("\\.[^.]+$", ""));
    }
}
