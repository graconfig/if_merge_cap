package com.handjapan.ifmerge.application.merge;

import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobManager;
import com.handjapan.ifmerge.application.job.JobType;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.*;
import com.handjapan.ifmerge.domain.merge.port.ClassificationAiGateway;
import com.handjapan.ifmerge.domain.merge.port.NamingAiGateway;
import com.handjapan.ifmerge.domain.merge.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 合并ユースケース。
 *
 * <p>原 Python {@code IFmerge/ebs_merger/cli.py:127-261} に対応する編排：
 * <ol>
 *   <li>records → IFInfo 集約</li>
 *   <li>AI 分類（モジュール × 業務シナリオ）</li>
 *   <li>module × scenario 毎に相似度計算 → Union-Find → グループ採番</li>
 *   <li>合并組毎に AI 命名 + 字段去重</li>
 *   <li>相似度マトリックス組立</li>
 * </ol>
 */
@Service
public class MergeInterfacesUseCase {

    private static final Logger log = LoggerFactory.getLogger(MergeInterfacesUseCase.class);

    private final JobManager jobManager;
    private final ClassificationAiGateway classificationGateway;
    private final NamingAiGateway namingGateway;
    private final TaskExecutor jobExecutor;

    // domain services（純 Java、Spring 管理外）
    private final IFAggregator aggregator = new IFAggregator();
    private final SimilarityCalculator similarityCalculator = new SimilarityCalculator();
    private final MergeGrouper grouper = new MergeGrouper();
    private final GroupingReasonBuilder reasonBuilder = new GroupingReasonBuilder();
    private final FieldDeduplicator deduplicator = new FieldDeduplicator();

    public MergeInterfacesUseCase(JobManager jobManager,
                                  ClassificationAiGateway classificationGateway,
                                  NamingAiGateway namingGateway,
                                  @Qualifier("jobExecutor") TaskExecutor jobExecutor) {
        this.jobManager = jobManager;
        this.classificationGateway = classificationGateway;
        this.namingGateway = namingGateway;
        this.jobExecutor = jobExecutor;
    }

    public Job submit(MergeInterfacesCommand cmd) {
        Job job = jobManager.create(JobType.MERGE);
        jobExecutor.execute(() -> runAsync(job.id(), cmd));
        return job;
    }

