package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.domain.analysis.model.CleanedSheet;
import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.analysis.port.AnalysisAiGateway;
import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import com.handjapan.ifmerge.domain.analysis.service.Phase1PromptBuilder;
import com.handjapan.ifmerge.domain.analysis.service.Phase2PromptBuilder;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;
import com.handjapan.ifmerge.domain.merge.port.ClassificationAiGateway;
import com.handjapan.ifmerge.domain.merge.port.NamingAiGateway;
import com.handjapan.ifmerge.domain.merge.service.MergePromptBuilders;
import com.handjapan.ifmerge.infrastructure.config.SapAiCoreProperties;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAP AI Core 経由の Claude 呼び出しクライアント（本番実装）。
 *
 * <p>有効化条件: {@code ifmerge.ai.mock=false}。
 *
 * <p>原 Python 対応:
 * <ul>
 *   <li>{@code IFmerge_1/analyzer/sap_client.py}</li>
 *   <li>{@code IFmerge_1/analyzer/ai_analyzer.py}</li>
 *   <li>{@code IFmerge/ebs_merger/ai_generator.py}（classify/naming はまだ簡易実装）</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "ifmerge.ai.mock", havingValue = "false")
public class SapAiCoreClient implements AnalysisAiGateway, ClassificationAiGateway, NamingAiGateway {

    private static final Logger log = LoggerFactory.getLogger(SapAiCoreClient.class);

    private final SapAiCoreProperties props;
    private final SapAiCoreTokenProvider tokenProvider;
    private final DeploymentResolver deploymentResolver;
    private final Phase1PromptBuilder phase1Builder;
    private final Phase2PromptBuilder phase2Builder;
    private final MergePromptBuilders mergePromptBuilders;
    private final RestClient http;

