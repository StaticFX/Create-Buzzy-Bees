package de.devin.cbbees.content.bee

import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.*

/**
 * Shared contract for mechanical bee entities (construction bees and bumble bees).
 * Enables unified behaviors and reduces code duplication between bee types.
 *
 * Implementors must also be [PathfinderMob] subclasses — default implementations
 * cast `this` to `PathfinderMob` to access entity state.
 *
 * Implemented by [MechanicalBeeEntity] and [MechanicalBumbleBeeEntity].
 */
interface MechanicalBeelike : NetworkedBee {
    var springTension: Float
    var rechargeFinishTick: Long
    var hiveEntryRetries: Int

    /** Label for debug logging (e.g. "Bee", "Bumble") */
    val debugLabel: String

    val networkId: UUID
    val inventory: SimpleContainer

    /** UUID of the home beehive, read from entity data */
    val homeId: UUID?
    fun setHomeId(uuid: UUID)

    /** The item stack representing this bee type (for drops / hive entry) */
    fun beeItemStack(): ItemStack

    // ── State machine fields (replaces Brain memories) ──

    /** Current walk target position, or null if not moving. Replaces WALK_TARGET memory. */
    var walkTargetPos: BlockPos?

    /** Cached hive instance. Replaces HIVE_INSTANCE memory. */
    var hiveInstance: de.devin.cbbees.content.domain.beehive.BeeHive?

    /** Cached hive position. Replaces HIVE_POS memory. */
    var hivePos: BlockPos?

    /** Player to return to (portable beehive removed). Replaces RETURNING_TO_OWNER memory. */
    var returningToOwner: net.minecraft.world.entity.player.Player?

    /** Orphaned tick counter for adoption timeout. */
    var orphanedTicks: Int

    /** Stuck detection data. */
    val stuckData: de.devin.cbbees.content.bee.state.StuckCheckData

    /** Consumes spring tension for an action. Returns false if empty. */
    fun consumeSpring(baseDrain: Double): Boolean

    // ── Default implementations ──────────────────────────────────────────

    /** Looks up the bee's hive, caching in the state field. */
    fun beehive(): BeeHive? {
        val cached = hiveInstance
        if (cached != null) return cached
        val self = this as PathfinderMob
        if (self.level().isClientSide) return null
        val hiveId = homeId ?: return null
        val hive = ServerBeeNetworkManager.findHive(hiveId)
        if (hive != null) {
            hiveInstance = hive
        }
        return hive
    }

    /** Tries to adopt into the closest available hive in the network */
    fun tryAdoptHive(exclude: BeeHive? = null): BeeHive? {
        val self = this as PathfinderMob
        val net = network() ?: return null
        val hive = net.hives
            .filter { it != exclude }
            .minByOrNull { it.pos.distSqr(self.blockPosition()) } ?: return null
        setHomeId(hive.id)
        hiveInstance = hive
        hivePos = hive.pos
        return hive
    }

    /** Gets the BeeContext for recharge/fuel calculations */
    fun getBeeContextForRecharge(): BeeContext {
        return beehive()?.getBeeContext() ?: BeeContext()
    }

    /** Drops inventory + bee item on the ground and removes this entity */
    fun dropBeeItemAndDiscard(reason: String = "unknown") {
        BeeDebug.log(this, "Dropping as item: $reason")
        val self = this as PathfinderMob
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val drop = ItemEntity(self.level(), self.x, self.y, self.z, stack.copy())
                self.level().addFreshEntity(drop)
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }
        val itemEntity = ItemEntity(self.level(), self.x, self.y, self.z, beeItemStack())
        self.level().addFreshEntity(itemEntity)
        self.discard()
    }

    companion object {
        /**
         * Lightweight flying travel — replaces Entity.move() with a single-block collision check.
         *
         * Entity.move() performs full block collision detection (640K+ chunk lookups at scale).
         * This version does ONE block check at the destination position: if solid, stop;
         * otherwise, setPos directly. Saves ~20% of tick time at 200+ bees.
         */
        fun travelFlying(mob: PathfinderMob, travelVector: Vec3) {
            if (mob.isControlledByLocalInstance()) {
                mob.moveRelative(0.04f, travelVector)
                val delta = mob.deltaMovement
                val newX = mob.x + delta.x
                val newY = mob.y + delta.y
                val newZ = mob.z + delta.z

                // Lightweight collision: check if destination block is solid
                val destPos = net.minecraft.core.BlockPos.containing(newX, newY, newZ)
                if (mob.level().getBlockState(destPos).getCollisionShape(mob.level(), destPos).isEmpty) {
                    mob.setPos(newX, newY, newZ)
                } else {
                    // Hit a solid block — stop movement, navigation will reroute
                    mob.deltaMovement = Vec3.ZERO
                }
                mob.deltaMovement = mob.deltaMovement.scale(0.91)
            }
            mob.calculateEntityAnimation(false)
        }

        /** Shared flying navigation setup — call from PathfinderMob.createNavigation() override */
        fun createFlyingNavigation(mob: PathfinderMob, level: Level): PathNavigation {
            val navigation = BeePathNavigation(mob, level)
            navigation.setCanOpenDoors(false)
            navigation.setCanPassDoors(true)
            return navigation
        }
    }
}
