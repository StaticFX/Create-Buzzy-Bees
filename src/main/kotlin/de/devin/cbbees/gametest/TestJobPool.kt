package de.devin.cbbees.gametest

import de.devin.cbbees.content.domain.BatchDispatcher
import de.devin.cbbees.content.domain.JobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import java.util.UUID

/**
 * Test-scoped [JobPool] that dispatches batches only to the networks its test set up.
 *
 * Uses the same [BatchDispatcher] as [de.devin.cbbees.content.domain.GlobalJobPool],
 * but only over its own jobs and networks, which keeps parallel gametest structures apart.
 *
 * [tick] re-dispatches pending batches so bees that complete their task and
 * return to the hive can pick up the next batch.
 */
class TestJobPool(initialNetwork: BeeNetwork) : JobPool {

    private val known = linkedMapOf(initialNetwork.id to initialNetwork)

    val network: BeeNetwork get() = resolve(known.keys.first())

    private val networks: List<BeeNetwork> get() = known.keys.map(::resolve)

    private val jobs = mutableListOf<BeeJob>()

    fun addNetwork(network: BeeNetwork) {
        known[network.id] = network
    }

    private fun resolve(id: UUID): BeeNetwork =
        ServerBeeNetworkManager.getNetwork(id)?.also { known[id] = it } ?: known.getValue(id)

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
        BatchDispatcher.dispatch(jobs, networks, Int.MAX_VALUE)
    }
}
