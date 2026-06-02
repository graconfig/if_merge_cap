package com.handjapan.ifmerge.infrastructure.adapter.outbound.ai;

import com.handjapan.ifmerge.domain.analysis.port.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * prompts.yaml を読み込んでプロンプトテンプレートを提供する。
 * 原 Python：IFmerge/ebs_merger/prompt_config.py と同じ書式。
 */
@Component
public class YamlPromptRepository implements PromptRepository {

    private static final Logger log = LoggerFactory.getLogger(YamlPromptRepository.class);

    private final Map<String, Object> templates;

    @SuppressWarnings("unchecked")
    public YamlPromptRepository(ResourceLoader resourceLoader,
                                @Value("${ifmerge.prompts.path:classpath:prompts.yaml}") String path) {
        Map<String, Object> loaded = Map.of();
        try {
            Resource res = resourceLoader.getResource(path);
            if (res.exists()) {
                try (InputStream in = res.getInputStream()) {
                    Object obj = new Yaml().load(in);
                    if (obj instanceof Map) {
                        loaded = (Map<String, Object>) obj;
                    }
                }
            } else {
                log.warn("prompts.yaml が見つかりません: {}", path);
            }
        } catch (IOException e) {
            log.warn("prompts.yaml の読み込みに失敗しました: {}", e.getMessage());
        }
        this.templates = loaded;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> render(String key, Map<String, Object> variables) {
        Object entry = templates.get(key);
        if (!(entry instanceof Map)) return Optional.empty();
        Object template = ((Map<String, Object>) entry).get("template");
        if (!(template instanceof String tmpl)) return Optional.empty();

        try {
            String rendered = tmpl;
            for (Map.Entry<String, Object> v : variables.entrySet()) {
                rendered = rendered.replace("{" + v.getKey() + "}", String.valueOf(v.getValue()));
            }
            return Optional.of(rendered);
        } catch (Exception e) {
            log.warn("prompt '{}' のフォーマットに失敗しました: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
