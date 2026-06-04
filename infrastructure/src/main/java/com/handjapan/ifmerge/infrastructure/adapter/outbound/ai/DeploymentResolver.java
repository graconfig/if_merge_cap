package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.infrastructure.config.SapAiCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * SAP AI Core deployment_id 解析。
 *
 * <p>解析顺序（原 Python ai_generator.py:_resolve_deployment_id 等价）：
 * <ol>
 *   <li>{@code ifmerge.ai.deployment-id} 非空 → 直接用</li>
 *   <li>{@code ifmerge.ai.model-name} 非空 → 调 {@code /lm/deployments?status=RUNNING} 找匹配</li>
 *   <li>都为空 → 抛异常</li>
 * </ol>
 */
@Component
public class DeploymentResolver {

    private static final Logger log = LoggerFactory.getLogger(DeploymentResolver.class);

    private final SapAiCoreProperties props;
    private final SapAiCoreTokenProvider tokenProvider;
    private final RestClient http;
    private volatile String cachedDeploymentId;

    public DeploymentResolver(SapAiCoreProperties props,
                              SapAiCoreTokenProvider tokenProvider) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.http = RestClient.builder().build();
    }

    public synchronized String resolve() {
        if (cachedDeploymentId != null && !cachedDeploymentId.isBlank()) {
            return cachedDeploymentId;
        }
        // ① 固定 deployment-id 优先
        if (props.hasFixedDeployment()) {
            cachedDeploymentId = props.deploymentId();
            log.info("使用配置的 deployment-id: {}", cachedDeploymentId);
            return cachedDeploymentId;
        }
        // ② 按 model-name 动态解析
        if (props.hasModelName()) {
            cachedDeploymentId = resolveByModelName();
            return cachedDeploymentId;
        }
        throw new IllegalStateException(
                "未配置 ifmerge.ai.deployment-id 也未配置 ifmerge.ai.model-name");
    }

    @SuppressWarnings("unchecked")
    private String resolveByModelName() {
        String url = props.baseUrl().replaceAll("/+$", "") + "/lm/deployments?status=RUNNING";
        String token = tokenProvider.getToken();

        Map<?, ?> body;
        try {
            body = http.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("AI-Resource-Group", props.resourceGroupOrDefault())
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Deployment 一覧の取得に失敗: " + e.getMessage(), e);
        }
        if (body == null) {
            throw new IllegalStateException("Deployment 一覧が空");
        }
        List<Map<String, Object>> resources =
                (List<Map<String, Object>>) body.get("resources");
        if (resources == null || resources.isEmpty()) {
            throw new IllegalStateException("RUNNING 状态的 deployment 一个都没有");
        }

        String target = props.modelName().toLowerCase();
        for (Map<String, Object> dep : resources) {
            Map<String, Object> details = (Map<String, Object>) dep.getOrDefault("details", Map.of());
            Map<String, Object> resourcesNode = (Map<String, Object>) details.getOrDefault("resources", Map.of());
            Map<String, Object> backendDetails = (Map<String, Object>) resourcesNode.getOrDefault("backendDetails", Map.of());
            Map<String, Object> model = (Map<String, Object>) backendDetails.getOrDefault("model", Map.of());

            String name = String.valueOf(model.getOrDefault("name", "")).toLowerCase();
            String version = String.valueOf(model.getOrDefault("version", "")).toLowerCase();
            String configName = String.valueOf(dep.getOrDefault("configurationName", "")).toLowerCase();

            if (name.contains(target) || version.contains(target) || configName.contains(target)) {
                String id = String.valueOf(dep.get("id"));
                log.info("Model '{}' → deployment-id={} (name={}, configurationName={})",
                        props.modelName(), id, name, configName);
                return id;
            }
        }
        throw new IllegalStateException(
                "model-name '" + props.modelName() + "' 没有匹配的 RUNNING deployment");
    }
}
