package com.example.commentanalyzer.data.local.entity

enum class ImportStatus {
    CREATED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
}

enum class ProcessingState {
    IMPORTED,
    LOCAL_REJECTED,
    READY_JEV,
    JEV_IN_PROGRESS,
    JEV_REJECTED,
    READY_GLM,
    GLM_IN_PROGRESS,
    COMPLETED,
    RETRY_WAIT,
    MANUAL_REVIEW,
    FAILED_PERMANENT,
    CANCELLED
}

enum class ProcessingStage {
    INGESTION,
    LOCAL_FILTER,
    JEV_GATE,
    GLM_ANALYSIS,
    DONE
}

enum class BatchStatus {
    PLANNED,
    CLAIMED,
    IN_FLIGHT,
    SUCCEEDED,
    PARTIAL,
    RETRY_WAIT,
    SPLIT,
    FAILED_PERMANENT,
    CANCELLED
}

enum class BatchItemStatus {
    PLANNED,
    SENT,
    SUCCEEDED,
    RETRY_WAIT,
    FAILED_PERMANENT,
    MOVED_TO_CHILD_BATCH
}

enum class TimestampPrecision {
    SECOND,
    MILLISECOND,
    UNKNOWN
}

enum class ResultStage {
    LOCAL_FILTER,
    JEV_GATE,
    GLM_ANALYSIS
}
