package de.devin.cbbees.gametest.network

import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.content.logistics.storage.PortStorage
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID

/** Minimal BeeHive stub with configurable position and work range. */
internal class StubHive(
    override val pos: BlockPos,
    private val level: Level,
    private val workRange: Double = 64.0,
) : BeeHive {
    override val id: UUID = UUID.randomUUID()
    override val world: Level get() = level
    override var networkId: UUID? = UUID.randomUUID()
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
internal class StubPort(
    override val pos: BlockPos,
    private val level: Level,
    private val portPriority: Int = 0,
    private val items: MutableList<ItemStack> = mutableListOf(),
) : LogisticsPort {
    override val id: UUID = UUID.randomUUID()
    override val world: Level get() = level
    override var networkId: UUID? = UUID.randomUUID()
    override fun getPortType(): PortType = PortType.INSERT
    override fun getFilter(): ItemStack = ItemStack.EMPTY
    override fun isValidForPickup(): Boolean = true
    override fun isValidForDropOff(): Boolean = true
    override fun storage(): PortStorage? = null
    override fun walkTarget(): WalkTarget = WalkTarget(pos, 1.0f, 0)
    override fun priority(): Int = portPriority
    override fun hasItemStack(stack: ItemStack): Boolean =
        items.any { ItemStack.isSameItem(it, stack) && it.count >= stack.count }
    override fun removeItemStack(stack: ItemStack): Boolean = false
    override fun addItemStack(stack: ItemStack): ItemStack = stack
}
