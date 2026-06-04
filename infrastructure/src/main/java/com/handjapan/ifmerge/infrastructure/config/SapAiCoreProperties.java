package com.handjapan.ifmerge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SAP AI Core 接続 + LLM 推论参数。
 *
 * <p>从 application.yaml 的 {@code ifmerge.ai.*} 加载（env 方式）。
 */
@ConfigurationProperties(prefix = "ifmerge.ai")
public record SapAiCoreProperties(
        String authUrl,
        String clientId,
        String clientSecret,
        String baseUrl,
        String resourceGroup,
        String modelName,
        String deploymentId,
        Double analysisTemperature,
        Integer analysisMaxTokens,
        Double generationTemperature,
        Integer generationMaxTokens
) {

    public boolean hasFixedDeployment() {
        return deploymentId != null && !deploymentId.isBlank();
    }

    public boolean hasModelName() {
        return modelName != null && !modelName.isBlank();
    }

    public double analysisTemperatureOrDefault() {
        return analysisTemperature != null ? analysisTemperature : 0.3;
    }

    public int analysisMaxTokensOrDefault() {
        return analysisMaxTokens != null ? analysisMaxTokens : 16384;
    }

    public double generationTemperatureOrDefault() {
        return generationTemperature != null ? generationTemperature : 0.7;
    }

    public int generationMaxTokensOrDefault() {
        return generationMaxTokens != null ? generationMaxTokens : 8192;
    }

    public String resourceGroupOrDefault() {
        return resourceGroup != null && !resourceGroup.isBlank() ? resourceGroup : "default";
    }
}
