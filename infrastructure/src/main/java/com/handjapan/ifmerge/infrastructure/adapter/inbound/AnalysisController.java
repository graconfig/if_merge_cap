package com.handjapan.ifmerge.infrastructure.adapter.inbound;

import com.handjapan.ifmerge.application.analysis.AnalyzeDocumentCommand;
import com.handjapan.ifmerge.application.analysis.AnalyzeDocumentUseCase;
import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobManager;
import com.handjapan.ifmerge.application.job.JobStatus;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.AnalysisResultDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.AnalyzeRequestDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.ErrorResponseDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.JobDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.mapper.AnalysisMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for IFmerge_1（解析サービス）。
 *
 * <p>パス：
 * <ul>
 *   <li>POST /api/v1/analyze — 解析タスク投入</li>
 *   <li>GET /api/v1/analyses/{id} — Job 状態</li>
 *   <li>GET /api/v1/analyses/{id}/result — 完全な解析結果</li>
 * </ul>
 *
 * <p>このコントローラは CAP CDS handler の替わりに直接 Spring MVC で HTTP を処理する。
 * 将来的に CAP の OData インターフェイスに切替可能（DESIGN_CAP.md 参照）。
 */
@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final AnalyzeDocumentUseCase useCase;
    private final JobManager jobManager;
    private final AnalysisMapper mapper;

    public AnalysisController(AnalyzeDocumentUseCase useCase,
                              JobManager jobManager,
                              AnalysisMapper mapper) {
        this.useCase = useCase;
        this.jobManager = jobManager;
        this.mapper = mapper;
    }

    /**
     * 解析タスクを投入する。HTTP は即座に Job を返し、実際の処理は @Async で後台実行。
     */
    @PostMapping("/analyze")
    public ResponseEntity<JobDto> analyze(@RequestBody AnalyzeRequestDto request) {
        log.info("POST /api/v1/analyze: fileName={}, sheets={}",
                request.fileName(),
                request.sheets() == null ? 0 : request.sheets().size());

        // 簡易バリデーション
        if (request.fileName() == null || request.fileName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.sheets() == null || request.sheets().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        AnalyzeDocumentCommand cmd = mapper.toCommand(request);
        Job job = useCase.submit(cmd);

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toJobDto(job));
    }

    /**
     * Job 状態を返す。
     */
    @GetMapping("/analyses/{id}")
    public ResponseEntity<?> getJob(@PathVariable UUID id) {
        return jobManager.findById(id)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok(mapper.toJobDto(job)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ErrorResponseDto.of("JOB_NOT_FOUND",
                                "Analysis job " + id + " not found or expired")));
    }

    /**
     * Job 完了後の完全な解析結果を返す。
     * 完了していなければ 409 を返す。
     */
    @GetMapping("/analyses/{id}/result")
    public ResponseEntity<?> getResult(@PathVariable UUID id) {
        var jobOpt = jobManager.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorResponseDto.of("JOB_NOT_FOUND", "Job " + id + " not found"));
        }
        Job job = jobOpt.get();
        if (job.status() != JobStatus.SUCCEEDED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ErrorResponseDto.of("JOB_NOT_COMPLETED",
                            "Job is " + job.status() + ", not SUCCEEDED yet"));
        }

        AnalysisResultDto dto = mapper.toResultDto(job, null);
        return ResponseEntity.ok(dto);
    }
}
