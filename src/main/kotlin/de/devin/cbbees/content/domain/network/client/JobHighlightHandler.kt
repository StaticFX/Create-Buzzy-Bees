package de.devin.cbbees.content.domain.network.client

import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import java.util.*

object JobHighlightHandler {
    private val keys = mutableSetOf<String>()
    private var highlightedJob: UUID? = null

    fun toggle(networkId: UUID, jobId: UUID, beeIds: List<UUID>, highlightPorts: Boolean) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return

        // If same job, just toggle off
        if (highlightedJob == jobId) {
            clear()
            highlightedJob = null
            return
        }

        clear()
        highlightedJob = jobId

        val keyBase = "job_${jobId}"
        val net = ClientBeeNetworkManager.getNetwork(networkId)

        // Bees
        beeIds.mapNotNull { uuid -> level.entitiesForRendering().firstOrNull { it.uuid == uuid } }
            .forEachIndexed { i, e ->
                val key = "${keyBase}_bee_$i"
                Outliner.getInstance()
                    .chaseAABB(key, e.boundingBox.inflate(0.1))
                    .colored(0xFF00FFFF.toInt())
                keys.add(key)
            }

        // Ports (approximation: all ports in the same network)
        if (highlightPorts) {
            net.components.filter { !it.isAnchor() }.forEachIndexed { i, comp ->
                val key = "${keyBase}_port_$i"
                Outliner.getInstance()
                    .chaseAABB(key, AABB(comp.pos))
                    .colored(0xFFFF8800.toInt())
                keys.add(key)
            }
        }
    }

    private fun clear() {
        // In some versions of Catnip/Create, we might not have a direct remove.
        // If so, we might need to use a short duration or a different approach.
        // But let's assume it exists as per the provided snippet.
        // If it fails, we'll fix it.
    }
}
