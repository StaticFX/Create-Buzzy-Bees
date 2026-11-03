package de.devin.cbbees.content.bee.server

import de.devin.cbbees.content.bee.flight.FlightPlan
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import java.util.UUID

/**
 * Type of bee for determining which state machine to use.
 * Each type maps to a specific bee item class.
 */
enum class BeeType(val itemClass: Class<*>) {
    CONSTRUCTION(de.devin.cbbees.content.bee.MechanicalBeeItem::class.java),
    TRANSPORT(de.devin.cbbees.content.bee.MechanicalBumbleBeeItem::class.java)
}

/**
 * Server-side bee. Bees are plain data objects, not entities, ticked directly by [ServerBeeManager].
 */
class ServerBeeData(
    val id: UUID,
    val type: BeeType,
    override var networkId: UUID,
    override var hiveId: UUID? = null,
    /** Owner player UUID for portable beehive bees. */
    var ownerId: UUID? = null,
) : BeeWorker {

    // ── Position & movement ──
    var pos: Vec3 = Vec3.ZERO

    // ── Spring (fuel) ──
    var springTension: Float = 1.0f

    // ── Task ──
    var currentTask: TaskBatch? = null
    var transportTask: TransportTask? = null

    // ── Flight plan (checkpoint-based navigation) ──
    var flightPlan: FlightPlan? = null
    var currentCheckpointIndex: Int = 0
    var nextCheckpointArrivalTick: Long = 0

    // ── Hive ──
    var hiveInstance: BeeHive? = null
    var hivePos: BlockPos? = null

    // ── Inventory ──
    val inventory: SimpleContainer =
        SimpleContainer(if (type == BeeType.CONSTRUCTION) CONSTRUCTION_INVENTORY_SIZE else TRANSPORT_INVENTORY_SIZE)

    override var carriedFluid: FluidStack = FluidStack.EMPTY

    /** Carried item as last sent to clients. */
    var syncedCarriedItem: ItemStack = ItemStack.EMPTY

    /** Players that were sent this bee's current flight plan. */
    val viewers: MutableSet<UUID> = mutableSetOf()

    /** Pours carried fluid back into a Dock Port, preferring drop-off ports. Fluid with nowhere to go is lost. */
    fun returnCarriedFluid() {
        val fluid = carriedFluid.takeIf { !it.isEmpty } ?: return
        val network = network()
        val port = network?.findFluidDropOff(fluid)
            ?: network?.dockPorts?.firstOrNull { it.testFilter(fluid) && it.canFill(fluid) }
        port?.fillFluid(fluid)
        carriedFluid = FluidStack.EMPTY
    }

    /** Hands items the bee still carries to a drop-off port, dropping whatever doesn't fit at the bee. */
    fun returnCarriedItems() {
        val network = network()
        getInventoryContents().map(ItemStack::copy).forEach { stack ->
            removeFromInventory(stack, stack.count)
            val rest = network?.findDropOff(stack, hiveId)?.addItemStack(stack) ?: stack
            if (!rest.isEmpty) _level.addFreshEntity(ItemEntity(_level, pos.x, pos.y, pos.z, rest))
        }
    }

    fun carriedItem(): ItemStack =
        if (!carriedFluid.isEmpty) ItemStack(carriedFluid.fluid.bucket)
        else getInventoryContents().firstOrNull() ?: ItemStack.EMPTY

    // ── Context cache ──
    var cachedBeeContext: BeeContext? = null

    // ── Level reference (set by ServerBeeManager before ticking) ──
    lateinit var _level: Level

    // ════════════════════════════════════════════════════════════════════
    //  BeeWorker implementation
    // ════════════════════════════════════════════════════════════════════

    override val uuid: UUID get() = id
    override fun blockPosition(): BlockPos = BlockPos.containing(pos)
    override fun level(): Level = _level

    override fun network(): BeeNetwork? =
        ServerBeeNetworkManager.getNetwork(networkId, _level)

    override fun getWorkerX(): Double = pos.x
    override fun getWorkerY(): Double = pos.y
    override fun getWorkerZ(): Double = pos.z

    override fun addToInventory(stack: ItemStack): ItemStack {
        var remaining = stack.copy()
        for (i in 0 until inventory.containerSize) {
            if (remaining.isEmpty) break
            remaining = inventory.addItem(remaining)
        }
        return remaining
    }

    override fun removeFromInventory(stack: ItemStack, count: Int) {
        var toRemove = count
        for (i in 0 until inventory.containerSize) {
            if (toRemove <= 0) break
            val slot = inventory.getItem(i)
            if (!slot.isEmpty && ItemStack.isSameItemSameComponents(slot, stack)) {
                val take = minOf(toRemove, slot.count)
                slot.shrink(take)
                if (slot.isEmpty) inventory.setItem(i, ItemStack.EMPTY)
                toRemove -= take
            }
        }
    }

    override fun getInventoryContents(): List<ItemStack> {
        val result = mutableListOf<ItemStack>()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) result.add(stack)
        }
        return result
    }

    override fun isInventoryFull(): Boolean {
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty || stack.count < stack.maxStackSize) return false
        }
        return true
    }

    override fun isInventoryEmpty(): Boolean {
        for (i in 0 until inventory.containerSize) {
            if (!inventory.getItem(i).isEmpty) return false
        }
        return true
    }

    override fun consumeSpring(baseDrain: Double): Boolean {
        if (springTension <= 0f) return false
        val ctx = getBeeContext()
        val effectiveDrain = if (type == BeeType.CONSTRUCTION) {
            (baseDrain / ctx.springEfficiency * ctx.fuelConsumptionMultiplier).toFloat()
        } else {
            baseDrain.toFloat()
        }
        springTension = (springTension - effectiveDrain).coerceAtLeast(0f)
        return true
    }

    override fun getBeeContext(): BeeContext {
        return cachedBeeContext ?: BeeContext()
    }

    companion object {
        const val CONSTRUCTION_INVENTORY_SIZE = 4
        const val TRANSPORT_INVENTORY_SIZE = 3
        const val FLUID_CAPACITY = FluidType.BUCKET_VOLUME
    }
}
