package de.devin.cbbees.content.logistics.ports

import com.simibubi.create.AllBlocks
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.bee.MechanicalBeeEntity
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.logistics.ports.LogisticPortBlock.Companion.PORT_STATE
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.items.IItemHandler
import java.util.*

class LogisticPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), LogisticsPort, IHaveGoggleInformation {

    lateinit var filteringBehavior: FilteringBehaviour

    lateinit var priorityBehavior: ScrollValueBehaviour

    override var id: UUID = UUID.randomUUID()
    override val world: Level get() = getLevel()!!
    override val pos: BlockPos get() = blockPos

    override var networkId: UUID = UUID.randomUUID()
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    var filterStack: ItemStack = ItemStack.EMPTY
    var priority = 0

    private val reservationManager = de.devin.cbbees.content.domain.logistics.PortReservationManager()

    // This is what the bees will call
    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    private fun getAttachesPos(): BlockPos {
        val attachedDir = LogisticPortBlock.getConnectedDirection(blockState).opposite
        return blockPos.relative(attachedDir)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        filteringBehavior = FilteringBehaviour(this, LogisticsPortFilterValueBox()).withCallback { onFilterChanged() }
        priorityBehavior = ScrollValueBehaviour(
            Component.translatable("gui.cbbees.logistics.priority.title"),
            this,
            LogisticsPortSelectionValueBox()
        )
            .between(0, 256)
            .withCallback {
                priority = it
            }

        behaviours.add(priorityBehavior)
        behaviours.add(filteringBehavior)
    }

    private fun onFilterChanged() {
        if (!level!!.isClientSide) {
            setChanged()
            sendData()
        }
    }

    override fun onLoad() {
        super.onLoad()
        if (level != null) {
            addToNetwork(level!!)
        }
    }

    override fun priority(): Int = priority

    override fun remove() {
        removeFromNetwork(level!!)
        super.remove()
    }

    override fun destroy() {
        removeFromNetwork(level!!)
        super.destroy()
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (tag.hasUUID("lp_id")) {
            id = tag.getUUID("lp_id")
        }
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId")
        }
        if (tag.contains("Filter")) {
            filterStack = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("lp_id", id)
        tag.putUUID("NetworkId", networkId)
        if (!filterStack.isEmpty) {
            tag.put("Filter", filterStack.save(registries))
        }
    }

    override fun getFilter(): ItemStack {
        return filterStack
    }

    override fun getItemHandler(level: Level): IItemHandler? {
        return getInventory(level)
    }

    override fun getPortType(): PortType = portType()

    override fun isValidForDropOff(): Boolean {
        if (portType() != PortType.INSERT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    private fun portState() = blockState.getValue(PORT_STATE)

    private fun portType() = blockState.getValue(LogisticPortBlock.PORT_TYPE)

    override fun isValidForPickup(): Boolean {
        if (portType() != PortType.EXTRACT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    override fun testFilter(stack: ItemStack): Boolean {
        return filteringBehavior.test(stack)
    }

    override fun canBeeDropOffItem(bee: MechanicalBeeEntity): Boolean {
        // TODO implement
        return true;
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(blockPos, 1.0f, 1)
    }

    fun hasCreativeCrate() = level?.let { AllBlocks.CREATIVE_CRATE.has(it.getBlockState(getAttachesPos())) } ?: false

    override fun hasItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false

        if (hasCreativeCrate()) return true

        val handler = getItemHandler(level) ?: return false
        var found = 0

        for (i in 0 until handler.slots) {
            val stackInSlot = handler.getStackInSlot(i)

            if (!stackInSlot.isEmpty && ItemStack.isSameItemSameComponents(stackInSlot, stack)) {
                found += stackInSlot.count
                if (found >= stack.count) return true
            }
        }

        return false
    }

    override fun removeItemStack(stack: ItemStack): Boolean {
        val level = level ?: return false
        if (hasCreativeCrate()) return true

        val handler = getItemHandler(level) ?: return false

        if (!hasItemStack(stack)) return false

        var remaining = stack.count
        for (i in 0 until handler.slots) {
            if (remaining <= 0) break
            val inSlot = handler.getStackInSlot(i)

            if (!inSlot.isEmpty && ItemStack.isSameItemSameComponents(inSlot, stack)) {
                val extracted = handler.extractItem(i, remaining, false)
                remaining -= extracted.count
            }
        }

        if (remaining <= 0) {
            pulseBusy()
            return true
        }
        return false
    }

    override fun addItemStack(stack: ItemStack): ItemStack {
        val level = level ?: return stack
        val handler = getItemHandler(level) ?: return stack

        val result = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack, false)
        if (result.count < stack.count) {
            pulseBusy()
        }
        return result
    }

    private var busyUntilTick: Long = 0L

    private fun pulseBusy() {
        val level = level ?: return
        if (level.isClientSide) return
        val currentState = blockState.getValue(PORT_STATE)
        if (currentState == PortState.INVALID) return
        busyUntilTick = level.gameTime + 20 // stay busy for 1 second
        if (currentState != PortState.BUSY) {
            level.setBlock(blockPos, blockState.setValue(PORT_STATE, PortState.BUSY), 3)
        }
    }

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean {
        val level = level ?: return false
        if (hasCreativeCrate()) return true

        val handler = getItemHandler(level) ?: return false
        var physical = 0
        for (i in 0 until handler.slots) {
            val slotStack = handler.getStackInSlot(i)
            if (!slotStack.isEmpty && ItemStack.isSameItemSameComponents(slotStack, stack)) {
                physical += slotStack.count
            }
        }

        return physical - reservationManager.getReservedCount(stack, excludeBeeId) >= stack.count
    }

    override fun reserve(beeId: UUID, items: List<ItemStack>, tick: Long) {
        reservationManager.reserve(beeId, items, tick)
        updateBusyState()
    }

    override fun releaseReservation(beeId: UUID) {
        if (reservationManager.release(beeId)) updateBusyState()
    }

    override fun cleanupReservations(currentTick: Long, maxAge: Long) {
        if (reservationManager.cleanup(currentTick, maxAge)) updateBusyState()
    }

    override fun clearReservations() {
        if (reservationManager.clear()) updateBusyState()
    }

    override fun lazyTick() {
        super.lazyTick()
        if (busyUntilTick > 0) {
            val level = level ?: return
            if (level.isClientSide) return
            if (level.gameTime >= busyUntilTick && !reservationManager.hasReservations) {
                busyUntilTick = 0L
                if (blockState.getValue(PORT_STATE) == PortState.BUSY) {
                    level.setBlock(blockPos, blockState.setValue(PORT_STATE, PortState.VALID), 3)
                }
            }
        }
    }

    private fun updateBusyState() {
        val level = level ?: return
        if (level.isClientSide) return
        val currentState = blockState.getValue(PORT_STATE)
        if (currentState == PortState.INVALID) return

        val targetState = if (reservationManager.hasReservations) PortState.BUSY else PortState.VALID
        if (currentState != targetState) {
            level.setBlock(blockPos, blockState.setValue(PORT_STATE, targetState), 3)
        }
    }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val network = network()
        Lang.builder("cbbees").translate("gui.goggles.beehive.network")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("cbbees").text(network.name).style(ChatFormatting.GOLD))
            .forGoggles(tooltip)
        return true
    }
}