package com.handjapan.ifmerge.domain.analysis.service;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.shared.exception.AnalysisException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 1 のプロンプトを構築するビルダー。
 *
 * <p>原 Python：
 * <ul>
 *   <li>{@code IFmerge_1/analyzer/ai_analyzer.py:46-74} — build_phase1_prompt</li>
 *   <li>{@code IFmerge_1/analyzer/ai_analyzer.py:318-330} — _format_sheet_head</li>
 * </ul>
 *
 * <p>役割：
 * <ol>
 *   <li>cleaned sheets を {@code [列番号]値} 形式の文字列にフォーマットする</li>
 *   <li>{@link PromptRepository} 経由で {@code prompts.yaml::phase1} テンプレートに埋め込む</li>
 * </ol>
 *
 * <p>純 Java（フレームワーク非依存）。SapAiCoreClient から手動でインスタンス化される。
 */
public class Phase1PromptBuilder {

    /** prompts.yaml 内のテンプレートキー。 */
    public static final String TEMPLATE_KEY = "phase1";

    private static final String TEMPLATE_VAR_FILE_NAME = "file_name";
    private static final String TEMPLATE_VAR_SHEET_HEAD = "sheet_head_text";

    private final PromptRepository promptRepository;

    public Phase1PromptBuilder(PromptRepository promptRepository) {
        this.promptRepository = Objects.requireNonNull(promptRepository, "promptRepository");
    }

    /**
     * Phase 1 用のプロンプト全文を組み立てる。
     *
     * @param fileName       設計書ファイル名（テンプレート変数）
     * @param sheets         GUI が清洗した CleanedSheet リスト
     * @param phase1HeadRows 各シートの先頭何行を AI に送るか
     * @return プロンプト文字列（占位符展開済み）
     * @throws AnalysisException prompts.yaml に phase1 エントリが存在しない場合
     */
    public String build(String fileName, List<CleanedSheet> sheets, int phase1HeadRows) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(sheets, "sheets");
        if (phase1HeadRows <= 0) {
            throw new IllegalArgumentException("phase1HeadRows must be > 0, got: " + phase1HeadRows);
        }

        String sheetHeadText = formatSheetHead(sheets, phase1HeadRows);

        return promptRepository
                .render(TEMPLATE_KEY, Map.of(
                        TEMPLATE_VAR_FILE_NAME, fileName,
                        TEMPLATE_VAR_SHEET_HEAD, sheetHeadText
                ))
                .orElseThrow(() -> new AnalysisException(
                        "PROMPT_TEMPLATE_MISSING",
                        "prompts.yaml に phase1 テンプレートが見つかりません"
                ));
    }

    /**
     * 各シートの先頭 N 行を {@code [列番号]値} 形式でフォーマットする。
     *
     * <p>出力例：
     * <pre>
     * === Sheet: エクスポート項目 ===
     * [Row 0] [0]No  [1]EBSテーブル名  [2]EBSテーブルID  [3]項目ID
     * [Row 1] [0]1  [1]受注ヘッダ  [2]OE_ORDER_HEADERS_ALL  [3]ORDER_NUMBER
     * === Sheet: 表紙 ===
     * [Row 0] [0]文書管理番号  [1]BDN-EPD-OF-093
     * </pre>
     *
     * <p>原 Python の {@code _format_sheet_head} と挙動を厳密に一致させる：
     * <ul>
     *   <li>{@code headers} があれば先頭行に追加して all_rows を構築</li>
     *   <li>{@code maxRows} 行で切り取り（headers 含む）</li>
     *   <li>各セル：null / 空文字はスキップ（Python の {@code if c} と等価）</li>
     *   <li>非空セルだけのある行のみ出力（全空行はスキップ）</li>
     *   <li>セパレータ：行内 "  "（2 スペース）、行間 "\n"（単一改行）</li>
     * </ul>
     *
     * @param sheets  対象シートリスト
     * @param maxRows 各シートで処理する最大行数（headers 含む）
     * @return フォーマット済みテキスト
     */
    public static String formatSheetHead(List<CleanedSheet> sheets, int maxRows) {
        List<String> parts = new ArrayList<>();

        for (CleanedSheet sheet : sheets) {
            parts.add("=== Sheet: " + sheet.name() + " ===");

            List<List<String>> allRows = new ArrayList<>();
            if (sheet.headers() != null && !sheet.headers().isEmpty()) {
                allRows.add(sheet.headers());
            }
            if (sheet.rows() != null) {
                allRows.addAll(sheet.rows());
            }

            int limit = Math.min(allRows.size(), maxRows);
            for (int rowIdx = 0; rowIdx < limit; rowIdx++) {
                List<String> row = allRows.get(rowIdx);
                List<String> tagged = new ArrayList<>();
                for (int ci = 0; ci < row.size(); ci++) {
                    String c = row.get(ci);
                    // Python の `if c:` と等価（None / 空文字を弾く）
                    if (c != null && !c.isEmpty()) {
                        tagged.add("[" + ci + "]" + c);
                    }
                }
                if (!tagged.isEmpty()) {
                    parts.add("[Row " + rowIdx + "] " + String.join("  ", tagged));
                }
            }
        }

        return String.join("\n", parts);
    }
}
