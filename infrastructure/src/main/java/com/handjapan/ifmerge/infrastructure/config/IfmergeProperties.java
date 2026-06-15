package com.handjapan.ifmerge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务相关的可调参数。
 *
 * <p>从 application.yaml 的 {@code ifmerge.analysis.*} / {@code ifmerge.merge.*} /
 * {@code ifmerge.job.*} 加载，并支持环境变量覆盖
 * （如 {@code IFMERGE_ANALYSIS_PHASE1HEADROWS}、{@code IFMERGE_MERGE_DEFAULTTHRESHOLD}）。
 *
 * <p>{@code ifmerge.ai.*} 由 {@link SapAiCoreProperties} 单独承载；
 * {@code ifmerge.job.ttl / cleanupInterval / runningTimeout} 仍由 {@code @Value} 直接读取，
 * 本类只负责此前未绑定的 analysis / merge / job.maxConcurrent。
 */
@ConfigurationProperties(prefix = "ifmerge")
public record IfmergeProperties(
        Analysis analysis,
        Merge merge,
        Job job
) {

    public IfmergeProperties {
        if (analysis == null) analysis = Analysis.defaults();
        if (merge == null) merge = Merge.defaults();
        if (job == null) job = Job.defaults();
    }

    public record Analysis(Integer phase1HeadRows, Integer maxChunkRows) {
        public static Analysis defaults() {
            return new Analysis(30, 100);
        }

        public int phase1HeadRowsOrDefault() {
            return phase1HeadRows != null ? phase1HeadRows : 30;
        }

        public int maxChunkRowsOrDefault() {
            return maxChunkRows != null ? maxChunkRows : 100;
        }
    }

    public record Merge(Double defaultThreshold, String defaultMode) {
        public static Merge defaults() {
            return new Merge(0.80, "max");
        }

        public double thresholdOrDefault() {
            return defaultThreshold != null ? defaultThreshold : 0.80;
        }

        public String modeOrDefault() {
            return defaultMode != null && !defaultMode.isBlank() ? defaultMode : "max";
        }
    }

    public record Job(Integer maxConcurrent) {
        public static Job defaults() {
            return new Job(10);
        }

        public int maxConcurrentOrDefault() {
            return maxConcurrent != null ? maxConcurrent : 10;
        }
    }
}
