package com.handjapan.ifmerge.infrastructure.adapter.inbound;

import com.handjapan.ifmerge.application.analysis.AnalyzeDocumentCommand;
import com.handjapan.ifmerge.application.analysis.AnalyzeDocumentUseCase;
import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobManager;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AnalysisService の CAP ハンドラ。
 *
 * TODO:
 *  - actions: analyze, getJob, getResult
 *  - CAP の cds-services-spring-boot に依存し、@On アノテーションでアクションを受ける
 *  - リクエスト DTO → AnalyzeDocumentCommand に変換
 *  - レスポンス：Job レコードを CDS の Job type にマップ
 *
 * 参考：DESIGN_CAP.md 第 5.1 節 analysis-service.cds
 */
// TODO: CAP CDS 統合時に @Component と @ServiceName を有効化する。
//       現状は REST Controller (AnalysisController) で代替している。
// @Component
// @ServiceName("AnalysisService")
public class AnalysisServiceHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceHandler.class);

    private final AnalyzeDocumentUseCase useCase;
    private final JobManager jobManager;

    public AnalysisServiceHandler(AnalyzeDocumentUseCase useCase, JobManager jobManager) {
        this.useCase = useCase;
        this.jobManager = jobManager;
    }

    @On(event = "analyze")
    public void onAnalyze(/* AnalyzeContext context */) {
        // TODO: context から fileName / sheets / options を取り出し、
        //       AnalyzeDocumentCommand を組み立てて useCase.submit(...) を呼ぶ。
        //       戻り値の Job を CDS の Job type にマップして context.setResult(...) で返す。
        log.info("[stub] AnalysisService.analyze called");
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @On(event = "getJob")
    public void onGetJob(/* GetJobContext context */) {
        // TODO: jobManager.findById(id) → CDS Job type に変換
        log.info("[stub] AnalysisService.getJob called");
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @On(event = "getResult")
    public void onGetResult(/* GetResultContext context */) {
        // TODO: jobManager.findById(id) の result（AnalysisResult）を返す
        log.info("[stub] AnalysisService.getResult called");
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
