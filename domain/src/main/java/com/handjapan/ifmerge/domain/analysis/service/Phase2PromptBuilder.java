package com.handjapan.ifmerge.domain.analysis.service;

import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.shared.exception.AnalysisException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 2 のプロンプトを構築するビルダー。
 *
 * <p>原 Python: {@code IFmerge_1/analyzer/ai_analyzer.py:140-198 build_phase2_prompt}
 *
 * <p>役割:
 * <ol>
 *   <li>chunk（データ行リスト）を {@code [列番号]値} 形式の文字列にフォーマット</li>
 *   <li>{@code prompts.yaml::phase2} テンプレートに埋め込む</li>
 * </ol>
 */
public class Phase2PromptBuilder {

    public static final String TEMPLATE_KEY = "phase2";

    private static final String VAR_FILE_NAME = "file_name";
    private static final String VAR_DOC_NUMBER = "doc_number";
    private static final String VAR_IF_NAME = "if_name";
    private static final String VAR_CHUNK_TEXT = "chunk_text";
    private static final String VAR_COL_TABLE_NAME = "col_table_name";
    private static final String VAR_COL_TABLE_ID = "col_table_id";
    private static final String VAR_COL_ITEM_ID = "col_item_id";
    private static final String VAR_COL_DIGIT = "col_digit";

    private final PromptRepository promptRepository;

    public Phase2PromptBuilder(PromptRepository promptRepository) {
        this.promptRepository = Objects.requireNonNull(promptRepository);
    }

    /**
     * Phase 2 のプロンプト全文を組み立てる。
     *
     * @param fileName       設計書ファイル名
     * @param docNumber      Phase1 で取得した文書番号
     * @param ifName         Phase1 で取得した IF 名
     * @param chunk          データ行のチャンク（既に列フィルタ適用後でもよい）
     * @param columnMapping  列マッピング（プロンプト変数として LLM に文脈提供）
     * @return プロンプト文字列
     */
    public String build(String fileName,
                        String docNumber,
                        String ifName,
                        List<List<String>> chunk,
                        AnalysisAiGateway.ColumnMapping columnMapping) {
        Objects.requireNonNull(fileName);
        Objects.requireNonNull(docNumber);
        Objects.requireNonNull(ifName);
        Objects.requireNonNull(chunk);

        String chunkText = formatChunk(chunk);

        Map<String, Object> vars = Map.of(
                VAR_FILE_NAME, fileName,
                VAR_DOC_NUMBER, docNumber,
                VAR_IF_NAME, ifName,
                VAR_CHUNK_TEXT, chunkText,
                VAR_COL_TABLE_NAME, columnMapping == null ? -1 : columnMapping.colTableName(),
                VAR_COL_TABLE_ID, columnMapping == null ? -1 : columnMapping.colTableId(),
                VAR_COL_ITEM_ID, columnMapping == null ? -1 : columnMapping.colItemId(),
                VAR_COL_DIGIT, columnMapping == null ? -1 : columnMapping.colDigit()
        );

        return promptRepository.render(TEMPLATE_KEY, vars)
                .orElseThrow(() -> new AnalysisException(
                        "PROMPT_TEMPLATE_MISSING",
                        "prompts.yaml に phase2 テンプレートが見つかりません"));
    }

    /**
     * データ行リスト → {@code [列番号]値} 形式のテキスト。
     *
     * <p>原 Python {@code _format_data_rows}（ai_analyzer.py:348-363）と等価。
     */
    public static String formatChunk(List<List<String>> chunk) {
        List<String> lines = new ArrayList<>();
        for (List<String> row : chunk) {
            List<String> tagged = new ArrayList<>();
            for (int ci = 0; ci < row.size(); ci++) {
                String c = row.get(ci);
                if (c != null && !c.isEmpty()) {
                    tagged.add("[" + ci + "]" + c);
                }
            }
            if (!tagged.isEmpty()) {
                lines.add(String.join("  ", tagged));
            }
        }
        return String.join("\n", lines);
    }
}
