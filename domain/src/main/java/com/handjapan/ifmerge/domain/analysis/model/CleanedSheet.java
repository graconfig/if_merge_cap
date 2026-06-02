package com.handjapan.ifmerge.domain.analysis.model;

import java.util.List;

/**
 * GUI から渡される清洗済みシートデータ。
 * 削除線・対角叉・空行は GUI 端で除去済み。
 */
public record CleanedSheet(
        String name,
        List<String> headers,
        List<List<String>> rows
) {
}
