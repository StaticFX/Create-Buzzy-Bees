package de.devin.cbbees.content.domain

import de.devin.cbbees.content.domain.job.BeeJob

/**
 * Abstraction for job dispatch and lifecycle management.
 *
 * Production code uses [GlobalJobPool] (singleton, cross-network dispatch).
 * Tests can substitute a scoped implementation that dispatches to a specific network.
 */
interface JobPool {
    fun dispatchNewJob(job: BeeJob)
    fun getAllJobs(): List<BeeJob>
    fun tick(gameTime: Long = 0L)
}
