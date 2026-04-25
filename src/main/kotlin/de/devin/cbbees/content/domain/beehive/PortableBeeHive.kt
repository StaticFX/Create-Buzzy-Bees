package de.devin.cbbees.content.domain.beehive

import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.items.IItemHandler
import top.theillusivec4.curios.api.CuriosApi
import java.util.*

/**
 * Implementation of IBeeHome that wraps a player and their portable beehive (backpack).
 *
 * Also implements [LogisticsPort] so the player's inventory acts as the highest-priority
 * logistics port in the portable network. Bees can take items from the player or bring
 * items to the player through the standard port-finding system.
 */
class PortableBeeHive(val player: Player) : BeeHive, LogisticsPort {

    companion object {
        /** Networking range for portable beehives (blocks). */
        const val NETWORKING_RANGE = 6.0
    }

    private val activeBees = mutableSetOf<UUID>()

    override fun getActiveBeeCount(): Int = activeBees.size

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getAvailableBeeCount() <= 0) {
            return false
        }
        if (getActiveBeeCount() >= getBeeContext().maxActiveBees) return false

        val beeItem = consumeBee()
        if (beeItem.isEmpty) return false
        val spawned = spawnBee(beeItem, batch)
        if (!spawned) {
            addBee(beeItem)
        }
        return spawned
    }

    private fun spawnBee(beeItem: ItemStack, batch: TaskBatch): Boolean {
        val ctx = getBeeContext()
        val honeyCost = (CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
        consumeHoney(honeyCost)

        val spawnPos = player.position().add(0.0, 2.0, 0.0)
        val bee = ServerBeeManager.spawnConstructionBee(
            hive = this,
            batch = batch,
            networkId = network().id,
            spawnPos = spawnPos,
            context = ctx,
            ownerId = player.uuid,
        )

        activeBees.add(bee.id)
        return true
    }

    override fun onBeeRemoved(bee: net.minecraft.world.entity.Entity) {
        activeBees.remove(bee.uuid)
    }

    /**
     * Removes active bee entries for entities that no longer exist in the world.
     * Called by the watchdog to prevent ghost bee counts from blocking new dispatches.
     */
    fun cleanupOrphanedBees() {
        val level = player.level() as? ServerLevel ?: return
        activeBees.removeIf { beeId ->
            val entity = level.getEntity(beeId)
            entity == null || !entity.isAlive
        }
    }

    override fun notifyTaskCompleted(task: BeeTask, beeId: UUID): TaskBatch? {
        if (!isValid()) return null
        val nextBatch = GlobalJobPool.workBacklog(this)
        nextBatch?.assignToBee(beeId, player.level().gameTime)
        return nextBatch
    }

    override val id: UUID get() = player.uuid
    override val world: Level get() = player.level()
    override val pos: BlockPos get() = player.blockPosition()
    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    override fun getBeeContext(): BeeContext {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return BeeContext()
        return (backpack.item as PortableBeehiveItem).getBeeContext(backpack)
    }

    override fun getNetworkingRange(): Double = NETWORKING_RANGE

    override fun rechargeSpring(ctx: BeeContext): Int {
        val honeyCost =
            (CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
        consumeHoney(honeyCost)
        return super.rechargeSpring(ctx)
    }

    override fun chargeReturnFuel(springDeficit: Float, ctx: BeeContext) {
        if (springDeficit <= 0f) return
        val honeyCost =
            (springDeficit * CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt()
                .coerceAtLeast(1)
        consumeHoney(honeyCost)
    }

    fun consumeHoney(amount: Int): Int {
        if (player.isCreative) return amount
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        val stored = backpack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        val toConsume = minOf(amount, stored)
        backpack.set(AllDataComponents.HONEY_FUEL.get(), stored - toConsume)
        return toConsume
    }

    fun hasHoney(amount: Int): Boolean {
        if (player.isCreative) return true
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        return backpack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0) >= amount
    }

    fun addBee(item: ItemStack): Boolean {
        val backpackItemStack = getBackpackStack()
        if (backpackItemStack.isEmpty) return false
        return (backpackItemStack.item as PortableBeehiveItem).addRobot(backpackItemStack, item)
    }

    override fun consumeBee(): ItemStack {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return ItemStack.EMPTY
        return (backpack.item as PortableBeehiveItem).consumeBee(backpack)
    }

    override fun getAvailableBeeCount(): Int {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        return (backpack.item as PortableBeehiveItem).getTotalRobotCount(backpack)
    }

    override fun returnBee(item: ItemStack): Boolean {
        return addBee(item)
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(player.blockPosition().above(2), 1.0f, 1)
    }

    /**
     * Portable beehive is always an anchor (BeeHive contract).
     * This takes precedence over [LogisticsPort]'s default of false.
     */
    override fun isAnchor(): Boolean = true

    override fun isInWorkRange(pos: BlockPos): Boolean = isInRange(pos)

    override fun sync() {}

    override fun getPortType(): PortType = PortType.INSERT // Both, but INSERT as default

    override fun getFilter(): ItemStack = ItemStack.EMPTY

    override fun isValidForPickup(): Boolean = true

    override fun isValidForDropOff(): Boolean = true

    override fun testFilter(stack: ItemStack): Boolean = true

    override fun canBeeDropOffItem(bee: MechanicalBeeEntity): Boolean = true

    override fun getItemHandler(level: Level): IItemHandler? = null

    /** Lowest priority — bees prefer network logistics ports; player inventory is the fallback. */
    override fun priority(): Int = Int.MIN_VALUE

    override fun hasItemStack(stack: ItemStack): Boolean {
        if (player.isCreative) return true
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, stack) && slot.count >= stack.count) {
                return true
            }
        }
        if (getBeeContext().inventoryAccessEnabled) {
            if (hasItemInContainers(stack)) return true
        }
        return false
    }

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean {
        return hasItemStack(stack)
    }

    override fun removeItemStack(stack: ItemStack): Boolean {
        if (player.isCreative) return true
        var remaining = stack.count
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, stack)) {
                val take = minOf(remaining, slot.count)
                slot.shrink(take)
                if (slot.isEmpty) player.inventory.setItem(i, ItemStack.EMPTY)
                remaining -= take
                if (remaining <= 0) return true
            }
        }
        if (remaining > 0 && getBeeContext().inventoryAccessEnabled) {
            remaining = removeItemFromContainers(stack, remaining)
        }
        return remaining <= 0
    }

    private fun hasItemInContainers(stack: ItemStack): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (slot.isEmpty) continue
            val contents = slot.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
            if (contents == net.minecraft.world.item.component.ItemContainerContents.EMPTY) continue
            for (contained in contents.nonEmptyItems()) {
                if (ItemStack.isSameItemSameComponents(contained, stack) && contained.count >= stack.count) {
                    return true
                }
            }
        }
        return false
    }

    private fun removeItemFromContainers(stack: ItemStack, count: Int): Int {
        var remaining = count
        for (i in 0 until player.inventory.containerSize) {
            if (remaining <= 0) break
            val slot = player.inventory.getItem(i)
            if (slot.isEmpty) continue
            val contents = slot.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
            if (contents == net.minecraft.world.item.component.ItemContainerContents.EMPTY) continue

            val items = contents.nonEmptyItems().toMutableList()
            var modified = false
            for (j in items.indices) {
                val contained = items[j]
                if (ItemStack.isSameItemSameComponents(contained, stack)) {
                    val take = minOf(remaining, contained.count)
                    contained.shrink(take)
                    remaining -= take
                    modified = true
                    if (remaining <= 0) break
                }
            }
            if (modified) {
                slot.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(items))
            }
        }
        return remaining
    }

    override fun addItemStack(stack: ItemStack): ItemStack {
        val copy = stack.copy()
        if (player.inventory.add(copy)) {
            return ItemStack.EMPTY
        }
        return copy
    }

    /**
     * Returns true if the player still has the portable beehive equipped
     * (in Curios back slot or chestplate slot).
     */
    fun isValid(): Boolean = !getBackpackStack().isEmpty

    private fun getBackpackStack(): ItemStack {
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (curiosResult.isPresent) {
            return curiosResult.get().stack()
        }
        val chestplate = player.inventory.armor[2]
        if (chestplate.item is PortableBeehiveItem) {
            return chestplate
        }
        return ItemStack.EMPTY
    }
}
