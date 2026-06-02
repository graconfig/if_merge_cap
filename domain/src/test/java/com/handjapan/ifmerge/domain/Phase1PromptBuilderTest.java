package com.handjapan.ifmerge.domain;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.analysis.service.Phase1PromptBuilder;
import com.handjapan.ifmerge.domain.shared.exception.AnalysisException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase1PromptBuilder の挙動が原 Python {@code _format_sheet_head} と
 * 一致することを検証する。
 */
class Phase1PromptBuilderTest {

    @Test
    void formatSheetHead_basicCase_matchesPythonFormat() {
        // 原 Python の出力期待値（IFmerge_1/analyzer/ai_analyzer.py:318-330 と同等）
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet(
                        "エクスポート項目",
                        List.of("No", "EBSテーブル名", "EBSテーブルID", "項目ID"),
                        List.of(
                                List.of("1", "受注ヘッダ", "OE_ORDER_HEADERS_ALL", "ORDER_NUMBER"),
                                List.of("2", "受注ヘッダ", "OE_ORDER_HEADERS_ALL", "CUSTOMER_ID")
                        )
                )
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 30);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: エクスポート項目 ===",
                "[Row 0] [0]No  [1]EBSテーブル名  [2]EBSテーブルID  [3]項目ID",
                "[Row 1] [0]1  [1]受注ヘッダ  [2]OE_ORDER_HEADERS_ALL  [3]ORDER_NUMBER",
                "[Row 2] [0]2  [1]受注ヘッダ  [2]OE_ORDER_HEADERS_ALL  [3]CUSTOMER_ID"
        ));
    }

    @Test
    void formatSheetHead_skipsEmptyCells() {
        // Python: `if c` は空文字をスキップ → tagged に入らない
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet(
                        "TestSheet",
                        List.of("A", "", "C"),
                        List.of(
                                List.of("1", "", "3")
                        )
                )
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 30);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: TestSheet ===",
                "[Row 0] [0]A  [2]C",
                "[Row 1] [0]1  [2]3"
        ));
    }

    @Test
    void formatSheetHead_skipsAllEmptyRows() {
        // Python: `if tagged:` で全空行は出力されない
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet(
                        "TestSheet",
                        List.of("A"),
                        List.of(
                                List.of("", "", ""),    // 全空 → スキップ
                                List.of("data")
                        )
                )
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 30);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: TestSheet ===",
                "[Row 0] [0]A",
                "[Row 2] [0]data"     // Row 1 は全空でスキップ、Row 2 は維持
        ));
    }

    @Test
    void formatSheetHead_truncatesAtMaxRows() {
        // headers + 5 rows → max=3 で切り取り → 出力 3 行（headers 含む）
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet(
                        "TestSheet",
                        List.of("H"),
                        List.of(
                                List.of("r0"),
                                List.of("r1"),
                                List.of("r2"),
                                List.of("r3"),
                                List.of("r4")
                        )
                )
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 3);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: TestSheet ===",
                "[Row 0] [0]H",
                "[Row 1] [0]r0",
                "[Row 2] [0]r1"
        ));
    }

    @Test
    void formatSheetHead_multipleSheets() {
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet("Sheet1", List.of("a"), List.of()),
                new CleanedSheet("Sheet2", List.of("b"), List.of())
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 30);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: Sheet1 ===",
                "[Row 0] [0]a",
                "=== Sheet: Sheet2 ===",
                "[Row 0] [0]b"
        ));
    }

    @Test
    void formatSheetHead_emptyHeaders_usesRowsOnly() {
        // Python: `[sheet.headers] + sheet.rows if sheet.headers else sheet.rows`
        // headers が空の場合は rows のみで番号付け
        List<CleanedSheet> sheets = List.of(
                new CleanedSheet(
                        "NoHeader",
                        List.of(),
                        List.of(List.of("r0c0", "r0c1"))
                )
        );

        String result = Phase1PromptBuilder.formatSheetHead(sheets, 30);

        assertThat(result).isEqualTo(String.join("\n",
                "=== Sheet: NoHeader ===",
                "[Row 0] [0]r0c0  [1]r0c1"
        ));
    }

    @Test
    void build_rendersPromptWithVariables() {
        PromptRepository fakeRepo = (key, vars) -> {
            if (!"phase1".equals(key)) return Optional.empty();
            String template = "File: {file_name}\nSheets:\n{sheet_head_text}";
            String out = template
                    .replace("{file_name}", String.valueOf(vars.get("file_name")))
                    .replace("{sheet_head_text}", String.valueOf(vars.get("sheet_head_text")));
            return Optional.of(out);
        };

        Phase1PromptBuilder builder = new Phase1PromptBuilder(fakeRepo);

        List<CleanedSheet> sheets = List.of(
                new CleanedSheet("S", List.of("h"), List.of(List.of("v")))
        );

        String result = builder.build("BDN-EPD-OF-093.xlsx", sheets, 30);

        assertThat(result).contains("File: BDN-EPD-OF-093.xlsx");
        assertThat(result).contains("=== Sheet: S ===");
        assertThat(result).contains("[Row 0] [0]h");
        assertThat(result).contains("[Row 1] [0]v");
    }

    @Test
    void build_throwsWhenTemplateMissing() {
        PromptRepository emptyRepo = (key, vars) -> Optional.empty();
        Phase1PromptBuilder builder = new Phase1PromptBuilder(emptyRepo);

        assertThatThrownBy(() ->
                builder.build("file.xlsx",
                        List.of(new CleanedSheet("S", List.of(), List.of())),
                        30)
        )
                .isInstanceOf(AnalysisException.class)
                .hasMessageContaining("phase1");
    }

    @Test
    void build_rejectsNonPositiveHeadRows() {
        Phase1PromptBuilder builder = new Phase1PromptBuilder((key, vars) -> Optional.of("x"));

        assertThatThrownBy(() ->
                builder.build("f", List.of(), 0)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
