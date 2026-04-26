package de.devin.cbbees.gametest.dsl

import com.mojang.authlib.GameProfile
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.beehive.PortableBeeHive
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.logistics.ports.LogisticPortBlockEntity
import de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity
import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.TestJobPool
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.items.ItemHandlerHelper
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

/**
 * Scope for integration tests that require a beehive network,
 * structure scanning, and async job dispatch.
 */
@GameTestDsl
class BeeTestScope(
    val helper: GameTestHelper,
    val level: ServerLevel,
) {
    lateinit var scan: GameTestSetup.StructureScan
        private set
    lateinit var jobPool: TestJobPool
        private set

    // ── Structure Scanning ──

    /**
     * Scans the test structure using the given config.
     *
     * ```kotlin
     * val scan = scanStructure {
     *     target(Blocks.DIRT)
     *     expect(Items.DIRT, count = 27)
     * }
     * ```
     */
    fun scanStructure(config: GameTestSetup.ScanConfigBuilder.() -> Unit): GameTestSetup.StructureScan {
        val builder = GameTestSetup.ScanConfigBuilder()
        builder.config()
        scan = GameTestSetup.scanStructure(helper, level, builder.build())
        return scan
    }

    // ── Network Setup ──

    fun setupNetwork(
        scan: GameTestSetup.StructureScan = this.scan,
        beeCount: Int = 5,
    ): TestJobPool {
        val hive = scan.beehive
            ?: throw GameTestAssertException("No mechanical beehive found in structure")
        jobPool = GameTestSetup.setupBeehiveNetwork(hive, scan.ports, scan.transportPorts, level, beeCount)
        return jobPool
    }

    // ── Portable Beehive ──

    /**
     * Creates a fake player with a portable beehive backpack and registers
     * a [PortableBeeHive] in its own isolated network.
     *
     * @param pos where to place the player in the world
     * @param beeCount number of bees to stock in the backpack
     * @param joinExistingNetwork if true, adds the portable hive to the existing
     *   test network (from [setupNetwork]). If false, creates a new isolated network.
     * @return the [PortableBeeHive] for assertions
     */
    fun setupPortableBeehive(
        pos: BlockPos,
        beeCount: Int = 5,
        joinExistingNetwork: Boolean = false,
    ): PortableBeeHive {
        val profile = GameProfile(UUID.randomUUID(), "TestPlayer")
        val fakePlayer = FakePlayerFactory.get(level, profile)
        fakePlayer.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)

        // Equip portable beehive in chestplate slot with bees and honey
        val backpack = ItemStack(AllItems.PORTABLE_BEEHIVE.get())
        val backpackItem = backpack.item as PortableBeehiveItem
        val beeItem = ItemStack(AllItems.MECHANICAL_BEE.get())
        repeat(beeCount) { backpackItem.addBee(backpack, beeItem.copy()) }
        // Give honey fuel (1000 should be enough for tests)
        backpack.set(de.devin.cbbees.registry.AllDataComponents.HONEY_FUEL.get(), 1000)
        fakePlayer.inventory.armor[2] = backpack

        val portableHive = PortableBeeHive(fakePlayer)

        if (joinExistingNetwork && ::jobPool.isInitialized) {
            // Add to existing test network directly via jobPool reference
            val network = jobPool.network
            portableHive.networkId = network.id
            network.addComponent(portableHive)
            ServerBeeNetworkManager.rebuildIndexes()
        } else {
            // Create isolated portable network
            val networkId = ServerBeeNetworkManager.stableNetworkId(fakePlayer.uuid)
            portableHive.networkId = networkId
            val network = BeeNetwork(networkId)
            network.addComponent(portableHive)
            ServerBeeNetworkManager.addNetwork(network)
            // Only set jobPool if not already initialized (don't overwrite stationary network's pool)
            if (!::jobPool.isInitialized) {
                jobPool = TestJobPool(network)
            }
        }

        return portableHive
    }

    // ── Job Dispatch ──

    fun deconstruct(positions: List<BlockPos> = scan.targetPositions): BeeJob {
        if (positions.isEmpty()) {
            throw GameTestAssertException("No block positions to deconstruct")
        }
        return GameTestSetup.dispatchDeconstruction(positions, level, jobPool)
    }

    /**
     * Dispatches a construction job that places blocks at the given positions.
     */
    fun construct(placements: List<GameTestSetup.PlacementTask>): de.devin.cbbees.content.domain.job.BeeJob {
        if (placements.isEmpty()) {
            throw GameTestAssertException("No placement tasks")
        }
        return GameTestSetup.dispatchConstruction(placements, level, jobPool)
    }

    // ── Port Queries ──

    fun portsByPriority(): List<PortWithVault> {
        return scan.ports
            .sortedByDescending { it.priority }
            .map { PortWithVault(it, it.blockPos.below()) }
    }

    data class PortWithVault(
        val port: LogisticPortBlockEntity,
        val vaultPos: BlockPos,
    ) {
        val priority: Int get() = port.priority
    }

    // ── Transport Port Queries ──

    fun transportPortsByPriority(): List<TransportPortWithVault> {
        return scan.transportPorts
            .sortedByDescending { it.priority() }
            .map { TransportPortWithVault(it, it.blockPos.below()) }
    }

    fun providerPorts(): List<TransportPortWithVault> {
        return scan.transportPorts
            .filter { it.isValidProvider() }
            .map { TransportPortWithVault(it, it.blockPos.below()) }
    }

    fun requesterPorts(): List<TransportPortWithVault> {
        return scan.transportPorts
            .filter { it.isValidRequester() }
            .sortedByDescending { it.priority() }
            .map { TransportPortWithVault(it, it.blockPos.below()) }
    }

    data class TransportPortWithVault(
        val port: TransportPortBlockEntity,
        val vaultPos: BlockPos,
    ) {
        val priority: Int get() = port.priority()
    }

    /**
     * Inserts items into the inventory at the given position (e.g., a vault).
     */
    fun insertItems(pos: BlockPos, item: Item, count: Int) {
        val handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null)
            ?: throw GameTestAssertException("No item handler at $pos")
        val remainder = ItemHandlerHelper.insertItemStacked(handler, ItemStack(item, count), false)
        if (!remainder.isEmpty) {
            throw GameTestAssertException("Could not insert all items at $pos, ${remainder.count} remaining")
        }
    }

    // ── Async Success ──

    /**
     * Runs [block] every tick until all assertions pass or the test times out.
     * Auto-ticks the [jobPool] before each check.
     */
    fun awaitSuccess(block: AssertionScope.() -> Unit) {
        helper.succeedWhen {
            GameTestSetup.assertOrRetry {
                if (::jobPool.isInitialized) {
                    jobPool.tick(level.gameTime)
                }
                AssertionScope(level, if (::scan.isInitialized) scan else null).block()
            }
        }
    }
}

