package com.handjapan.ifmerge.domain.merge.service;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.shared.exception.MergeException;

import java.util.*;

/**
 * Merge フェーズの 3 種類のプロンプトビルダー。
 *
 * <p>原 Python 対応：
 * <ul>
 *   <li>classify  → {@code ai_classifier.py:56-90}</li>
 *   <li>allIfInfo → {@code ai_generator.py:266-301}</li>
 *   <li>mergedName → {@code ai_generator.py:404-425}</li>
 * </ul>
 *
 * <p>{@code if_info_block} の構造は 3 種類とも異なる（原 Python と一致させる）。
 */
public class MergePromptBuilders {

    private final PromptRepository promptRepository;

    public MergePromptBuilders(PromptRepository promptRepository) {
        this.promptRepository = Objects.requireNonNull(promptRepository);
    }

    // =====================================================================
    // ① classify_interfaces
    // =====================================================================

    /**
     * 分類用 prompt を組み立てる。
     * 各 IF：tables 最大 5 件、items 最大 5 件をブロックに含める。
     */
    public String buildClassifyPrompt(List<IFInfo> ifInfos,
                                       Map<String, List<InterfaceRecord>> recordsByIf) {
        StringBuilder block = new StringBuilder();
        int idx = 1;
        for (IFInfo info : ifInfos) {
            List<InterfaceRecord> records = recordsByIf.getOrDefault(info.ifName(), List.of());

            List<String> tables = records.stream()
                    .map(InterfaceRecord::ebsTableName)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .limit(5)
                    .toList();
            List<String> sampleItems = records.stream()
                    .map(InterfaceRecord::itemName)
                    .filter(s -> s != null && !s.isBlank())
                    .limit(5)
                    .toList();

            block.append("\n").append(idx++).append(". IF名: ").append(info.ifName()).append("\n")
                    .append("   文書管理番号: ").append(info.docNumber()).append("\n")
                    .append("   関連テーブル: ").append(String.join(", ", tables)).append("\n")
                    .append("   項目総数: ").append(info.itemCount()).append("\n")
                    .append("   サンプル項目: ").append(String.join(", ", sampleItems)).append("\n");
        }

        Map<String, Object> vars = Map.of(
                "count", ifInfos.size(),
                "if_info_block", block.toString()
        );
        return promptRepository.render("classify_interfaces", vars)
                .orElseThrow(() -> new MergeException(
                        "PROMPT_TEMPLATE_MISSING",
                        "prompts.yaml に classify_interfaces テンプレートが見つかりません"));
    }

    // =====================================================================
    // ② generate_all_if_info
    // =====================================================================

    /**
     * 概要 + 代表項目生成 prompt を組み立てる。
     * 各 IF：tables 最大 3 件、参考項目 最大 20 件、代表項目数 = item_count × 20%。
     */
    public String buildGenerateAllIfInfoPrompt(List<IFInfo> ifInfos,
                                                Map<String, List<InterfaceRecord>> recordsByIf) {
        StringBuilder block = new StringBuilder();
        int idx = 1;
        for (IFInfo info : ifInfos) {
            List<InterfaceRecord> records = recordsByIf.getOrDefault(info.ifName(), List.of());

            List<String> tables = records.stream()
                    .map(InterfaceRecord::ebsTableName)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
            List<String> items = records.stream()
                    .map(InterfaceRecord::itemName)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
            List<String> sampleItems = items.stream().limit(20).toList();
            int top20 = Math.max(1, (int) Math.round(info.itemCount() * 0.2));

            block.append("\n").append(idx++).append(". IF名: ").append(info.ifName()).append("\n")
                    .append("   文書管理番号: ").append(info.docNumber()).append("\n")
                    .append("   関連テーブル: ").append(String.join(", ", tables)).append("\n")
                    .append("   項目総数: ").append(info.itemCount()).append("\n")
                    .append("   選択すべき代表項目数: ").append(top20).append("個（項目総数の約20%）\n")
                    .append("   参考項目（最初の").append(sampleItems.size()).append("個）: ")
                    .append(String.join(", ", sampleItems)).append("\n");
        }

        Map<String, Object> vars = Map.of(
                "count", ifInfos.size(),
                "if_info_block", block.toString()
        );
        return promptRepository.render("generate_all_if_info", vars)
                .orElseThrow(() -> new MergeException(
                        "PROMPT_TEMPLATE_MISSING",
                        "prompts.yaml に generate_all_if_info テンプレートが見つかりません"));
    }

    // =====================================================================
    // ③ generate_merged_if_name
    // =====================================================================

    /**
     * 合并組の新名前生成 prompt を組み立てる。
     * 各メンバー：tables 最大 3 件を 1 行で示す。
     */
    public String buildMergedNamePrompt(List<String> groupMemberIfNames,
                                         Map<String, List<InterfaceRecord>> recordsByIf) {
        StringBuilder block = new StringBuilder();
        for (String name : groupMemberIfNames) {
            List<InterfaceRecord> records = recordsByIf.getOrDefault(name, List.of());
            List<String> tables = records.stream()
                    .map(InterfaceRecord::ebsTableName)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();
            if (block.length() > 0) block.append("\n");
            block.append("- ").append(name)
                    .append("（関連テーブル：").append(String.join(", ", tables)).append("）");
        }

        Map<String, Object> vars = Map.of("if_info_block", block.toString());
        return promptRepository.render("generate_merged_if_name", vars)
                .orElseThrow(() -> new MergeException(
                        "PROMPT_TEMPLATE_MISSING",
                        "prompts.yaml に generate_merged_if_name テンプレートが見つかりません"));
    }
}
