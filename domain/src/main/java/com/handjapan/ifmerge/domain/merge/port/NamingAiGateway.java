package com.handjapan.ifmerge.domain.merge.port;

import com.handjapan.ifmerge.domain.analysis.model.InterfaceRecord;
import com.handjapan.ifmerge.domain.merge.model.IFInfo;

import java.util.List;
import java.util.Map;

/**
 * AI による命名・IF 概要生成のゲートウェイ。
 * 原 Python：IFmerge/ebs_merger/ai_generator.py:236-459。
 */
public interface NamingAiGateway {

    /**
     * 全 IF の概要と代表項目名を一括生成。
     *
     * @param ifInfos     IF 情報リスト（doc_number / item_count 取得用）
     * @param recordsByIf ifName → records（表名 / 項目名 取得用）
     */
    Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos,
                                              Map<String, List<InterfaceRecord>> recordsByIf);

    /**
     * 合并組の新 IF 名を生成。
     *
     * @param groupMemberIfNames グループメンバー IF 名
     * @param recordsByIf        ifName → records（メンバー各々の表名取得用）
     */
    String generateMergedIfName(List<String> groupMemberIfNames,
                                Map<String, List<InterfaceRecord>> recordsByIf);

    record IFSummary(String summary, String representativeItem) {
    }
}
