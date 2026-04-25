package de.devin.cbbees.content.beehive

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import de.devin.cbbees.content.bee.*
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.items.AllItems
import net.createmod.catnip.lang.Lang
import net.createmod.catnip.lang.LangNumberFormat
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper
import java.util.*
import kotlin.math.abs

class MechanicalBeehiveBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    KineticBlockEntity(type, pos, state), IHaveGoggleInformation, BeeHive {

    override val id: UUID get() = homeId
    override val world: Level get() = getLevel()!!
    override val pos: BlockPos get() = blockPos

    private var homeId = UUID.randomUUID()

    /** Active bee UUIDs grouped by job ID (server side only) */
    private val activeBeesByJob = mutableMapOf<UUID, MutableSet<UUID>>()

    override fun getActiveBeeCount(): Int = activeBeesByJob.values.sumOf { it.size }

    fun getActiveBeeCountForJob(jobId: UUID): Int = activeBeesByJob[jobId]?.size ?: 0

    val beeInventory = object : ItemStackHandler(1) {
        override fun onContentsChanged(slot: Int) = sync()
        override fun isItemValid(slot: Int, stack: ItemStack) =
            stack.item is MechanicalBeeItem || stack.item is MechanicalBumbleBeeItem
    }


    val inventory = CombinedInvWrapper(beeInventory)

    val instructions = mutableListOf<BeeInstruction>()

    override fun onLoad() {
        super.onLoad()
        if (level != null) {
            if (level!!.isClientSide) {
                // Always register on client so the component is tracked for display,
                // even before the beehive receives kinetic power.
                addToNetwork(level!!)
            } else if (getSpeed() != 0f) {
                addToNetwork(level!!)
            }
        }
    }

    override fun remove() {
        if (level != null) removeFromNetwork(level!!)
        super.remove()
    }

    override fun destroy() {
        removeFromNetwork(level!!)
        super.destroy()
    }

    fun spawnBee(beeItem: ItemStack, batch: TaskBatch): Boolean {
        val spawnPos = Vec3.atCenterOf(blockPos.above()).add(
            BeeSeparation.spawnOffset(level!!.random)
        )
        val ctx = getBeeContext()

        val bee = ServerBeeManager.spawnConstructionBee(
            hive = this,
            batch = batch,
            networkId = network().id,
            spawnPos = spawnPos,
            context = ctx,
            beeType = batch.beeType,
        )

        activeBeesByJob.getOrPut(batch.job.jobId) { mutableSetOf() }.add(bee.id)
        sync()
        return true
    }

    override fun acceptBatch(batch: TaskBatch): Boolean {
        if (getActiveBeeCount() >= getBeeContext().maxActiveBees) return false

        // Pickup batches use bumble bees; everything else uses construction bees
        val beeItemClass = if (batch.beeType == BeeType.TRANSPORT)
            MechanicalBumbleBeeItem::class.java
        else
            MechanicalBeeItem::class.java

        if (getAvailableBeeCountOfType(beeItemClass) <= 0) return false

        this.setChanged()
        val beeItem = consumeBeeOfType(beeItemClass)
        if (beeItem.isEmpty) return false
        return spawnBee(beeItem, batch)
    }

    override fun notifyTaskCompleted(task: BeeTask, beeId: UUID): TaskBatch? {
        val nextBatch = GlobalJobPool.workBacklog(this)
        nextBatch?.assignToBee(beeId, level!!.gameTime)
        return nextBatch
    }

    override fun onBeeRemoved(bee: net.minecraft.world.entity.Entity) {
        onBeeRemovedById(bee.uuid)
    }

    /** UUID-based removal for non-entity bees. */
    fun onBeeRemovedById(beeId: UUID) {
        var found = false
        val iter = activeBeesByJob.iterator()
        while (iter.hasNext()) {
            val (_, bees) = iter.next()
            if (bees.remove(beeId)) {
                found = true
                if (bees.isEmpty()) iter.remove()
                break
            }
        }
        if (found) sync()
    }

