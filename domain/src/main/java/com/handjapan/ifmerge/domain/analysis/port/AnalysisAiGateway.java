package com.handjapan.ifmerge.domain.analysis.port;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;

import java.util.List;

/**
 * 解析用 AI ゲートウェイポート。
 * Phase 1: シート構造識別
 * Phase 2: 字段抽取
 *
 * 実装は infrastructure 層（SAP AI Core 経由の Claude 呼び出し）。
 */
public interface AnalysisAiGateway {

    /**
     * Phase 1：全シートの先頭部分から固定情報・データシート・列構造を識別する。
     *
     * @param fileName        ファイル名
     * @param sheets          清洗済みシートリスト
     * @param phase1HeadRows  各シートの先頭何行を送信するか
     * @return Phase 1 の解析結果
     */
    Phase1Result analyzePhase1(String fileName, List<CleanedSheet> sheets, int phase1HeadRows);

    /**
     * Phase 2：データ行チャンクから項目を抽出する。
     *
     * @param fileName     ファイル名
     * @param docNumber    文書管理番号（Phase 1 で取得）
     * @param ifName       IF 名（Phase 1 で取得）
     * @param chunk        データ行チャンク
     * @param columnMapping  列マッピング情報
     * @return 抽出された InterfaceRecord リスト
     */
    List<InterfaceRecord> analyzePhase2(String fileName,
                                        String docNumber,
                                        String ifName,
                                        List<List<String>> chunk,
                                        ColumnMapping columnMapping);

    /**
     * Phase 1 の結果：文書情報 + データシートメタデータ。
     */
    record Phase1Result(
            String documentNumber,
            String ifName,
            List<DataSheetMeta> dataSheets
    ) {
    }

    record DataSheetMeta(
            String sheetName,
            int dataStartRow,
            ColumnMapping columnMapping
    ) {
    }

    /**
     * 列マッピング（不明列は -1）。
     */
    record ColumnMapping(
            int colTableName,
            int colTableId,
            int colItemId,
            int colDigit
    ) {
    }
}
