package com.handjapan.ifmerge.infrastructure.adapter.inbound;

import com.handjapan.ifmerge.application.job.JobManager;
import com.handjapan.ifmerge.application.merge.MergeInterfacesUseCase;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * MergeService の CAP ハンドラ。
 *
 * TODO:
 *  - actions: merge, getJob, getResult
 *  - リクエスト DTO → MergeInterfacesCommand に変換
 *  - レスポンス：Job / MergeResult を CDS type にマップ
 */
// TODO: CAP CDS 統合時に @Component と @ServiceName を有効化する。
//       現状は REST Controller (MergeController) で代替している。
// @Component
// @ServiceName("MergeService")
public class MergeServiceHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(MergeServiceHandler.class);

    private final MergeInterfacesUseCase useCase;
    private final JobManager jobManager;

    public MergeServiceHandler(MergeInterfacesUseCase useCase, JobManager jobManager) {
        this.useCase = useCase;
        this.jobManager = jobManager;
    }

    @On(event = "merge")
    public void onMerge(/* MergeContext context */) {
        // TODO: records[], options を取り出し、useCase.submit(...) を呼ぶ
        log.info("[stub] MergeService.merge called");
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @On(event = "getJob")
    public void onGetJob(/* GetJobContext context */) {
        log.info("[stub] MergeService.getJob called");
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @On(event = "getResult")
    public void onGetResult(/* GetResultContext context */) {
        log.info("[stub] MergeService.getResult called");
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
