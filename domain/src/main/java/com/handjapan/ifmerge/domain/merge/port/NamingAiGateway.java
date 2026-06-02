package com.handjapan.ifmerge.domain.merge.port;

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
     */
    Map<String, IFSummary> generateAllIfInfo(List<IFInfo> ifInfos);

    /**
     * 合并組の新 IF 名を生成。
     */
    String generateMergedIfName(List<String> groupMemberIfNames, List<IFInfo> ifInfos);

    record IFSummary(String summary, String representativeItem) {
    }
}
