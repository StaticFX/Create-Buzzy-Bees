package de.devin.cbbees.content.bee

import com.mojang.serialization.Dynamic
import de.devin.cbbees.content.bee.client.BeeClientTracker
import de.devin.cbbees.content.bee.state.StuckCheckData
import de.devin.cbbees.content.bee.state.TransportBeeState
import de.devin.cbbees.content.bee.state.TransportBeeStateMachine
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.ClientBeeNetworkManager
import de.devin.cbbees.content.domain.network.BeeNetwork
import de.devin.cbbees.content.domain.task.TransportTask
import de.devin.cbbees.items.AllItems
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.SimpleContainer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Mechanical Bumble Bee entity — a logistics transport bee.
 *
 * Slower than Mechanical Bees but carries more items.
 * Shuttles items between EXTRACT and INSERT logistics ports.
 */
class MechanicalBumbleBeeEntity(entityType: EntityType<out PathfinderMob>, level: Level) : PathfinderMob(entityType, level),
    GeoEntity, MechanicalBeelike {

    companion object {
        private val BEEHIVE_ID: EntityDataAccessor<Optional<UUID>> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.OPTIONAL_UUID)
        private val TARGET_POS: EntityDataAccessor<Optional<BlockPos>> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.OPTIONAL_BLOCK_POS)
        private val SPRING_TENSION: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(MechanicalBumbleBeeEntity::class.java, EntityDataSerializers.FLOAT)

        const val WORK_RANGE: Double = 2.5
        const val INVENTORY_SIZE: Int = 3

        fun createAttributes(): AttributeSupplier.Builder {
            return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.FLYING_SPEED, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0)
        }
    }

    private val geoCache = GeckoLibUtil.createInstanceCache(this)

    override var inventory = SimpleContainer(INVENTORY_SIZE)
        private set

    override var networkId: UUID = UUID.randomUUID()

    override var rechargeFinishTick: Long = -1
    override var hiveEntryRetries = 0
    override val debugLabel: String = "Bumble"

    val workRange: Double = WORK_RANGE

    override var springTension: Float
        get() = entityData.get(SPRING_TENSION)
        set(value) = entityData.set(SPRING_TENSION, value.coerceIn(0.0f, 1.0f))

    override val homeId: UUID? get() = entityData.get(BEEHIVE_ID).getOrNull()
    override fun setHomeId(uuid: UUID) { entityData.set(BEEHIVE_ID, Optional.of(uuid)) }
    override fun beeItemStack(): ItemStack = ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get())

    // ── State machine fields ──
    var beeState = TransportBeeState.FLYING_TO_SOURCE
    var transportTask: TransportTask? = null
    override var walkTargetPos: BlockPos? = null
    override var hiveInstance: BeeHive? = null
    override var hivePos: BlockPos? = null
    override var returningToOwner: Player? = null
    override var orphanedTicks: Int = 0
    override val stuckData = StuckCheckData()

    /**
     * Consumes spring tension for an action. BumbleBee uses flat rates (no BeeContext).
     * Returns false if spring is already empty. Drains to 0 if insufficient for a full action.
     */
    override fun consumeSpring(baseDrain: Double): Boolean {
        if (springTension <= 0f) return false
        val effectiveDrain = baseDrain.toFloat()
        springTension = (springTension - effectiveDrain).coerceAtLeast(0f)
        return true
    }

    override fun network(): BeeNetwork? {
        return if (level().isClientSide) {
            ClientBeeNetworkManager.getNetwork(networkId)
        } else {
            ServerBeeNetworkManager.getNetwork(networkId, level())
        }
    }

    init {
        this.moveControl = FlyingMoveControl(this, 40, true)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (source.entity is Player && (source.entity as Player).isCreative) {
            return super.hurt(source, amount)
        }
        return false
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "controller", 5) { event ->
                event.setAndContinue(RawAnimation.begin().thenLoop("idle"))
            }
        )
    }

    override fun customServerAiStep() {
        this.level().profiler.push("mechanicalBumbleBrain")
        TransportBeeStateMachine.tick(
            this, this.level() as ServerLevel, (this.level() as ServerLevel).gameTime
        )
        this.level().profiler.pop()
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache? {
        return geoCache
    }

    override fun brainProvider(): Brain.Provider<*> {
        @Suppress("UNCHECKED_CAST")
        return Brain.provider(
            listOf<net.minecraft.world.entity.ai.memory.MemoryModuleType<*>>(),
            listOf<net.minecraft.world.entity.ai.sensing.SensorType<out net.minecraft.world.entity.ai.sensing.Sensor<in MechanicalBumbleBeeEntity>>>()
        ) as Brain.Provider<MechanicalBumbleBeeEntity>
    }

    @Suppress("UNCHECKED_CAST")
    override fun makeBrain(dynamic: Dynamic<*>): Brain<MechanicalBumbleBeeEntity> {
        return this.brainProvider().makeBrain(dynamic) as Brain<MechanicalBumbleBeeEntity>
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(BEEHIVE_ID, Optional.empty())
        builder.define(TARGET_POS, Optional.empty())
        builder.define(SPRING_TENSION, 1.0f)
    }

    override fun createNavigation(level: Level): PathNavigation =
        MechanicalBeelike.createFlyingNavigation(this, level)

    override fun travel(travelVector: Vec3) =
        MechanicalBeelike.travelFlying(this, travelVector)

    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide) {
            network()?.releaseReservations(this.uuid)
            beehive()?.onBeeRemoved(this)
        }
        super.remove(reason)
    }

    override fun onAddedToLevel() {
        super.onAddedToLevel()
        if (level().isClientSide) {
            BeeClientTracker.onBeeAdded(this)
        }
    }

    override fun onRemovedFromLevel() {
        super.onRemovedFromLevel()
        if (level().isClientSide) {
            BeeClientTracker.onBeeRemoved(this)
        }
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        // Legacy entity bee from old save — return to hive or drop as item
        val beeItem = beeItemStack()
        val hive = beehive()
        if (hive != null && hive.returnBee(beeItem)) {
            val hivePos = hive.pos
            for (i in 0 until inventory.containerSize) {
                val stack = inventory.getItem(i)
                if (!stack.isEmpty) {
                    level().addFreshEntity(ItemEntity(level(), hivePos.x + 0.5, hivePos.y + 1.0, hivePos.z + 0.5, stack.copy()))
                }
            }
        } else {
            level().addFreshEntity(ItemEntity(level(), x, y, z, beeItem))
            for (i in 0 until inventory.containerSize) {
                val stack = inventory.getItem(i)
                if (!stack.isEmpty) {
                    level().addFreshEntity(ItemEntity(level(), x, y, z, stack.copy()))
                }
            }
        }
        discard()
    }

    private var lastSyncedTargetPos: BlockPos? = null

    private fun syncTargetPos() {
        val newPos: BlockPos? = walkTargetPos

        if (newPos != lastSyncedTargetPos) {
            lastSyncedTargetPos = newPos
            entityData.set(TARGET_POS, if (newPos != null) Optional.of(newPos) else Optional.empty())
        }
    }

    override fun getTargetPos(): BlockPos? = entityData.get(TARGET_POS).orElse(null)

    // Fly through water — no swimming, no water drag
    override fun isNoGravity(): Boolean = true
    override fun isInWater(): Boolean = false

    @Deprecated("Overrides deprecated MC method", level = DeprecationLevel.WARNING)
    override fun isPushedByFluid(): Boolean = false

    override fun push(entity: Entity) { /* no-op */ }
    override fun doPush(entity: Entity) { /* no-op */ }
    @Deprecated("Overrides deprecated MC method", level = DeprecationLevel.WARNING)
    override fun isPushable(): Boolean = false
    override fun pushEntities() { /* no-op — skip expensive nearby entity scan */ }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        entityData.get(BEEHIVE_ID).ifPresent { compound.putUUID("HomeId", it) }
        compound.putUUID("NetworkId", networkId)
        compound.putFloat("SpringTension", springTension)
        compound.putLong("RechargeFinishTick", rechargeFinishTick)

        val itemsTag = ListTag()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) {
                val slotTag = stack.save(registryAccess()) as CompoundTag
                slotTag.putInt("Slot", i)
                itemsTag.add(slotTag)
            }
        }
        compound.put("BeeInventory", itemsTag)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        if (compound.hasUUID("HomeId")) {
            entityData.set(BEEHIVE_ID, Optional.of(compound.getUUID("HomeId")))
        }
        if (compound.hasUUID("NetworkId")) {
            networkId = compound.getUUID("NetworkId")
        }
        if (compound.contains("SpringTension")) {
            springTension = compound.getFloat("SpringTension")
        }
        if (compound.contains("RechargeFinishTick")) {
            rechargeFinishTick = compound.getLong("RechargeFinishTick")
        }

        if (compound.contains("BeeInventory")) {
            val itemsTag = compound.getList("BeeInventory", 10)
            for (j in 0 until itemsTag.size) {
                val slotTag = itemsTag.getCompound(j)
                val slot = slotTag.getInt("Slot")
                if (slot in 0 until inventory.containerSize) {
                    inventory.setItem(slot, ItemStack.parseOptional(registryAccess(), slotTag))
                }
            }
        }
    }

    override fun getName(): Component {
        return Component.translatable("entity.cbbees.mechanical_bumble_bee")
    }

    /**
     * Inserts a stack into the bee's inventory. Returns the remainder that didn't fit.
     */
    fun addToInventory(stack: ItemStack): ItemStack {
        var remaining = stack.copy()
        for (i in 0 until inventory.containerSize) {
            if (remaining.isEmpty) return ItemStack.EMPTY
            val slotStack = inventory.getItem(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                val canAdd = minOf(remaining.count, slotStack.maxStackSize - slotStack.count)
                if (canAdd > 0) {
                    slotStack.grow(canAdd)
                    remaining.shrink(canAdd)
                }
            }
        }
        for (i in 0 until inventory.containerSize) {
            if (remaining.isEmpty) return ItemStack.EMPTY
            if (inventory.getItem(i).isEmpty) {
                inventory.setItem(i, remaining.copy())
                return ItemStack.EMPTY
            }
        }
        return remaining
    }

    fun getInventoryContents(): List<ItemStack> {
        val list = mutableListOf<ItemStack>()
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty) list.add(stack)
        }
        return list
    }

    fun isInventoryEmpty(): Boolean {
        for (i in 0 until inventory.containerSize) {
            if (!inventory.getItem(i).isEmpty) return false
        }
        return true
    }

    fun isInventoryFull(): Boolean {
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty || stack.count < stack.maxStackSize) return false
        }
        return true
    }

    fun removeFromInventory(stack: ItemStack, count: Int): Boolean {
        var toRemove = count
        for (i in 0 until inventory.containerSize) {
            if (toRemove <= 0) break
            val slotStack = inventory.getItem(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, stack)) {
                val removed = minOf(slotStack.count, toRemove)
                slotStack.shrink(removed)
                toRemove -= removed
                if (slotStack.isEmpty) inventory.setItem(i, ItemStack.EMPTY)
            }
        }
        return toRemove <= 0
    }
}
