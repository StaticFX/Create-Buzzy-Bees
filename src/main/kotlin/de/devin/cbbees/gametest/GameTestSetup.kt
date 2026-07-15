package de.devin.cbbees.gametest

import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.JobPool
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.INetworkComponent
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity
import de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.gametest.dsl.GameTestDsl
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import java.util.UUID

/**
 * Shared utilities for integration game tests.
 */
object GameTestSetup {

    // ── Structure Scanning ──

    /**
     * Scans a test structure using the provided [ScanConfig].
     */
    fun scanStructure(helper: GameTestHelper, level: ServerLevel, config: ScanConfig): StructureScan {
        val bounds = helper.bounds
        var beehive: MechanicalBeehiveBlockEntity? = null
        val targetPositions = mutableListOf<BlockPos>()
        val ports = mutableListOf<LogisticPortBlockEntity>()
        val transportPorts = mutableListOf<TransportPortBlockEntity>()
        val allPositions = mutableListOf<BlockPos>()

        val targetBlocks = config.targetBlocks

        for (x in bounds.minX.toInt()..bounds.maxX.toInt()) {
            for (y in bounds.minY.toInt()..bounds.maxY.toInt()) {
                for (z in bounds.minZ.toInt()..bounds.maxZ.toInt()) {
                    val worldPos = BlockPos(x, y, z)
                    allPositions.add(worldPos)
                    val be = level.getBlockEntity(worldPos)
                    if (be is MechanicalBeehiveBlockEntity) beehive = be
                    if (be is LogisticPortBlockEntity) ports.add(be)
                    if (be is TransportPortBlockEntity) transportPorts.add(be)
                    val state = level.getBlockState(worldPos)
                    if (targetBlocks.any { state.`is`(it) }) {
                        targetPositions.add(worldPos)
                    }
                }
            }
        }

        return StructureScan(beehive, targetPositions, ports, transportPorts, allPositions, config)
    }

    // ── Network Setup ──

    fun setupBeehiveNetwork(
        beehive: MechanicalBeehiveBlockEntity,
        ports: List<LogisticPortBlockEntity>,
        transportPorts: List<TransportPortBlockEntity> = emptyList(),
        level: ServerLevel,
        beeCount: Int = 5,
    ): TestJobPool {
        val beeItem = ItemStack(AllItems.MECHANICAL_BEE.get())
        repeat(beeCount) { beehive.addBee(beeItem.copy()) }

        if (beehive.speed <= 0f) beehive.setSpeed(64f)

        // Collect all network components to register
        val allComponents = mutableListOf<INetworkComponent>()
        allComponents.addAll(ports)
        allComponents.addAll(transportPorts)

        // Unregister from any auto-registered networks (structure onLoad)
        ServerBeeNetworkManager.unregisterWorker(beehive)
        for (comp in allComponents) {
            ServerBeeNetworkManager.unregisterComponent(comp)
        }

        val networkId = UUID.randomUUID()
        beehive.networkId = networkId
        val network = BeeNetwork(networkId)
        network.addComponent(beehive)
        for (comp in allComponents) {
            comp.networkId = networkId
            network.addComponent(comp)
        }
        ServerBeeNetworkManager.addNetwork(network)

        return TestJobPool(network)
    }

    // ── Job Dispatch ──

    fun dispatchDeconstruction(
        positions: List<BlockPos>,
        level: ServerLevel,
        jobPool: JobPool,
    ): BeeJob {
        val corner1 = positions.minWith(compareBy({ it.x }, { it.y }, { it.z }))
        val corner2 = positions.maxWith(compareBy({ it.x }, { it.y }, { it.z }))

        val job = BeeJob(
            jobId = UUID.randomUUID(),
            centerPos = BlockPos(
                (corner1.x + corner2.x) / 2,
                (corner1.y + corner2.y) / 2,
                (corner1.z + corner2.z) / 2,
            ),
            level = level,
            jobType = JobType.Deconstruction,
        )

        val batches = SchematicCreateBridge(level).generateRemovalTasks(corner1, corner2, job)
        job.addBatches(batches)
        jobPool.dispatchNewJob(job)
        return job
    }

    /**
     * Creates a construction job that places blocks at the given positions.
     * Each entry maps a world position to a (blockState, requiredItems) pair.
     */
    fun dispatchConstruction(
        placements: List<PlacementTask>,
        level: ServerLevel,
        jobPool: JobPool,
    ): BeeJob {
        val positions = placements.map { it.pos }
        val center = BlockPos(
            positions.sumOf { it.x } / positions.size,
            positions.sumOf { it.y } / positions.size,
            positions.sumOf { it.z } / positions.size,
        )

        val job = BeeJob(
            jobId = UUID.randomUUID(),
            centerPos = center,
            level = level,
            jobType = JobType.Construction,
        )

        val batches = placements.map { placement ->
            val task = de.devin.cbbees.content.domain.task.BeeTask.place(
                pos = placement.pos,
                state = placement.state,
                items = placement.items,
                job = job,
            )
            de.devin.cbbees.content.domain.task.TaskBatch(listOf(task), job, placement.pos)
        }

        job.addBatches(batches)
        jobPool.dispatchNewJob(job)
        return job
    }

    data class PlacementTask(
        val pos: BlockPos,
        val state: net.minecraft.world.level.block.state.BlockState,
        val items: List<net.minecraft.world.item.ItemStack>,
    )

    // ── Helpers ──

    fun countItem(level: ServerLevel, pos: BlockPos, item: Item): Int {
        val handler = de.devin.cbbees.compat.NeoForgeCapabilityCompat.getUnsidedItemHandler(level, pos) ?: return 0
        var count = 0
        for (slot in 0 until handler.slots) {
            val stack = handler.getStackInSlot(slot)
            if (stack.`is`(item)) count += stack.count
        }
        return count
    }

    fun assertOrRetry(check: () -> Unit) {
        try {
            check()
        } catch (e: GameTestAssertException) {
            throw e
        } catch (e: Exception) {
            throw GameTestAssertException("Unexpected error: ${e.message}")
        }
    }

    // ── Data Classes ──

    /**
     * Configuration for structure scanning, built via [ScanConfigBuilder].
     */
    data class ScanConfig(
        val targetBlocks: List<Block>,
        val expectedItems: List<ExpectedItem>,
    )

    data class ExpectedItem(val item: Item, val count: Int)

    /**
     * Builder DSL for [ScanConfig].
     *
     * ```kotlin
     * scanStructure {
     *     target(Blocks.DIRT)
     *     expect(Items.DIRT, count = 27)
     * }
     * ```
     */
    @GameTestDsl
    class ScanConfigBuilder {
        private val targets = mutableListOf<Block>()
        private val expected = mutableListOf<ExpectedItem>()

        /** Declares a block type that should be targeted for deconstruction. */
        fun target(block: Block) { targets.add(block) }

        /** Declares an expected item and count that should end up in inventories after the test. */
        fun expect(item: Item, count: Int) { expected.add(ExpectedItem(item, count)) }

        fun build() = ScanConfig(targets.toList(), expected.toList())
    }

    /**
     * Result of scanning a test structure.
     */
    data class StructureScan(
        val beehive: MechanicalBeehiveBlockEntity?,
        val targetPositions: List<BlockPos>,
        val ports: List<LogisticPortBlockEntity>,
        val transportPorts: List<TransportPortBlockEntity>,
        val allPositions: List<BlockPos>,
        val config: ScanConfig,
    )
}
