package de.devin.cbbees.gametest.network

import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.IItemHandler
import java.util.UUID

/**
 * Unit tests for the network linking mechanism.
 *
 * Verifies that portable networks can link to block networks and see their ports,
 * that range filtering works correctly, and that links are one-directional.
 */
object NetworkLinkingTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    // ── Stub implementations ──

    /** Minimal BeeHive stub with configurable position and work range. */
    private class StubHive(
        override val pos: BlockPos,
        private val level: Level,
        private val workRange: Double = 64.0,
    ) : BeeHive {
        override val id: UUID = UUID.randomUUID()
        override val world: Level get() = level
        override var networkId: UUID = UUID.randomUUID()
        override fun getAvailableBeeCount(): Int = 0
        override fun hasBeeOfType(beeType: BeeType): Boolean = false
        override fun getBeeContext(): BeeContext = BeeContext()
        override fun consumeBee(): ItemStack = ItemStack.EMPTY
        override fun returnBee(item: ItemStack): Boolean = false
        override fun acceptBatch(batch: TaskBatch): Boolean = false
        override fun walkTarget(): WalkTarget = WalkTarget(pos, 1.0f, 0)
        override fun getActiveBeeCount(): Int = 0
        override fun notifyTaskCompleted(task: BeeTask, beeId: UUID): TaskBatch? = null
        override fun getWorkRange(): Double = workRange
    }

    /** Minimal LogisticsPort stub with configurable position and priority. */
    private class StubPort(
        override val pos: BlockPos,
        private val level: Level,
        private val portPriority: Int = 0,
        private val items: MutableList<ItemStack> = mutableListOf(),
    ) : LogisticsPort {
        override val id: UUID = UUID.randomUUID()
        override val world: Level get() = level
        override var networkId: UUID = UUID.randomUUID()
        override fun getPortType(): PortType = PortType.INSERT
        override fun getFilter(): ItemStack = ItemStack.EMPTY
        override fun isValidForPickup(): Boolean = true
        override fun isValidForDropOff(): Boolean = true
        override fun canBeeDropOffItem(bee: MechanicalBeeEntity): Boolean = true
        override fun getItemHandler(level: Level): IItemHandler? = null
        override fun walkTarget(): WalkTarget = WalkTarget(pos, 1.0f, 0)
        override fun priority(): Int = portPriority
        override fun hasItemStack(stack: ItemStack): Boolean =
            items.any { ItemStack.isSameItem(it, stack) && it.count >= stack.count }
        override fun removeItemStack(stack: ItemStack): Boolean = false
        override fun addItemStack(stack: ItemStack): ItemStack = stack
    }

    // ── Tests ──

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun no_links_sees_own_ports_only(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        val hive = StubHive(BlockPos(0, 0, 0), level)
        val ownPort = StubPort(BlockPos(1, 0, 0), level)

        val network = BeeNetwork()
        network.addComponent(hive)
        network.addComponent(ownPort)

        assertEquals(1, network.ports.size, "ports count")
        assertTrue(network.ports.contains(ownPort), "should contain own port")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun linking_makes_block_ports_visible(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        // Portable network with a hive
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 64.0)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)

        // Block network with a port nearby
        val blockHive = StubHive(BlockPos(5, 0, 0), level)
        val blockPort = StubPort(BlockPos(10, 0, 0), level)
        val blockNetwork = BeeNetwork()
        blockNetwork.addComponent(blockHive)
        blockNetwork.addComponent(blockPort)

        // Before linking: portable network sees no ports (hive doesn't implement LogisticsPort)
        assertEquals(0, portableNetwork.ports.size, "ports before link")

        // After linking: portable network sees block port
        portableNetwork.linkNetwork(blockNetwork)
        assertEquals(1, portableNetwork.ports.size, "ports after link")
        assertTrue(portableNetwork.ports.contains(blockPort), "should see block port")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun linked_ports_filtered_by_work_range(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        // Portable hive at origin with 32 block work range
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 32.0)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)

        // Block network with one near port and one far port
        val blockHive = StubHive(BlockPos(100, 0, 0), level)
        val nearPort = StubPort(BlockPos(20, 0, 0), level)  // within 32 blocks
        val farPort = StubPort(BlockPos(200, 0, 0), level)  // way outside 32 blocks
        val blockNetwork = BeeNetwork()
        blockNetwork.addComponent(blockHive)
        blockNetwork.addComponent(nearPort)
        blockNetwork.addComponent(farPort)

        portableNetwork.linkNetwork(blockNetwork)

        assertEquals(1, portableNetwork.ports.size, "only near port visible")
        assertTrue(portableNetwork.ports.contains(nearPort), "near port visible")
        assertFalse(portableNetwork.ports.contains(farPort), "far port not visible")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun unlinking_removes_block_ports(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 64.0)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)

        val blockHive = StubHive(BlockPos(5, 0, 0), level)
        val blockPort = StubPort(BlockPos(10, 0, 0), level)
        val blockNetwork = BeeNetwork()
        blockNetwork.addComponent(blockHive)
        blockNetwork.addComponent(blockPort)

        portableNetwork.linkNetwork(blockNetwork)
        assertEquals(1, portableNetwork.ports.size, "port visible after link")

        portableNetwork.unlinkNetwork(blockNetwork)
        assertEquals(0, portableNetwork.ports.size, "port gone after unlink")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun link_is_one_directional(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        // Portable network with hive that also implements LogisticsPort
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 64.0)
        val portablePort = StubPort(BlockPos(1, 0, 0), level)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)
        portableNetwork.addComponent(portablePort)

        // Block network with its own port
        val blockHive = StubHive(BlockPos(5, 0, 0), level)
        val blockPort = StubPort(BlockPos(10, 0, 0), level)
        val blockNetwork = BeeNetwork()
        blockNetwork.addComponent(blockHive)
        blockNetwork.addComponent(blockPort)

        // Link portable -> block (one-directional)
        portableNetwork.linkNetwork(blockNetwork)

        // Portable sees both: own port + block port
        assertEquals(2, portableNetwork.ports.size, "portable sees both ports")

        // Block network should NOT see portable port (no reverse link)
        assertEquals(1, blockNetwork.ports.size, "block sees only own port")
        assertTrue(blockNetwork.ports.contains(blockPort), "block has its own port")
        assertFalse(blockNetwork.ports.contains(portablePort), "block does not see portable port")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun clear_links_removes_all(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 64.0)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)

        val block1 = BeeNetwork()
        block1.addComponent(StubHive(BlockPos(5, 0, 0), level))
        block1.addComponent(StubPort(BlockPos(6, 0, 0), level))

        val block2 = BeeNetwork()
        block2.addComponent(StubHive(BlockPos(10, 0, 0), level))
        block2.addComponent(StubPort(BlockPos(11, 0, 0), level))

        portableNetwork.linkNetwork(block1)
        portableNetwork.linkNetwork(block2)
        assertEquals(2, portableNetwork.ports.size, "sees both block ports")

        portableNetwork.clearLinks()
        assertEquals(0, portableNetwork.ports.size, "no ports after clearLinks")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun find_available_provider_uses_linked_ports(helper: GameTestHelper) = helper.unitTest {
        val level = helper.level
        val portableHive = StubHive(BlockPos(0, 0, 0), level, workRange = 64.0)
        val portableNetwork = BeeNetwork()
        portableNetwork.addComponent(portableHive)

        // Block network with a port that has items
        val blockHive = StubHive(BlockPos(5, 0, 0), level)
        val stone = ItemStack(net.minecraft.world.item.Items.STONE, 64)
        val blockPort = StubPort(BlockPos(10, 0, 0), level, items = mutableListOf(stone))
        val blockNetwork = BeeNetwork()
        blockNetwork.addComponent(blockHive)
        blockNetwork.addComponent(blockPort)

        // Before link: no provider
        val beforeLink = portableNetwork.findAvailableProvider(ItemStack(net.minecraft.world.item.Items.STONE))
        assertTrue(beforeLink == null, "no provider before link")

        // After link: finds the block port
        portableNetwork.linkNetwork(blockNetwork)
        val afterLink = portableNetwork.findAvailableProvider(ItemStack(net.minecraft.world.item.Items.STONE))
        assertNotNull(afterLink, "provider found after link")
        assertEquals(blockPort.id, afterLink!!.id, "provider is the block port")
    }
}