    public SapAiCoreClient(SapAiCoreProperties props,
                           SapAiCoreTokenProvider tokenProvider,
                           DeploymentResolver deploymentResolver,
                           PromptRepository promptRepository) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.deploymentResolver = deploymentResolver;
        this.phase1Builder = new Phase1PromptBuilder(promptRepository);
        this.phase2Builder = new Phase2PromptBuilder(promptRepository);
        this.mergePromptBuilders = new MergePromptBuilders(promptRepository);
        this.http = RestClient.builder().build();
    }

    // ============================================================
    // AnalysisAiGateway — Phase 1
    // ============================================================

    @Override
    @Retry(name = "sap-ai-core")
    public Phase1Result analyzePhase1(String fileName,
                                      List<CleanedSheet> sheets,
                                      int phase1HeadRows) {
        log.info("Phase1 開始: fileName={}, sheets={}, headRows={}",
                fileName, sheets.size(), phase1HeadRows);

        String prompt = phase1Builder.build(fileName, sheets, phase1HeadRows);
        Map<String, Object> response = converse(
                prompt,
                List.of(ToolSchemas.extractDocMeta()),
                props.analysisTemperatureOrDefault(),
                props.analysisMaxTokensOrDefault()
        );
        logUsage("phase1", response);

        Phase1Result result = ResponseParser.toPhase1Result(response);
        log.info("Phase1 完了: doc={}, if={}, dataSheets={}",
                result.documentNumber(), result.ifName(), result.dataSheets().size());
        return result;
    }

    // ============================================================
    // AnalysisAiGateway — Phase 2
    // ============================================================

    @Override
    @Retry(name = "sap-ai-core")
    public List<InterfaceRecord> analyzePhase2(String fileName,
                                               String docNumber,
                                               String ifName,
                                               List<List<String>> chunk,
                                               ColumnMapping columnMapping) {
        log.info("Phase2 開始: fileName={}, chunkRows={}", fileName, chunk.size());

        String prompt = phase2Builder.build(fileName, docNumber, ifName, chunk, columnMapping);
        Map<String, Object> response = converse(
                prompt,
                List.of(ToolSchemas.extractInterfaceInfo()),
                props.analysisTemperatureOrDefault(),
                props.analysisMaxTokensOrDefault()
        );
        logUsage("phase2", response);

        List<InterfaceRecord> records = ResponseParser.toInterfaceRecords(response, docNumber, ifName);
        log.info("Phase2 完了: {} records", records.size());
        return records;
    }

    // ============================================================
    // ClassificationAiGateway
    // ============================================================

    @Override
    @Retry(name = "sap-ai-core")
    public Map<String, CategoryInfo> classify(List<IFInfo> ifInfos,
                                              Map<String, List<InterfaceRecord>> recordsByIf) {
        log.info("Classify 開始: {} IFs", ifInfos.size());

        if (ifInfos.isEmpty()) {
            return new LinkedHashMap<>();
        }

        try {
            String prompt = mergePromptBuilders.buildClassifyPrompt(ifInfos, recordsByIf);
            Map<String, Object> response = converse(
                    prompt,
                    List.of(ToolSchemas.classifyInterfaces()),
                    props.generationTemperatureOrDefault(),
                    props.generationMaxTokensOrDefault()
            );
            logUsage("classify", response);

            Map<String, CategoryInfo> result = ResponseParser.toClassifyCategories(response);
            log.info("Classify 完了: {} categories", result.size());

            // フォールバック：AI が分類を返さない場合は全 IF を「その他_未分類」へ
            if (result.isEmpty()) {
                log.warn("Classify: AI 返却が空、フォールバック適用");
                List<String> names = ifInfos.stream().map(IFInfo::ifName).toList();
                result.put("その他_未分類", new CategoryInfo(
                        "その他", "未分類", "AI 分類が空のためフォールバック", names));
            }
            return result;

        } catch (Exception e) {
            log.error("Classify 失敗、フォールバック適用: {}", e.getMessage());
            Map<String, CategoryInfo> fallback = new LinkedHashMap<>();
            List<String> names = ifInfos.stream().map(IFInfo::ifName).toList();
            fallback.put("その他_未分類", new CategoryInfo(
                    "その他", "未分類", "Classify 失敗フォールバック", names));
            return fallback;
        }
    }

    // ============================================================
    // NamingAiGateway — generateAllIfInfo
    // ============================================================

    @Override
    @Retry(name = "sap-ai-core")
    public Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos,
                                                     Map<String, List<InterfaceRecord>> recordsByIf) {
        log.info("GenerateAllIfInfo 開始: {} IFs", ifInfos.size());

        if (ifInfos.isEmpty()) {
            return new HashMap<>();
        }

        try {
            String prompt = mergePromptBuilders.buildGenerateAllIfInfoPrompt(ifInfos, recordsByIf);
            Map<String, Object> response = converse(
                    prompt,
                    List.of(ToolSchemas.generateAllIfInfo()),
                    props.generationTemperatureOrDefault(),
                    props.generationMaxTokensOrDefault()
            );
            logUsage("generateAllIfInfo", response);

            Map<String, IFSummary> summaries = ResponseParser.toIfSummaries(response);
            log.info("GenerateAllIfInfo 完了: {} 件", summaries.size());

            // 未返却の IF には fallback で representativeItem だけ埋める
            for (IFInfo info : ifInfos) {
                summaries.putIfAbsent(info.ifName(),
                        new IFSummary("", info.representativeItem()));
            }
            return summaries;

        } catch (Exception e) {
            log.error("GenerateAllIfInfo 失敗、空フォールバック: {}", e.getMessage());
            Map<String, IFSummary> result = new HashMap<>();
            for (IFInfo info : ifInfos) {
                result.put(info.ifName(), new IFSummary("", info.representativeItem()));
            }
            return result;
        }
    }

    // ============================================================
    // NamingAiGateway — generateMergedIfName
    // ============================================================

    @Override
    @Retry(name = "sap-ai-core")
    public String generateMergedIfName(List<String> groupMemberIfNames,
                                        Map<String, List<InterfaceRecord>> recordsByIf) {
        if (groupMemberIfNames == null || groupMemberIfNames.isEmpty()) {
            return "";
        }
        if (groupMemberIfNames.size() == 1) {
            return groupMemberIfNames.get(0);
        }

        log.info("GenerateMergedIfName 開始: {} members", groupMemberIfNames.size());

        try {
            String prompt = mergePromptBuilders.buildMergedNamePrompt(groupMemberIfNames, recordsByIf);
            Map<String, Object> response = converse(
                    prompt,
                    List.of(ToolSchemas.generateMergedName()),
                    props.generationTemperatureOrDefault(),
                    props.generationMaxTokensOrDefault()
            );
            logUsage("generateMergedIfName", response);

            String merged = ResponseParser.toMergedName(response);
            if (merged == null || merged.isBlank()) {
                log.warn("GenerateMergedIfName: AI 返却が空、underscore 連結にフォールバック");
                return String.join("_", groupMemberIfNames);
            }
            log.info("GenerateMergedIfName 完了: {}", merged);
            return merged;

        } catch (Exception e) {
            log.error("GenerateMergedIfName 失敗、underscore 連結フォールバック: {}", e.getMessage());
            return String.join("_", groupMemberIfNames);
        }
    }

    // ============================================================
    // Converse API 调用核心
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> converse(String prompt,
                                          List<Map<String, Object>> tools,
                                          double temperature,
                                          int maxTokens) {
        String deploymentId = deploymentResolver.resolve();
        String url = props.baseUrl().replaceAll("/+$", "")
                + "/inference/deployments/" + deploymentId + "/converse";
        String token = tokenProvider.getToken();

        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of("type", "text", "text", prompt))
                )),
                "toolConfig", Map.of(
                        "tools", tools,
                        "toolChoice", Map.of("any", Map.of())
                ),
                "inferenceConfig", Map.of(
                        "maxTokens", maxTokens,
                        "temperature", temperature
                )
        );

        try {
            Map<String, Object> response = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("AI-Resource-Group", props.resourceGroupOrDefault())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new IllegalStateException("Converse API 返回空响应");
            }
            return response;
        } catch (Exception e) {
            log.error("Converse API 调用失败: url={}", url, e);
            throw new RuntimeException("SAP AI Core Converse API 失敗: " + e.getMessage(), e);
        }
    }

    private void logUsage(String phase, Map<String, Object> response) {
        Object usage = response.get("usage");
        if (usage instanceof Map<?, ?> m) {
            log.info("[TOKEN] phase={} in={} out={}",
                    phase, m.get("inputTokens"), m.get("outputTokens"));
        }
    }
}
