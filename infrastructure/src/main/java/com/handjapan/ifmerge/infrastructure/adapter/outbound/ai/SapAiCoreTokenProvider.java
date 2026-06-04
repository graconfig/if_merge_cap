package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.infrastructure.config.SapAiCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth2 client_credentials Token 取得 + 缓存。
 *
 * <p>原 Python 对応：{@code sap_client.py:_get_access_token} +
 * {@code ai_generator.py:_get_access_token}。
 */
@Component
public class SapAiCoreTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(SapAiCoreTokenProvider.class);
    private static final int REFRESH_BUFFER_SECONDS = 60;

    private final SapAiCoreProperties props;
    private final RestClient http;
    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public SapAiCoreTokenProvider(SapAiCoreProperties props) {
        this.props = props;
        this.http = RestClient.builder().build();
    }

    public synchronized String getToken() {
        Instant now = Instant.now();
        if (cachedToken != null
                && now.isBefore(expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
            return cachedToken;
        }
        refresh();
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }

    private void refresh() {
        String tokenUrl = props.authUrl().replaceAll("/+$", "") + "/oauth/token";
        String basic = Base64.getEncoder().encodeToString(
                (props.clientId() + ":" + props.clientSecret()).getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        log.info("OAuth2 token 取得開始: {}", tokenUrl);
        Map<?, ?> body;
        try {
            body = http.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SAP AI Core OAuth2 token 取得失敗: " + e.getMessage(), e);
        }
        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("OAuth2 response 缺 access_token: " + body);
        }
        cachedToken = (String) body.get("access_token");
        int expiresIn = body.get("expires_in") instanceof Number n
                ? n.intValue() : 3600;
        expiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("OAuth2 token 取得成功，有効期 {} 秒", expiresIn);
    }
}
