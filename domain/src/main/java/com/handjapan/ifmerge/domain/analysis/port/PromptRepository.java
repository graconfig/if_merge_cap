package com.handjapan.ifmerge.domain.analysis.port;

import java.util.Map;
import java.util.Optional;

/**
 * プロンプトテンプレートリポジトリ。
 * 実装は prompts.yaml を読み込んで占位符を展開する（infrastructure 層）。
 */
public interface PromptRepository {

    /**
     * 指定されたキーのプロンプトテンプレートを取得し、占位符を展開する。
     *
     * @param key        プロンプトキー（例：phase1, phase2, classify_interfaces）
     * @param variables  テンプレート変数
     * @return 展開済みプロンプト、未定義なら empty
     */
    Optional<String> render(String key, Map<String, Object> variables);
}
