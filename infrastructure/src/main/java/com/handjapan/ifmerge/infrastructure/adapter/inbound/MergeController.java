package com.handjapan.ifmerge.infrastructure.adapter.inbound;

import com.handjapan.ifmerge.application.job.Job;
import com.handjapan.ifmerge.application.job.JobManager;
import com.handjapan.ifmerge.application.job.JobStatus;
import com.handjapan.ifmerge.application.merge.MergeInterfacesCommand;
import com.handjapan.ifmerge.application.merge.MergeInterfacesUseCase;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.ErrorResponseDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.JobDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.MergeRequestDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.dto.MergeResultDto;
import com.handjapan.ifmerge.infrastructure.adapter.inbound.mapper.MergeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for IFmerge（合并サービス）。
 */
@RestController
@RequestMapping("/api/v1")
public class MergeController {

    private static final Logger log = LoggerFactory.getLogger(MergeController.class);

    private final MergeInterfacesUseCase useCase;
    private final JobManager jobManager;
    private final MergeMapper mapper;

    public MergeController(MergeInterfacesUseCase useCase,
                           JobManager jobManager,
                           MergeMapper mapper) {
        this.useCase = useCase;
        this.jobManager = jobManager;
        this.mapper = mapper;
    }

    @PostMapping("/merge")
    public ResponseEntity<JobDto> merge(@RequestBody MergeRequestDto request) {
        log.info("POST /api/v1/merge: records={}",
                request.records() == null ? 0 : request.records().size());

        if (request.records() == null || request.records().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        MergeInterfacesCommand cmd = mapper.toCommand(request);
        Job job = useCase.submit(cmd);

        // Use AnalysisMapper-style toJobDto inline; or duplicate. Here let MergeMapper provide it.
        return ResponseEntity.status(HttpStatus.CREATED).body(toJobDto(job));
    }

    @GetMapping("/merges/{id}")
    public ResponseEntity<?> getJob(@PathVariable UUID id) {
        return jobManager.findById(id)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok(toJobDto(job)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ErrorResponseDto.of("JOB_NOT_FOUND",
                                "Merge job " + id + " not found or expired")));
    }

    @GetMapping("/merges/{id}/result")
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

        MergeResultDto dto = mapper.toResultDto(job);
        return ResponseEntity.ok(dto);
    }

    private JobDto toJobDto(Job job) {
        return new JobDto(
                job.id(),
                job.type().name(),
                job.status().name(),
                job.progress(),
                job.phase(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.error() == null ? null : new JobDto.ErrorInfoDto(
                        job.error().code(),
                        job.error().message(),
                        job.error().occurredAt()
                )
        );
    }
}