    /**
     * Removes active bee entries for entities that no longer exist in the world.
     * Called by the watchdog to prevent ghost bee counts from blocking new dispatches.
     */
    fun cleanupOrphanedBees() {
        val level = this.level ?: return
        var cleaned = false
        val iter = activeBeesByJob.iterator()
        while (iter.hasNext()) {
            val (_, bees) = iter.next()
            val beeIter = bees.iterator()
            while (beeIter.hasNext()) {
                val beeId = beeIter.next()
                // Check both entity system AND non-entity ServerBeeManager
                val existsAsEntity = (level as? ServerLevel)?.getEntity(beeId)?.isAlive == true
                val existsAsData = ServerBeeManager.getBee(beeId) != null
                if (!existsAsEntity && !existsAsData) {
                    beeIter.remove()
                    cleaned = true
                }
            }
            if (bees.isEmpty()) iter.remove()
        }
        if (cleaned) sync()
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(Vec3.atCenterOf(blockPos.above()), 1.0f, 2)
    }


    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun onSpeedChanged(previousSpeed: Float) {
        super.onSpeedChanged(previousSpeed)
        if (level == null || level!!.isClientSide) return

        if (getSpeed() == 0f) {
            // No RPM — remove from network
            ServerBeeNetworkManager.unregisterWorker(this.id)
        } else if (previousSpeed == 0f) {
            // Just started receiving RPM — join network
            ServerBeeNetworkManager.registerWorker(this)
        } else {
            // RPM changed — re-register so the network picks up the new range
            ServerBeeNetworkManager.unregisterWorker(this.id)
            ServerBeeNetworkManager.registerWorker(this)
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("HomeId", homeId)
        tag.putUUID("NetworkId", networkId)

        val activeBeesList = ListTag()
        activeBeesByJob.forEach { (jobId, bees) ->
            bees.forEach { beeId ->
                val comp = CompoundTag()
                comp.putUUID("Id", beeId)
                comp.putUUID("JobId", jobId)
                activeBeesList.add(comp)
            }
        }
        tag.put("ActiveBees", activeBeesList)

        tag.putInt("ActiveBeeCount", getActiveBeeCount())
        tag.put("BeeInv", beeInventory.serializeNBT(registries))

        val instList = ListTag()
        instructions.forEach {
            val instTag = CompoundTag()
            it.serializeNBT(instTag)
            instList.add(instTag)
        }
        tag.put("Instructions", instList)
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (tag.hasUUID("HomeId")) {
            homeId = tag.getUUID("HomeId")
        }
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId")
        }

        activeBeesByJob.clear()
        if (tag.contains("ActiveBees", Tag.TAG_LIST.toInt())) {
            val list = tag.getList("ActiveBees", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val comp = list.getCompound(i)
                if (comp.hasUUID("Id")) {
                    val jobId = if (comp.hasUUID("JobId")) comp.getUUID("JobId") else UUID(0, 0)
                    activeBeesByJob.getOrPut(jobId) { mutableSetOf() }.add(comp.getUUID("Id"))
                }
            }
        }
        beeInventory.deserializeNBT(registries, tag.getCompound("BeeInv"))

        instructions.clear()
        val instList = tag.getList("Instructions", Tag.TAG_COMPOUND.toInt())
        for (i in 0 until instList.size) {
            instructions.add(BeeInstruction.deserializeNBT(instList.getCompound(i)))
        }
    }

    override fun getBeeContext(): BeeContext {
        val context = BeeContext()

        val rpm = abs(getSpeed())
        val speedDiv = CBBeesConfig.hiveRpmSpeedDivisor.get()
        val beeDivisor = CBBeesConfig.hiveRpmBeeDivisor.get()
        val baseRange = CBBeesConfig.hiveBaseRange.get()
        val rangePerRpm = CBBeesConfig.hiveRangePerRpm.get()

        if (rpm > 0) {
            context.speedMultiplier *= (1.0 + (rpm / speedDiv))
            context.springEfficiency = 1.0 + (rpm / speedDiv)
            val extraBees = (rpm / beeDivisor).toInt()
            context.maxActiveBees = maxOf(
                context.maxActiveBees + extraBees,
                CBBeesConfig.minActiveBeesAtRpm.get()
            )
            context.workRange = baseRange + rpm * rangePerRpm
            context.maxContributedBees += extraBees
        } else {
            context.maxActiveBees = 0
            context.maxContributedBees = 0
            context.workRange = 0.0
        }

        // Cap at config limit
        context.maxActiveBees = minOf(context.maxActiveBees, CBBeesConfig.maxBeesPerHive.get())

        return context
    }

