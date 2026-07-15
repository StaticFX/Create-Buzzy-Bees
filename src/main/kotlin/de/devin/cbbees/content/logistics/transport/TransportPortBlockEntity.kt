package de.devin.cbbees.content.logistics.transport

import com.simibubi.create.AllBlocks as CreateAllBlocks
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.redstone.link.LinkBehaviour
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.domain.logistics.PortReservationManager
import de.devin.cbbees.content.domain.logistics.TransportPort
import de.devin.cbbees.content.logistics.ports.LogisticsPortSelectionValueBox
import de.devin.cbbees.content.logistics.ports.PortState
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
import org.apache.commons.lang3.tuple.Pair
import java.util.*

class TransportPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), TransportPort, IHaveGoggleInformation {

    lateinit var priorityBehavior: ScrollValueBehaviour
    override lateinit var linkBehaviour: LinkBehaviour

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

    var priority = 0

    private val reservationManager = PortReservationManager()

    override fun isProvider(): Boolean =
        blockState.getValue(TransportPortBlock.TRANSPORT_MODE) == TransportMode.PROVIDER

    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = TransportPortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    private fun getAttachedPos(): BlockPos {
        val attachedDir = TransportPortBlock.getConnectedDirection(blockState).opposite
        return blockPos.relative(attachedDir)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        linkBehaviour = LinkBehaviour.receiver(
            this,
            Pair.of(CargoFrequencySlot(true), CargoFrequencySlot(false))
        ) { /* no-op: we don't use redstone signals */ }

        priorityBehavior = ScrollValueBehaviour(
            Component.translatable("gui.cbbees.logistics.priority.title"),
            this,
            LogisticsPortSelectionValueBox()
        )
            .between(0, 256)
            .withCallback { priority = it }

        behaviours.add(linkBehaviour)
        behaviours.add(priorityBehavior)
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
        if (tag.hasUUID("tp_id")) {
            id = tag.getUUID("tp_id")
        }
        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId")
        }
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("tp_id", id)
        tag.putUUID("NetworkId", networkId)
    }

    override fun getItemHandler(level: Level): IItemHandler? = getInventory(level)

    override fun isValidProvider(): Boolean {
        if (!isProvider()) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    override fun isValidRequester(): Boolean {
        if (isProvider()) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    private fun portState() = blockState.getValue(TransportPortBlock.PORT_STATE)

    override fun walkTarget(): WalkTarget = WalkTarget(blockPos, 1.0f, 1)

    fun hasCreativeCrate() =
        level?.let { CreateAllBlocks.CREATIVE_CRATE.has(it.getBlockState(getAttachedPos())) } ?: false

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
        return remaining <= 0
    }

    override fun addItemStack(stack: ItemStack): ItemStack {
        val level = level ?: return stack
        val handler = getItemHandler(level) ?: return stack
        return net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, stack, false)
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

    private fun updateBusyState() {
        val level = level ?: return
        if (level.isClientSide) return
        val currentState = blockState.getValue(TransportPortBlock.PORT_STATE)
        if (currentState == PortState.INVALID) return

        val targetState = if (reservationManager.hasReservations) PortState.BUSY else PortState.VALID
        if (currentState != targetState) {
            level.setBlock(blockPos, blockState.setValue(TransportPortBlock.PORT_STATE, targetState), 3)
        }
    }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val network = network()
        val typeKey = if (isProvider()) "gui.goggles.transport.provider" else "gui.goggles.transport.requester"

        Lang.builder("cbbees").translate(typeKey)
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)

        Lang.builder("cbbees").translate("gui.goggles.beehive.network")
            .style(ChatFormatting.GRAY)
            .add(Lang.builder("cbbees").text(network.name).style(ChatFormatting.GOLD))
            .forGoggles(tooltip, 1)

        return true
    }
}