    public void runAsync(UUID jobId, MergeInterfacesCommand cmd) {
        try {
            jobManager.markRunning(jobId, "AGGREGATING");

            List<InterfaceRecord> records = cmd.records();
            double threshold = cmd.options().threshold();
            SimilarityMode mode = cmd.options().mode();

            // ────────── 1. records → IFInfo マップ + records by IF ──────────
            Map<String, IFInfo> ifMap = aggregator.aggregate(records);
            log.info("Job {}: 集約後 {} IFs", jobId, ifMap.size());

            // ifName → records の Map（prompt 構築用）
            Map<String, List<InterfaceRecord>> recordsByIf = records.stream()
                    .collect(Collectors.groupingBy(InterfaceRecord::ifName));

            jobManager.updateProgress(jobId, 10, "CLASSIFYING");

            // ────────── 2. AI 分類（モジュール × シナリオ） ──────────
            Map<String, ClassificationAiGateway.CategoryInfo> categories =
                    classificationGateway.classify(new ArrayList<>(ifMap.values()), recordsByIf);
            log.info("Job {}: 分類完了（{} categories）", jobId, categories.size());
            jobManager.updateProgress(jobId, 30, "GENERATING_IF_INFO");

            // ────────── 3. AI 概要 + 代表項目（バッチ生成） ──────────
            Map<String, NamingAiGateway.IFSummary> summaries =
                    namingGateway.generateAllIfInfo(new ArrayList<>(ifMap.values()), recordsByIf);
            jobManager.updateProgress(jobId, 50, "SIMILARITY");

            // ────────── 4. module × scenario 毎に相似度 → グループ ──────────
            GroupIdAllocator allocator = new GroupIdAllocator();
            List<MergeGroup> allGroups = new ArrayList<>();
            List<MergeResult.ModuleSimilarityMatrix> matrices = new ArrayList<>();

            // category を module でグルーピング
            Map<String, List<ClassificationAiGateway.CategoryInfo>> byModule = new LinkedHashMap<>();
            for (ClassificationAiGateway.CategoryInfo cat : categories.values()) {
                byModule.computeIfAbsent(cat.module(), k -> new ArrayList<>()).add(cat);
            }

            int totalScenarios = categories.size();
            int scenarioIdx = 0;
            for (Map.Entry<String, List<ClassificationAiGateway.CategoryInfo>> moduleEntry : byModule.entrySet()) {
                String module = moduleEntry.getKey();
                List<MergeResult.ScenarioMatrix> scenarioMatrices = new ArrayList<>();

                for (ClassificationAiGateway.CategoryInfo cat : moduleEntry.getValue()) {
                    scenarioIdx++;
                    int progress = 50 + (int) (40.0 * scenarioIdx / Math.max(1, totalScenarios));
                    jobManager.updateProgress(jobId, progress,
                            "MERGING_" + cat.module() + "_" + cat.scenario());

                    // この category 内の IF だけ抽出
                    Map<String, IFInfo> catIfMap = new LinkedHashMap<>();
                    for (String name : cat.ifNames()) {
                        IFInfo info = ifMap.get(name);
                        if (info != null) catIfMap.put(name, info);
                    }
                    if (catIfMap.isEmpty()) continue;

                    // 相似度（閾値内 + 全量）
                    List<SimilarityPair> pairsForGrouping =
                            similarityCalculator.buildMatrix(catIfMap.values(), threshold, mode);
                    List<SimilarityPair> allPairs =
                            similarityCalculator.buildFullMatrix(catIfMap.values(), mode);

                    // Union-Find
                    Map<String, List<String>> rawGroups =
                            grouper.groupSimilarIFs(catIfMap.keySet(), pairsForGrouping);

                    // グループ毎に MergeGroup 構築
                    for (List<String> rawMemberNames : rawGroups.values()) {
                        String groupingId = allocator.next(module);
                        String mergedIfName = namingGateway.generateMergedIfName(
                                rawMemberNames, recordsByIf);

                        // メンバーは IF 名でソート（原 result_generator.py:104 の
                        // sorted(if_dict.keys()) と整合、出力を安定化）
                        List<String> memberNames = new ArrayList<>(rawMemberNames);
                        Collections.sort(memberNames);

                        // メンバー毎の詳細（文書管理番号・項目数・IF概要・代表項目名・根拠）を構築。
                        // 原 result_generator.py:104-150 の OutputRow 構築に対応。
                        List<MergeMember> members = new ArrayList<>();
                        for (String name : memberNames) {
                            IFInfo info = catIfMap.get(name);
                            NamingAiGateway.IFSummary sum = summaries.get(name);

                            String ifSummary = sum != null && sum.summary() != null ? sum.summary() : "";
                            // 代表項目名：AI 結果（無ければ gateway 側で IFInfo フォールバック済み）
                            String repItem = sum != null ? sum.representativeItem()
                                    : (info != null ? info.representativeItem() : "");

                            // メンバー単位の根拠（原 result_generator.py:127-131：if_name 視点）
                            String reason = reasonBuilder.build(name, memberNames, pairsForGrouping);

                            members.add(new MergeMember(
                                    name,
                                    info != null ? info.docNumber() : "",
                                    info != null ? info.itemCount() : 0,
                                    ifSummary,
                                    splitRepresentativeItems(repItem),
                                    reason
                            ));
                        }

                        // 字段去重（メンバー全 IF の records から）
                        List<InterfaceRecord> memberRecords = records.stream()
                                .filter(r -> memberNames.contains(r.ifName()))
                                .toList();
                        Set<FieldPair> mergedFields = deduplicator.dedupe(memberRecords);

                        allGroups.add(new MergeGroup(
                                groupingId,
                                module,
                                cat.scenario(),
                                members,
                                mergedIfName,
                                mergedFields
                        ));
                    }

                    // マトリックス組立（このシナリオ分）
                    scenarioMatrices.add(buildScenarioMatrix(cat.scenario(), catIfMap, allPairs));
                }

                if (!scenarioMatrices.isEmpty()) {
                    matrices.add(new MergeResult.ModuleSimilarityMatrix(module, scenarioMatrices));
                }
            }

            jobManager.updateProgress(jobId, 95, "BUILDING_RESULT");

            // ────────── 5. サマリー組立 ──────────
            int mergeableGroups = (int) allGroups.stream().filter(MergeGroup::isMergeRequired).count();
            int savedCount = allGroups.stream()
                    .filter(MergeGroup::isMergeRequired)
                    .mapToInt(g -> g.memberIfNames().size() - 1)
                    .sum();
            Set<String> modules = allGroups.stream().map(MergeGroup::module)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            MergeResult.Summary summary = new MergeResult.Summary(
                    ifMap.size(),
                    allGroups.size(),
                    mergeableGroups,
                    savedCount,
                    modules
            );

            MergeResult result = new MergeResult(summary, allGroups, matrices);
            jobManager.markSucceeded(jobId, result);
            log.info("Job {}: 合并完了（{} groups, {} mergeable, {} saved）",
                    jobId, allGroups.size(), mergeableGroups, savedCount);

        } catch (Exception e) {
            log.error("Job {}: Merge 失敗", jobId, e);
            jobManager.markFailed(jobId, "MERGE_FAILED", e.getMessage());
        }
    }

