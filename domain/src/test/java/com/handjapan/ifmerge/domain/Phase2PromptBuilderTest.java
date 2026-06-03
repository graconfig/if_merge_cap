package com.handjapan.ifmerge.domain;

import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.analysis.service.Phase2PromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase2PromptBuilder の列フィルタリング挙動が原 Python
 * {@code ai_analyzer.py:333-363（_filter_columns + _format_data_rows）}
 * と一致することを検証する。
 */
class Phase2PromptBuilderTest {

    // ─────────────────────────────────────────────────────────
    // resolveColumnIndices: ColumnMapping → ソート済み一意な ≥0 列
    // ─────────────────────────────────────────────────────────

    @Test
    void resolveColumnIndices_dropsNegative_dedups_andSorts() {
        // colTableName=5, colTableId=2, colItemId=3, colDigit=-1
        var cm = new AnalysisAiGateway.ColumnMapping(5, 2, 3, -1);

        List<Integer> result = Phase2PromptBuilder.resolveColumnIndices(cm);

        // -1 除外、ソート → [2, 3, 5]
        assertThat(result).containsExactly(2, 3, 5);
    }

    @Test
    void resolveColumnIndices_dedupsDuplicateColumns() {
        // 同じ列が複数役割に割り当たるケース（table_id と item_id が同列など）
        var cm = new AnalysisAiGateway.ColumnMapping(1, 2, 2, 4);

        List<Integer> result = Phase2PromptBuilder.resolveColumnIndices(cm);

        assertThat(result).containsExactly(1, 2, 4);
    }

    @Test
    void resolveColumnIndices_allNegative_returnsEmpty() {
        var cm = new AnalysisAiGateway.ColumnMapping(-1, -1, -1, -1);

        assertThat(Phase2PromptBuilder.resolveColumnIndices(cm)).isEmpty();
    }