    fun addBee(item: ItemStack): Boolean {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (stack.isEmpty) {
                beeInventory.setStackInSlot(i, item.copyWithCount(1))
                return true
            } else if (ItemStack.isSameItemSameComponents(stack, item) && stack.count < stack.maxStackSize) {
                stack.grow(1)
                sync()
                return true
            }
        }
        return false
    }

    override fun consumeBee(): ItemStack {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty && (stack.item is MechanicalBeeItem || stack.item is MechanicalBumbleBeeItem)) {
                val consumed = stack.copyWithCount(1)
                stack.shrink(1)
                sync()
                return consumed
            }
        }
        return ItemStack.EMPTY
    }

    /**
     * Consumes a bee of a specific item type (e.g. only MechanicalBeeItem or only MechanicalBumbleBeeItem).
     */
    fun consumeBeeOfType(itemClass: Class<*>): ItemStack {
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty && itemClass.isInstance(stack.item)) {
                val consumed = stack.copyWithCount(1)
                stack.shrink(1)
                sync()
                return consumed
            }
        }
        return ItemStack.EMPTY
    }

    override fun returnBee(item: ItemStack): Boolean {
        return addBee(item)
    }

    // BeeSource implementation
    override fun getAvailableBeeCount(): Int {
        var count = 0
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty) {
                count += stack.count
            }
        }
        return count
    }

    /**
     * Gets the count of available bees of a specific item type.
     */
    fun getAvailableBeeCountOfType(itemClass: Class<*>): Int {
        var count = 0
        for (i in 0 until beeInventory.slots) {
            val stack = beeInventory.getStackInSlot(i)
            if (!stack.isEmpty && itemClass.isInstance(stack.item)) {
                count += stack.count
            }
        }
        return count
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        // Show kinetic stats (speed, stress) from KineticBlockEntity
        super<KineticBlockEntity>.addToGoggleTooltip(tooltip, isPlayerSneaking)

        Lang.builder("cbbees").translate("gui.goggles.beehive_stats")
            .forGoggles(tooltip)

        // Network Info
        val net = network()
        net.let { n ->
            Lang.builder("cbbees").translate("gui.goggles.beehive.network")
                .style(ChatFormatting.GRAY)
                .add(Lang.builder("cbbees").text(n.name).style(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1)
        }

        // Flying Bees
        Lang.builder("cbbees").translate("gui.goggles.beehive.flying")
            .style(ChatFormatting.GRAY)
            .add(
                Lang.builder("cbbees").text(ChatFormatting.GOLD, LangNumberFormat.format(getActiveBeeCount().toDouble()))
            )
            .forGoggles(tooltip, 1)

        // Stored Bees
        val storedBees = getAvailableBeeCount()

        Lang.builder("cbbees").translate("gui.goggles.beehive.stored")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("cbbees").text(ChatFormatting.GOLD, LangNumberFormat.format(storedBees.toDouble())))
            .forGoggles(tooltip, 1)

        // Capacity
        val context = getBeeContext()
        Lang.builder("cbbees").translate("gui.goggles.beehive.capacity")
            .style(ChatFormatting.GRAY)
            .add(
                Lang.builder("cbbees")
                    .text(ChatFormatting.GOLD, LangNumberFormat.format(context.maxActiveBees.toDouble()))
            )
            .forGoggles(tooltip, 1)

        // Spring Efficiency
        Lang.builder("cbbees").translate("gui.goggles.beehive.spring_efficiency")
            .style(ChatFormatting.GRAY)
            .add(
                Lang.builder("cbbees")
                    .text(ChatFormatting.GOLD, "${LangNumberFormat.format(context.springEfficiency)}x")
            )
            .forGoggles(tooltip, 1)

        return true
    }

    override fun getIcon(isPlayerSneaking: Boolean): ItemStack {
        return AllItems.MECHANICAL_BEE.asStack()
    }

    fun getMaterialSource(): MaterialSource {
        return WirelessMaterialSource(
            world,
            listOf(pos.below(), pos.north(), pos.south(), pos.east(), pos.west())
        )
    }
}