    /**
     * 代表項目名（AI はカンマ区切りの 1 文字列で返す）を個別項目のリストに分解する。
     * 原 Python は文字列をそのままセルに書いていたが、本実装は List で保持し
     * GUI 側（grouping_writer.py）が ", " で再結合するため、表示は等価。
     */
    private static List<String> splitRepresentativeItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : raw.split("[,、，]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /**
     * 単一シナリオの相似度マトリックスを組み立てる。
     * 行/列は IF 名でソート（matrix_exporter.py:66 と一致）。
     */
    private MergeResult.ScenarioMatrix buildScenarioMatrix(String scenario,
                                                            Map<String, IFInfo> catIfMap,
                                                            List<SimilarityPair> allPairs) {
        List<String> ifNames = new ArrayList<>(catIfMap.keySet());
        Collections.sort(ifNames);
        List<String> docNumbers = ifNames.stream()
                .map(n -> catIfMap.get(n).docNumber())
                .toList();

        int n = ifNames.size();
        double[][] maxMatrix = new double[n][n];
        Map<String, Double> pairMap = new HashMap<>();
        for (SimilarityPair p : allPairs) {
            pairMap.put(p.if1Name() + "→" + p.if2Name(), p.similarity());
            pairMap.put(p.if2Name() + "→" + p.if1Name(), p.similarity());
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) maxMatrix[i][j] = 1.0;
                else maxMatrix[i][j] = pairMap.getOrDefault(ifNames.get(i) + "→" + ifNames.get(j), 0.0);
            }
        }

        // 方向別相似度（行 IF の分母）— UseCase からは IFInfo の field_pairs にアクセス可能
        MergeResult.DirectionalValue[][] dirMatrix = new MergeResult.DirectionalValue[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dirMatrix[i][j] = new MergeResult.DirectionalValue(1.0, 1.0);
                    continue;
                }
                IFInfo a = catIfMap.get(ifNames.get(i));
                IFInfo b = catIfMap.get(ifNames.get(j));
                Set<FieldPair> common = new HashSet<>(a.fieldPairs());
                common.retainAll(b.fieldPairs());
                double row2col = a.fieldPairs().isEmpty() ? 0.0
                        : (double) common.size() / a.fieldPairs().size();
                double col2row = b.fieldPairs().isEmpty() ? 0.0
                        : (double) common.size() / b.fieldPairs().size();
                dirMatrix[i][j] = new MergeResult.DirectionalValue(row2col, col2row);
            }
        }

        return new MergeResult.ScenarioMatrix(
                scenario,
                new MergeResult.Axis(ifNames, docNumbers),
                maxMatrix,
                dirMatrix
        );
    }
}
