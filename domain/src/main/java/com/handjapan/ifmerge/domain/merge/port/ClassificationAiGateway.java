package com.handjapan.ifmerge.domain.merge.port;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;

import java.util.List;
import java.util.Map;

/**
 * IF を SAP モジュール × 業務シナリオで分類する AI ゲートウェイ。
 * 原 Python：IFmerge/ebs_merger/ai_classifier.py:26-169。
 */
public interface ClassificationAiGateway {

    /**
     * @param ifInfos     IF 情報リスト（fieldPairs / itemCount 取得用）
     * @param recordsByIf ifName → records の Map（表名 / 項目名 取得用）
     * @return categoryName → CategoryInfo のマップ。失敗時は全 IF を「その他_未分類」に配置するフォールバック実装も可。
     */
    Map<String, CategoryInfo> classify(List<IFInfo> ifInfos,
                                       Map<String, List<InterfaceRecord>> recordsByIf);

    record CategoryInfo(
            String module,
            String scenario,
            String description,
            List<String> ifNames
    ) {
    }
}
