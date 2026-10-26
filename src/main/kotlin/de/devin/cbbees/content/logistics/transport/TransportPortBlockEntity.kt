package de.devin.cbbees.content.logistics.transport

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.redstone.link.LinkBehaviour
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.domain.network.addNetworkTooltip
import de.devin.cbbees.content.domain.logistics.PortReservationManager
import de.devin.cbbees.content.domain.logistics.TransportPort
import de.devin.cbbees.content.logistics.ports.LogisticsPortSelectionValueBox
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.storage.PortStorage
import de.devin.cbbees.content.logistics.storage.PortStorages
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
import org.apache.commons.lang3.tuple.Pair
import java.util.*

class TransportPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), TransportPort, IHaveGoggleInformation {

    lateinit var priorityBehavior: ScrollValueBehaviour
    override lateinit var linkBehaviour: LinkBehaviour

    override var id: UUID = UUID.randomUUID()
    override val world: Level get() = getLevel()!!
    override val pos: BlockPos get() = blockPos

    override var networkId: UUID? = null
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
        networkId = if (tag.hasUUID("NetworkId")) tag.getUUID("NetworkId") else null
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("tp_id", id)
        networkId?.let { tag.putUUID("NetworkId", it) }
    }

    override fun storage(): PortStorage? {
        val level = level ?: return null
        val attachedDir = TransportPortBlock.getConnectedDirection(blockState).opposite
        return PortStorages.find(level, blockPos.relative(attachedDir), attachedDir.opposite)
    }

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

    override fun hasItemStack(stack: ItemStack): Boolean = (storage()?.count(stack) ?: 0) > 0

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean =
        (storage()?.count(stack) ?: 0) - reservationManager.getReservedCount(stack, excludeBeeId) >= stack.count

    override fun removeItemStack(stack: ItemStack): Boolean = storage()?.extract(stack) ?: false

    override fun addItemStack(stack: ItemStack): ItemStack = storage()?.insert(stack) ?: stack

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
        val typeKey = if (isProvider()) "gui.goggles.transport.provider" else "gui.goggles.transport.requester"

        Lang.builder("cbbees").translate(typeKey)
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)

        addNetworkTooltip(tooltip, 1)

        return true
    }
}
