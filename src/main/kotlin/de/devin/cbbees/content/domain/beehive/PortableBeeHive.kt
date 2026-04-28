package de.devin.cbbees.content.domain.beehive

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.backpack.PortableBeehiveItem
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.domain.logistics.PortReservationManager
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
import de.devin.cbbees.compat.CuriosCompat
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

        /** Maximum work/logistics range for portable beehives (blocks). */
        const val MAX_WORK_RANGE = 64.0
    }

    /** Active bee UUIDs grouped by job ID, mirroring MechanicalBeehiveBlockEntity's tracking. */
    private val activeBeesByJob = mutableMapOf<UUID, MutableSet<UUID>>()

    /** Tracks item reservations made by bees picking up from the player's inventory. */
    private val reservationManager = PortReservationManager()

    override fun getActiveBeeCount(): Int = activeBeesByJob.values.sumOf { it.size }

    fun getActiveBeeCountForJob(jobId: UUID): Int = activeBeesByJob[jobId]?.size ?: 0

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getAvailableBeeCount() <= 0) return false
        if (getActiveBeeCount() >= getBeeContext().maxActiveBees) return false
        if (!hasBeeOfType(batch.beeType)) return false
        if (getActiveBeeCountForJob(batch.job.jobId) >= getMaxContributionBees()) return false

        val ctx = getBeeContext()
        val honeyCost = (CBBeesConfig.portableHoneyPerRewind.get() * ctx.fuelConsumptionMultiplier).toInt().coerceAtLeast(1)
        if (!hasHoney(honeyCost)) return false

        val beeItem = consumeBeeOfType(batch.beeType)
        if (beeItem.isEmpty) return false
        CreateBuzzyBeez.LOGGER.debug("[PortableHive] Spawning bee for batch at ${batch.targetPosition}")
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

        activeBeesByJob.getOrPut(batch.job.jobId) { mutableSetOf() }.add(bee.id)
        return true
    }

    override fun onBeeRemoved(bee: net.minecraft.world.entity.Entity) {
        onBeeRemovedById(bee.uuid)
    }

    override fun onBeeRemovedById(beeId: UUID) {
        val iter = activeBeesByJob.iterator()
        while (iter.hasNext()) {
            val (_, bees) = iter.next()
            if (bees.remove(beeId)) {
                if (bees.isEmpty()) iter.remove()
                break
            }
        }
    }

    /**
     * Removes active bee entries for entities that no longer exist in the world.
     * Called by the watchdog to prevent ghost bee counts from blocking new dispatches.
     */
    fun cleanupOrphanedBees() {
        val level = player.level() as? ServerLevel ?: return
        val iter = activeBeesByJob.iterator()
        while (iter.hasNext()) {
            val (_, bees) = iter.next()
            bees.removeIf { beeId ->
                ServerBeeManager.getBee(beeId) == null &&
                    ((level.getEntity(beeId)?.isAlive) != true)
            }
            if (bees.isEmpty()) iter.remove()
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
        return (backpackItemStack.item as PortableBeehiveItem).addBee(backpackItemStack, item)
    }

    override fun consumeBee(): ItemStack {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return ItemStack.EMPTY
        return (backpack.item as PortableBeehiveItem).consumeBee(backpack)
    }

    fun consumeBeeOfType(beeType: de.devin.cbbees.content.bee.server.BeeType): ItemStack {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return ItemStack.EMPTY
        return (backpack.item as PortableBeehiveItem).consumeBeeOfType(backpack, beeType.itemClass)
    }

    override fun hasBeeOfType(beeType: de.devin.cbbees.content.bee.server.BeeType): Boolean {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return false
        val contents = backpack.get(net.minecraft.core.component.DataComponents.CONTAINER) ?: return false
        val items = net.minecraft.core.NonNullList.withSize(PortableBeehiveItem.TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)
        return (0 until PortableBeehiveItem.BEE_SLOTS).any { !items[it].isEmpty && beeType.itemClass.isInstance(items[it].item) }
    }

    override fun getAvailableBeeCount(): Int {
        val backpack = getBackpackStack()
        if (backpack.isEmpty) return 0
        return (backpack.item as PortableBeehiveItem).getTotalBeeCount(backpack)
    }

    override fun returnBee(item: ItemStack): Boolean {
        return addBee(item)
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(player.blockPosition().above(2), 1.0f, 1)
    }

    override fun gatherPos(): BlockPos = player.blockPosition().above(3)

    override fun approachPos(): BlockPos = player.blockPosition().above(3)

    /**
     * Portable beehive is always an anchor (BeeHive contract).
     * This takes precedence over [LogisticsPort]'s default of false.
     */
    override fun isAnchor(): Boolean = true

    override fun getWorkRange(): Double = minOf(super.getWorkRange(), MAX_WORK_RANGE)

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
            if (!slot.isEmpty && ItemStack.isSameItem(slot, stack) && slot.count >= stack.count) {
                return true
            }
        }
        if (getBeeContext().inventoryAccessEnabled) {
            if (hasItemInContainers(stack)) return true
        }
        return false
    }

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean {
        if (player.isCreative) return true
        val physical = countItem(stack)
        val reserved = reservationManager.getReservedCount(stack, excludeBeeId)
        return physical - reserved >= stack.count
    }

    private fun countItem(stack: ItemStack): Int {
        var count = 0
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItem(slot, stack)) {
                count += slot.count
            }
        }
        if (getBeeContext().inventoryAccessEnabled) {
            count += countItemInContainers(stack)
        }
        return count
    }

    private fun countItemInContainers(stack: ItemStack): Int {
        var count = 0
        for (i in 0 until player.inventory.containerSize) {
            val container = player.inventory.getItem(i)
            val handler = container.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM)
                ?: continue
            for (slot in 0 until handler.slots) {
                val slotStack = handler.getStackInSlot(slot)
                if (!slotStack.isEmpty && ItemStack.isSameItem(slotStack, stack)) {
                    count += slotStack.count
                }
            }
        }
        return count
    }

    override fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {
        reservationManager.reserve(beeId, items, tick)
    }

    override fun releaseReservation(beeId: UUID) {
        reservationManager.release(beeId)
    }

    override fun cleanupReservations(currentTick: Long, maxAge: Long) {
        reservationManager.cleanup(currentTick, maxAge)
    }

    override fun clearReservations() {
        reservationManager.clear()
    }

    override fun removeItemStack(stack: ItemStack): Boolean {
        if (player.isCreative) return true
        var remaining = stack.count
        for (i in 0 until player.inventory.containerSize) {
            val slot = player.inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItem(slot, stack)) {
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
            val container = player.inventory.getItem(i)
            if (container.isEmpty) continue
            val handler = container.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM)
                ?: continue
            for (slot in 0 until handler.slots) {
                val slotStack = handler.getStackInSlot(slot)
                if (!slotStack.isEmpty && ItemStack.isSameItem(slotStack, stack) && slotStack.count >= stack.count) {
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
            val container = player.inventory.getItem(i)
            if (container.isEmpty) continue
            val handler = container.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM)
                ?: continue
            for (slot in 0 until handler.slots) {
                if (remaining <= 0) break
                val slotStack = handler.getStackInSlot(slot)
                if (!slotStack.isEmpty && ItemStack.isSameItem(slotStack, stack)) {
                    val extracted = handler.extractItem(slot, remaining, false)
                    remaining -= extracted.count
                }
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
        val curiosStack = CuriosCompat.findFirstCurio(player) { it.item is PortableBeehiveItem }
        if (!curiosStack.isEmpty) return curiosStack
        val chestplate = player.inventory.armor[2]
        if (chestplate.item is PortableBeehiveItem) {
            return chestplate
        }
        return ItemStack.EMPTY
    }
}
