package com.handjapan.ifmerge.application.merge;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.SimilarityMode;

import java.util.List;

/**
 * Merge ユースケースの入力コマンド。
 * GUI から渡される records JSON を直接受け取る（stateless）。
 */
public record MergeInterfacesCommand(
        List<InterfaceRecord> records,
        Options options
) {

    public record Options(double threshold, SimilarityMode mode) {
        public static Options defaults() {
            return new Options(0.80, SimilarityMode.MAX);
        }
    }
}