/**
 * Scope available inside [BeeTestScope.awaitSuccess] blocks.
 * Provides world-aware assertion helpers that retry on failure.
 */
@GameTestDsl
class AssertionScope(
    val level: ServerLevel,
    private val scan: GameTestSetup.StructureScan?,
) {

    fun assertAllAir(positions: List<BlockPos>) {
        for (pos in positions) {
            if (!level.getBlockState(pos).isAir) {
                throw GameTestAssertException("Block at $pos is not air: ${level.getBlockState(pos)}")
            }
        }
    }

    fun assertAllBlocks(positions: List<BlockPos>, label: String = "block", predicate: (BlockState) -> Boolean) {
        for (pos in positions) {
            val state = level.getBlockState(pos)
            if (!predicate(state)) {
                throw GameTestAssertException("$label failed at $pos: ${state.block}")
            }
        }
    }

    fun assertItemCount(positions: List<BlockPos>, item: Item, atLeast: Int) {
        var total = 0
        for (pos in positions) {
            total += GameTestSetup.countItem(level, pos, item)
        }
        if (total < atLeast) {
            throw GameTestAssertException(
                "Expected at least $atLeast ${item.description.string}, found $total"
            )
        }
    }

    fun assertItemCountAt(pos: BlockPos, item: Item, atLeast: Int) {
        val count = GameTestSetup.countItem(level, pos, item)
        if (count < atLeast) {
            throw GameTestAssertException(
                "Expected at least $atLeast ${item.description.string} at $pos, found $count"
            )
        }
    }

    fun assertNoItems(pos: BlockPos, item: Item) {
        val count = GameTestSetup.countItem(level, pos, item)
        if (count > 0) {
            throw GameTestAssertException(
                "Expected no ${item.description.string} at $pos, found $count"
            )
        }
    }

    /**
     * Asserts that all targets from the scan config have been removed (are now air)
     * and all expected items are present in inventories.
     *
     * Shorthand for the most common deconstruction assertion pattern.
     */
    fun assertTargetsDeconstructed() {
        val s = scan ?: throw GameTestAssertException("No scan available — call scanStructure first")
        assertAllAir(s.targetPositions)
        for (expected in s.config.expectedItems) {
            assertItemCount(s.allPositions, expected.item, atLeast = expected.count)
        }
    }

    fun check(condition: Boolean, message: () -> String) {
        if (!condition) throw GameTestAssertException(message())
    }
}

/**
 * Runs an integration test with beehive network support.
 *
 * ```kotlin
 * @GameTest(template = "cbbees:gametest/cbbees_deconstruct", timeoutTicks = 6000, setupTicks = 20)
 * @JvmStatic
 * fun myTest(helper: GameTestHelper) = helper.beeTest {
 *     val scan = scanStructure {
 *         target(Blocks.DIRT)
 *         expect(Items.DIRT, count = 27)
 *     }
 *     setupNetwork(scan)
 *     deconstruct()
 *
 *     awaitSuccess {
 *         assertTargetsDeconstructed()
 *     }
 * }
 * ```
 */
inline fun GameTestHelper.beeTest(block: BeeTestScope.() -> Unit) {
    val level = this.level as ServerLevel
    BeeTestScope(this, level).block()
}
