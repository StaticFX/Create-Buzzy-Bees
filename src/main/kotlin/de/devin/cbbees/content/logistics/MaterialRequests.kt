package de.devin.cbbees.content.logistics

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.MissingMaterials
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.logistics.storage.AutocraftRequester
import de.devin.cbbees.content.logistics.waggle.WagglePortBlockEntity
import de.devin.cbbees.util.ServerSide
import java.util.UUID

/**
 * Orders the materials bee jobs are waiting for.
 *
 * [de.devin.cbbees.content.domain.BatchDispatcher] reports each job whose batches it had to skip
 * because the network lacks items. Only those jobs are looked at, through the network's Waggle
 * Ports and [AutocraftRequester], so nothing runs while no job is short of anything. A shortage
 * that lasts is reported again on every dispatch, which also re-orders requests that timed out.
 */
@ServerSide
object MaterialRequests {

    private class Reports(val network: BeeNetwork) {
        val jobs = HashSet<BeeJob>()
    }

    private val reports = HashMap<UUID, Reports>()
    private val nextWaggleTick = HashMap<UUID, Long>()
    private val nextCraftTick = HashMap<UUID, Long>()

    fun report(network: BeeNetwork, job: BeeJob) {
        reports.getOrPut(network.id) { Reports(network) }.jobs += job
    }

    fun tick(gameTime: Long) {
        if (reports.isEmpty()) return
        for ((id, report) in reports) {
            val waggle = gameTime >= (nextWaggleTick[id] ?: 0L)
            val craft = CBBeesConfig.autocraftMissing.get() && gameTime >= (nextCraftTick[id] ?: 0L)
            if (!waggle && !craft) continue

            val network = report.network
            val missing = MissingMaterials.forJobs(network, report.jobs)
            if (waggle) {
                nextWaggleTick[id] = gameTime + CBBeesConfig.waggleRequestInterval.get()
                network.components.filterIsInstance<WagglePortBlockEntity>().forEach { it.requestMissing(missing, gameTime) }
            }
            if (craft) {
                nextCraftTick[id] = gameTime + CBBeesConfig.autocraftInterval.get()
                AutocraftRequester.request(network, missing, gameTime)
            }
        }
        reports.clear()
    }

    fun clear() {
        reports.clear()
        nextWaggleTick.clear()
        nextCraftTick.clear()
        AutocraftRequester.clear()
    }
}