    @Test
    void resolveColumnIndices_null_returnsEmpty() {
        assertThat(Phase2PromptBuilder.resolveColumnIndices(null)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────
    // formatChunk(chunk, colOffset): 元の列番号を保持して絞り込む
    // ─────────────────────────────────────────────────────────

    @Test
    void formatChunkWithOffset_keepsOnlySelectedColumns_withOriginalColumnNumbers() {
        // 6 列の行：[0]No [1]name [2]id [3]item [4]noise [5]digit
        List<List<String>> chunk = List.of(
                List.of("1", "受注ヘッダ", "OE_HDR", "ORDER_NUMBER", "ゴミ", "10"),
                List.of("2", "受注明細", "OE_LINE", "LINE_ID", "捨てる", "5")
        );
        // table_name=1, table_id=2, item_id=3, digit=5（4 は除外される）
        List<Integer> colOffset = List.of(1, 2, 3, 5);

        String result = Phase2PromptBuilder.formatChunk(chunk, colOffset);

        // [4]ゴミ / [0]No は出力されず、タグは元の列番号
        assertThat(result).isEqualTo(String.join("\n",
                "[1]受注ヘッダ  [2]OE_HDR  [3]ORDER_NUMBER  [5]10",
                "[1]受注明細  [2]OE_LINE  [3]LINE_ID  [5]5"
        ));
    }

    @Test
    void formatChunkWithOffset_skipsEmptyCells() {
        // 選択列の値が空ならそのタグは出ない（Python: if cell）
        List<List<String>> chunk = List.of(
                Arrays.asList("x", "", "ITEM", "noise", "")
        );
        List<Integer> colOffset = List.of(1, 2, 4);

        String result = Phase2PromptBuilder.formatChunk(chunk, colOffset);

        // [1]="" → skip、[2]=ITEM、[4]="" → skip
        assertThat(result).isEqualTo("[2]ITEM");
    }

    @Test
    void formatChunkWithOffset_skipsAllEmptyRow() {
        // 選択列がすべて空の行は 1 行も出力しない（Python: if tagged）
        List<List<String>> chunk = List.of(
                Arrays.asList("keep", "", ""),     // 選択列 [1][2] が空 → 行ごとスキップ
                Arrays.asList("keep", "A", "B")
        );
        List<Integer> colOffset = List.of(1, 2);

        String result = Phase2PromptBuilder.formatChunk(chunk, colOffset);

        assertThat(result).isEqualTo("[1]A  [2]B");
    }

    @Test
    void formatChunkWithOffset_outOfRangeColumn_treatedAsEmpty() {
        // 列番号が行の長さを超える場合は空扱い（Python: row[i] if i < len(row) else ''）
        List<List<String>> chunk = List.of(
                List.of("a", "b")   // index 0,1 のみ
        );
        List<Integer> colOffset = List.of(1, 5);   // 5 は範囲外

        String result = Phase2PromptBuilder.formatChunk(chunk, colOffset);

        assertThat(result).isEqualTo("[1]b");
    }

    @Test
    void formatChunkWithOffset_emptyOffset_fallsBackToAllColumns() {
        // colOffset が空 → 全列を自然な列番号で出力（Python: col_offset=None）
        List<List<String>> chunk = List.of(
                List.of("a", "b", "c")
        );

        String result = Phase2PromptBuilder.formatChunk(chunk, List.of());

        assertThat(result).isEqualTo("[0]a  [1]b  [2]c");
    }

    // ─────────────────────────────────────────────────────────
    // 既存の formatChunk(chunk)（全列）の回帰確認
    // ─────────────────────────────────────────────────────────

    @Test
    void formatChunk_allColumns_unchanged() {
        List<List<String>> chunk = List.of(
                Arrays.asList("a", "", "c")
        );

        assertThat(Phase2PromptBuilder.formatChunk(chunk)).isEqualTo("[0]a  [2]c");
    }

    // ─────────────────────────────────────────────────────────
    // build(): 絞り込んだ chunk_text がテンプレートに渡る
    // ─────────────────────────────────────────────────────────

    @Test
    void build_appliesColumnFilteringToChunkText() {
        PromptRepoStub repo = new PromptRepoStub();
        Phase2PromptBuilder builder = new Phase2PromptBuilder(repo);

        List<List<String>> chunk = List.of(
                List.of("1", "受注ヘッダ", "OE_HDR", "ORDER_NUMBER", "ゴミ", "10")
        );
        var cm = new AnalysisAiGateway.ColumnMapping(1, 2, 3, 5);

        String result = builder.build("F.xlsx", "DOC-1", "IF-A", chunk, cm);

        // chunk_text は絞り込み済み（[0]No と [4]ゴミ は含まれない）
        assertThat(result).contains("[1]受注ヘッダ  [2]OE_HDR  [3]ORDER_NUMBER  [5]10");
        assertThat(result).doesNotContain("ゴミ");
        assertThat(result).doesNotContain("[0]1");
        // 列マッピング変数も渡る
        assertThat(result).contains("col_item_id=3");
    }

    @Test
    void build_noColumnsIdentified_sendsAllColumns() {
        PromptRepoStub repo = new PromptRepoStub();
        Phase2PromptBuilder builder = new Phase2PromptBuilder(repo);

        List<List<String>> chunk = List.of(List.of("a", "b", "c"));
        var cm = new AnalysisAiGateway.ColumnMapping(-1, -1, -1, -1);

        String result = builder.build("F.xlsx", "DOC-1", "IF-A", chunk, cm);

        assertThat(result).contains("[0]a  [1]b  [2]c");
    }

    /** {chunk_text} と {col_item_id} を埋め込む簡易テンプレート。 */
    private static final class PromptRepoStub
            implements com.handjapan.ifmerge.domain.analysis.port.PromptRepository {
        @Override
        public Optional<String> render(String key, java.util.Map<String, Object> vars) {
            if (!"phase2".equals(key)) return Optional.empty();
            return Optional.of(
                    "data:\n" + vars.get("chunk_text")
                            + "\ncol_item_id=" + vars.get("col_item_id"));
        }
    }
}
