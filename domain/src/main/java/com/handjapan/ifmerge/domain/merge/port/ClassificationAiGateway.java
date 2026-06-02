package com.handjapan.ifmerge.domain.merge.port;

import com.handjapan.ifmerge.domain.merge.model.IFInfo;

import java.util.List;
import java.util.Map;

/**
 * IF を SAP モジュール × 業務シナリオで分類する AI ゲートウェイ。
 * 原 Python：IFmerge/ebs_merger/ai_classifier.py:26-169。
 */
public interface ClassificationAiGateway {

    /**
     * @param ifInfos IF 情報リスト
     * @return categoryName → CategoryInfo のマップ。失敗時は全 IF を「その他_未分類」に配置するフォールバック実装も可。
     */
    Map<String, CategoryInfo> classify(List<IFInfo> ifInfos);

    record CategoryInfo(
            String module,
            String scenario,
            String description,
            List<String> ifNames
    ) {
    }
}
