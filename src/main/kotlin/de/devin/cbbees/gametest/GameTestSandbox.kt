package de.devin.cbbees.gametest

import de.devin.cbbees.content.domain.network.INetworkComponent
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.topology.DefaultAnchorTopology
import de.devin.cbbees.content.domain.network.topology.NetworkTopology
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestInfo
import net.minecraft.gametest.framework.GameTestListener
import net.minecraft.gametest.framework.GameTestRunner
import net.minecraft.gametest.framework.GameTestServer
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent

object GameTestSandbox : NetworkTopology by DefaultAnchorTopology {

    private const val MARGIN = 2.0

    private val regions = mutableMapOf<GameTestInfo, AABB>()

    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        if (event.server is GameTestServer) ServerBeeNetworkManager.setTopology(this)
    }

    fun enter(helper: GameTestHelper) {
        val info = helper.testInfo
        regions[info] = info.structureBounds.inflate(MARGIN, 0.0, MARGIN)
        info.addListener(object : GameTestListener {
            override fun testStructureLoaded(testInfo: GameTestInfo) {}
            override fun testPassed(test: GameTestInfo, runner: GameTestRunner) = leave(test)
            override fun testFailed(test: GameTestInfo, runner: GameTestRunner) = leave(test)
            override fun testAddedForRerun(oldTest: GameTestInfo, newTest: GameTestInfo, runner: GameTestRunner) {}
        })
    }

    private fun leave(info: GameTestInfo) {
        val region = regions.remove(info) ?: return
        ServerBeeNetworkManager.getNetworks()
            .filter { network -> network.components.any { region.containsColumn(it.pos) } }
            .flatMap { it.components }
            .forEach(ServerBeeNetworkManager::unregisterComponent)
    }

    private fun regionOf(pos: BlockPos): AABB? = regions.values.firstOrNull { it.containsColumn(pos) }

    private fun sameRegion(a: BlockPos, b: BlockPos): Boolean {
        val region = regionOf(a) ?: return false
        return region.containsColumn(b)
    }

    private fun AABB.containsColumn(pos: BlockPos) =
        pos.x + 0.5 in minX..maxX && pos.z + 0.5 in minZ..maxZ

    override fun canShareNetwork(a: INetworkComponent, b: INetworkComponent) = sameRegion(a.pos, b.pos)

    override fun canConnectAnchors(a: INetworkComponent, b: INetworkComponent) =
        sameRegion(a.pos, b.pos) && DefaultAnchorTopology.canConnectAnchors(a, b)

    override fun isOperationalRange(anchor: INetworkComponent, pos: BlockPos) =
        sameRegion(anchor.pos, pos) && DefaultAnchorTopology.isOperationalRange(anchor, pos)

    override fun isLogisticsRange(anchor: INetworkComponent, pos: BlockPos) =
        sameRegion(anchor.pos, pos) && DefaultAnchorTopology.isLogisticsRange(anchor, pos)
}
