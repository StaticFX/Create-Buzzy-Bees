package de.devin.cbbees.gametest

import de.devin.cbbees.content.domain.JobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TaskStatus

/**
 * Test-scoped [JobPool] that dispatches all batches to a single known network.
 *
 * Unlike [de.devin.cbbees.content.domain.GlobalJobPool] which scans all networks
 * and picks the closest one, this implementation guarantees isolation between
 * parallel gametest structures.
 *
 * [tick] re-dispatches pending batches so bees that complete their task and
 * return to the hive can pick up the next batch.
 */
class TestJobPool(val network: BeeNetwork) : JobPool {

    private val jobs = mutableListOf<BeeJob>()

    override fun dispatchNewJob(job: BeeJob) {
        jobs.add(job)
        dispatchPending()
    }

    override fun getAllJobs(): List<BeeJob> = jobs

    override fun tick(gameTime: Long) {
        jobs.removeAll { it.status == JobStatus.COMPLETED || it.status == JobStatus.CANCELLED }
        dispatchPending()
    }

    private fun dispatchPending() {
        for (job in jobs) {
            if (job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) continue
            for (batch in job.batches) {
                if (batch.status != TaskStatus.PENDING) continue
                if (!batch.canRetry()) continue
                if (!job.isPhaseReady(batch.phase)) continue
                batch.assignedNetworkId = network.id
                network.dispatchBatch(batch)
            }
        }
    }
}
