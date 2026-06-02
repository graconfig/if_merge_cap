package com.handjapan.ifmerge.application.analysis;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;

import java.util.List;

/**
 * Analyze ユースケースの入力コマンド。
 */
public record AnalyzeDocumentCommand(
        String fileName,
        List<CleanedSheet> sheets,
        Options options
) {

    public record Options(int phase1HeadRows, int maxChunkRows) {
        public static Options defaults() {
            return new Options(30, 100);
        }
    }
}
