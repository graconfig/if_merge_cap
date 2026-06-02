package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.analysis.service.Phase1PromptBuilder;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.port.ClassificationAiGateway;
import com.handjapan.ifmerge.domain.merge.port.NamingAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SAP AI Core 経由の Claude 呼び出しクライアント。
 * 3 つの Gateway ポートを同一クラスで実装する（共通の token 管理・retry 機構を持つため）。
 *
 * <p>原 Python 対応：
 * <ul>
 *   <li>{@code IFmerge_1/analyzer/sap_client.py} — OAuth + Converse API</li>
 *   <li>{@code IFmerge_1/analyzer/ai_analyzer.py} — Phase1/Phase2 編排</li>
 *   <li>{@code IFmerge/ebs_merger/ai_generator.py} — Naming/Classification</li>
 * </ul>
 *
 * <p>呼び出しパラメータ（原 Python と一致）：
 * <ul>
 *   <li>Phase1/Phase2: {@code temperature=0.3, max_tokens=16384}</li>
 *   <li>Classification/Naming: {@code temperature=0.7, max_tokens=8192}</li>
 *   <li>Retry: {@code max_retries=3, wait=2^attempt} → 1s, 2s</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ifmerge.ai.mock", havingValue = "false")
public class SapAiCoreClient implements AnalysisAiGateway, ClassificationAiGateway, NamingAiGateway {

    private static final Logger log = LoggerFactory.getLogger(SapAiCoreClient.class);

    /** Phase 1/Phase 2 用の LLM 推論パラメータ。原 Python ai_analyzer.py:296-297 と一致。 */
    private static final double ANALYSIS_TEMPERATURE = 0.3;
    private static final int ANALYSIS_MAX_TOKENS = 16384;

    /** Classification/Naming 用。原 Python ai_generator.py:214 と一致。 */
    private static final double GENERATION_TEMPERATURE = 0.7;
    private static final int GENERATION_MAX_TOKENS = 8192;

    private final Phase1PromptBuilder phase1Builder;
    // TODO: Phase2PromptBuilder phase2Builder
    // TODO: ClassificationPromptBuilder classifyBuilder
    // TODO: NamingPromptBuilder namingBuilder
    private final PromptRepository promptRepository;

    public SapAiCoreClient(PromptRepository promptRepository) {
        this.promptRepository = promptRepository;
        this.phase1Builder = new Phase1PromptBuilder(promptRepository);
    }

    // ============================================================
    // AnalysisAiGateway
    // ============================================================

    @Override
    public Phase1Result analyzePhase1(String fileName, List<CleanedSheet> sheets, int phase1HeadRows) {
        log.info("Phase1 開始: fileName={}, sheets={}, headRows={}",
                fileName, sheets.size(), phase1HeadRows);

        // ① プロンプト構築（domain の Phase1PromptBuilder を使用）
        String prompt = phase1Builder.build(fileName, sheets, phase1HeadRows);
        log.debug("Phase1 prompt length={} chars", prompt.length());

        // ② Tool schema (extract_doc_meta)
        Object toolConfig = buildExtractDocMetaTool();

        // ③ Converse API 呼び出し（temperature=0.3, max_tokens=16384, retry付き）
        // TODO: 実装
        //  - OAuth2 client_credentials で token 取得（キャッシュ）
        //  - POST {base_url}/inference/deployments/{id}/converse
        //  - body: { messages, toolConfig: { tools, toolChoice: {any:{}} }, inferenceConfig }
        //  - Resilience4j @Retry: max-attempts=3, wait-duration=1s, multiplier=2
        //  - response から content[].toolUse を抽出
        //  - tool_use.input を Phase1Result に変換
        //    （ColumnMapping は nested record として組み立て）
        log.warn("[stub] Phase1 HTTP 呼び出しは未実装");
        throw new UnsupportedOperationException(
                "SapAiCoreClient.analyzePhase1: HTTP call not implemented yet. " +
                "Prompt is built correctly (length=" + prompt.length() + ")"
        );
    }

    @Override
    public List<InterfaceRecord> analyzePhase2(String fileName,
                                               String docNumber,
                                               String ifName,
                                               List<List<String>> chunk,
                                               ColumnMapping columnMapping) {
        // TODO: Phase2PromptBuilder を使ってプロンプト構築 → Converse API
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ============================================================
    // ClassificationAiGateway
    // ============================================================

    @Override
    public Map<String, CategoryInfo> classify(List<IFInfo> ifInfos) {
        // TODO: prompts.yaml::classify_interfaces をレンダリング → Converse API
        log.info("[stub] classify: {} IFs", ifInfos.size());
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ============================================================
    // NamingAiGateway
    // ============================================================

    @Override
    public Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos) {
        // TODO: prompts.yaml::generate_all_if_info をレンダリング → Converse API
        log.info("[stub] generateAllIfInfo: {} IFs", ifInfos.size());
        return new HashMap<>();
    }

    @Override
    public String generateMergedIfName(List<String> groupMemberIfNames, List<IFInfo> ifInfos) {
        // TODO: prompts.yaml::generate_merged_if_name をレンダリング → Converse API
        log.info("[stub] generateMergedIfName: {} members", groupMemberIfNames.size());
        return String.join("_", groupMemberIfNames);
    }

    // ============================================================
    // Tool schemas（Converse API 仕様）
    // ============================================================

    /**
     * Phase 1 の extract_doc_meta Tool schema を構築する。
     * 原 Python {@code IFmerge_1/analyzer/ai_analyzer.py:77-137} と等価。
     *
     * <p>必須フィールド：
     * <ul>
     *   <li>document_number, if_name, data_sheets（トップレベル）</li>
     *   <li>sheet_name, data_start_row, col_table_id, col_item_id（data_sheets 各要素）</li>
     *   <li>col_table_name, col_digit は不要（不明時 -1）</li>
     * </ul>
     */
    private Object buildExtractDocMetaTool() {
        // TODO: 実際の Tool schema オブジェクトを構築（SAP AI SDK の型 or Map）
        //  返却例：
        //  {
        //    "toolSpec": {
        //      "name": "extract_doc_meta",
        //      "description": "設計書の固定情報・データシート・列構造を識別する",
        //      "inputSchema": {
        //        "json": {
        //          "type": "object",
        //          "properties": {
        //            "document_number": { "type": "string" },
        //            "if_name":         { "type": "string" },
        //            "data_sheets": {
        //              "type": "array",
        //              "items": {
        //                "type": "object",
        //                "properties": {
        //                  "sheet_name":      { "type": "string" },
        //                  "data_start_row":  { "type": "integer" },
        //                  "col_table_name":  { "type": "integer" },
        //                  "col_table_id":    { "type": "integer" },
        //                  "col_item_id":     { "type": "integer" },
        //                  "col_digit":       { "type": "integer" }
        //                },
        //                "required": ["sheet_name", "data_start_row", "col_table_id", "col_item_id"]
        //              }
        //            }
        //          },
        //          "required": ["document_number", "if_name", "data_sheets"]
        //        }
        //      }
        //    }
        //  }
        return null;
    }
}
