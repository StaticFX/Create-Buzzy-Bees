package de.devin.cbbees.content.domain.job

/**
 * Status of this job.
 */
enum class JobStatus {
    /** Job is actively being worked on */
    IN_PROGRESS,
    /** All tasks completed */
    COMPLETED,
    /** Job was cancelled */
    CANCELLED
}