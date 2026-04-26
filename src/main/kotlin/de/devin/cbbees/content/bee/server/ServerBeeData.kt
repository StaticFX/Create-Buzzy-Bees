package de.devin.cbbees.content.bee.server

import de.devin.cbbees.content.bee.flight.FlightPlan
import de.devin.cbbees.content.bee.state.ConstructionBeeState
import de.devin.cbbees.content.bee.state.StuckCheckData
import de.devin.cbbees.content.bee.state.TransportBeeState
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
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
 * Lightweight bee data object — replaces the full [de.devin.cbbees.content.bee.MechanicalBeeEntity].
 *
 * No entity overhead: no PathfinderMob, no Brain, no SynchedEntityData, no vanilla tick chain.
 * Ticked directly by [ServerBeeManager] with pure Kotlin logic.
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
    var velocity: Vec3 = Vec3.ZERO
    var yRot: Float = 0f

    // ── Spring (fuel) ──
    var springTension: Float = 1.0f
    var rechargeFinishTick: Long = -1

    // ── State machine ──
    var constructionState: ConstructionBeeState = ConstructionBeeState.GATHERING
    var transportState: TransportBeeState = TransportBeeState.FLYING_TO_SOURCE
    var currentTask: TaskBatch? = null
    var transportTask: TransportTask? = null

    // ── Flight plan (checkpoint-based navigation) ──
    var flightPlan: FlightPlan? = null
    var currentCheckpointIndex: Int = 0
    var nextCheckpointArrivalTick: Long = 0
    /** Game tick when the current flight plan was assigned. Used to sync client position. */
    var planStartTick: Long = 0

    // ── Legacy navigation (kept for state machine compat during transition) ──
    var walkTarget: BlockPos? = null

    // ── Hive ──
    var hiveInstance: BeeHive? = null
    var hivePos: BlockPos? = null
    var hiveEntryRetries: Int = 0

    // ── Safety ──
    var orphanedTicks: Int = 0
    val stuckData: StuckCheckData = StuckCheckData()
    var returningToOwner: Player? = null

    // ── Inventory ──
    val inventory: SimpleContainer = SimpleContainer(if (type == BeeType.CONSTRUCTION) 4 else 3)

    // ── Context cache ──
    var cachedBeeContext: BeeContext? = null
    private var contextRefreshTick: Int = 0

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

    override fun getOwnerPlayer(): Player? {
        val ownerId = this.ownerId ?: return null
        return (_level as? ServerLevel)?.server?.playerList?.getPlayer(ownerId)
    }

    fun refreshContext() {
        cachedBeeContext = hiveInstance?.getBeeContext()
    }

    // ════════════════════════════════════════════════════════════════════
    //  NBT Serialization
    // ════════════════════════════════════════════════════════════════════

    fun save(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID("Id", id)
        tag.putString("Type", type.name)
        tag.putUUID("NetworkId", networkId)
        hiveId?.let { tag.putUUID("HiveId", it) }
        ownerId?.let { tag.putUUID("OwnerId", it) }

        tag.putDouble("PosX", pos.x)
        tag.putDouble("PosY", pos.y)
        tag.putDouble("PosZ", pos.z)
        tag.putFloat("YRot", yRot)
        tag.putFloat("Spring", springTension)
        tag.putLong("RechargeTick", rechargeFinishTick)

        tag.putString("State", if (type == BeeType.CONSTRUCTION) constructionState.name else transportState.name)

        walkTarget?.let {
            tag.putInt("WalkX", it.x)
            tag.putInt("WalkY", it.y)
            tag.putInt("WalkZ", it.z)
        }

        hivePos?.let {
            tag.putInt("HivePosX", it.x)
            tag.putInt("HivePosY", it.y)
            tag.putInt("HivePosZ", it.z)
        }

        // Save inventory
        val itemList = ListTag()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val itemTag = CompoundTag()
                itemTag.putByte("Slot", i.toByte())
                itemList.add(stack.save(registries, itemTag))
            }
        }
        tag.put("Inventory", itemList)

        return tag
    }

    companion object {
        fun load(tag: CompoundTag, registries: HolderLookup.Provider): ServerBeeData {
            val id = tag.getUUID("Id")
            val type = BeeType.valueOf(tag.getString("Type"))
            val networkId = tag.getUUID("NetworkId")

            val bee = ServerBeeData(id, type, networkId)
            if (tag.hasUUID("HiveId")) bee.hiveId = tag.getUUID("HiveId")
            if (tag.hasUUID("OwnerId")) bee.ownerId = tag.getUUID("OwnerId")

            bee.pos = Vec3(tag.getDouble("PosX"), tag.getDouble("PosY"), tag.getDouble("PosZ"))
            bee.yRot = tag.getFloat("YRot")
            bee.springTension = tag.getFloat("Spring")
            bee.rechargeFinishTick = tag.getLong("RechargeTick")

            if (type == BeeType.CONSTRUCTION) {
                bee.constructionState = try {
                    ConstructionBeeState.valueOf(tag.getString("State"))
                } catch (_: Exception) { ConstructionBeeState.FLYING_HOME }
            } else {
                bee.transportState = try {
                    TransportBeeState.valueOf(tag.getString("State"))
                } catch (_: Exception) { TransportBeeState.FLYING_HOME }
            }

            if (tag.contains("WalkX")) {
                bee.walkTarget = BlockPos(tag.getInt("WalkX"), tag.getInt("WalkY"), tag.getInt("WalkZ"))
            }
            if (tag.contains("HivePosX")) {
                bee.hivePos = BlockPos(tag.getInt("HivePosX"), tag.getInt("HivePosY"), tag.getInt("HivePosZ"))
            }

            // Load inventory
            val itemList = tag.getList("Inventory", Tag.TAG_COMPOUND.toInt())
            for (j in 0 until itemList.size) {
                val itemTag = itemList.getCompound(j)
                val slot = itemTag.getByte("Slot").toInt()
                if (slot in 0 until bee.inventory.containerSize) {
                    bee.inventory.setItem(slot, ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY))
                }
            }

            return bee
        }
    }
}
