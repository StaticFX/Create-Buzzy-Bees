package de.devin.cbbees.content.logistics.dock

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.domain.logistics.DockPort
import de.devin.cbbees.content.domain.network.addNetworkTooltip
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.LogisticsPortFilterValueBox
import de.devin.cbbees.content.logistics.ports.LogisticsPortSelectionValueBox
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.ports.PortType
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.util.UUID

class DockPortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), DockPort, IHaveGoggleInformation {

    lateinit var filteringBehavior: FilteringBehaviour

    lateinit var priorityBehavior: ScrollValueBehaviour

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

    private var busyUntilTick: Long = 0L

    fun getFluidHandler(level: Level): IFluidHandler? {
        val attachedDir = PortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.FluidHandler.BLOCK, attachedPos, attachedDir.opposite)
    }

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        filteringBehavior = FilteringBehaviour(this, LogisticsPortFilterValueBox()).forFluids()
        priorityBehavior = ScrollValueBehaviour(
            Component.translatable("gui.cbbees.logistics.priority.title"),
            this,
            LogisticsPortSelectionValueBox()
        )
            .between(0, 256)
            .withCallback { priority = it }

        behaviours.add(priorityBehavior)
        behaviours.add(filteringBehavior)
    }

    override fun onLoad() {
        super.onLoad()
        level?.let(::addToNetwork)
    }

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
        if (tag.hasUUID("dp_id")) id = tag.getUUID("dp_id")
        networkId = if (tag.hasUUID("NetworkId")) tag.getUUID("NetworkId") else null
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("dp_id", id)
        networkId?.let { tag.putUUID("NetworkId", it) }
    }

    override fun priority(): Int = priority

    private fun portType() = blockState.getValue(PortBlock.PORT_TYPE)

    private fun portState() = blockState.getValue(PortBlock.PORT_STATE)

    override fun isValidForPickup(): Boolean = portType() == PortType.EXTRACT && portState() != PortState.INVALID

    override fun isValidForDropOff(): Boolean = portType() == PortType.INSERT && portState() != PortState.INVALID

    override fun testFilter(fluid: FluidStack): Boolean = filteringBehavior.test(fluid)

    override fun hasFluid(fluid: FluidStack): Boolean {
        val handler = getFluidHandler(world) ?: return false
        return handler.drain(fluid, FluidAction.SIMULATE).amount >= fluid.amount
    }

    override fun drainFluid(fluid: FluidStack): Boolean {
        val handler = getFluidHandler(world) ?: return false
        if (handler.drain(fluid, FluidAction.SIMULATE).amount < fluid.amount) return false
        handler.drain(fluid, FluidAction.EXECUTE)
        pulseBusy()
        return true
    }

    override fun canFill(fluid: FluidStack): Boolean {
        val handler = getFluidHandler(world) ?: return false
        return handler.fill(fluid, FluidAction.SIMULATE) >= fluid.amount
    }

    override fun fillFluid(fluid: FluidStack): Int {
        val handler = getFluidHandler(world) ?: return 0
        val filled = handler.fill(fluid, FluidAction.EXECUTE)
        if (filled > 0) pulseBusy()
        return filled
    }

    private fun pulseBusy() {
        val level = level ?: return
        if (level.isClientSide || portState() == PortState.INVALID) return
        busyUntilTick = level.gameTime + 20
        if (portState() != PortState.BUSY) {
            level.setBlock(blockPos, blockState.setValue(PortBlock.PORT_STATE, PortState.BUSY), 3)
        }
    }

    override fun lazyTick() {
        super.lazyTick()
        val level = level ?: return
        if (level.isClientSide || busyUntilTick == 0L || level.gameTime < busyUntilTick) return
        busyUntilTick = 0L
        if (portState() == PortState.BUSY) {
            level.setBlock(blockPos, blockState.setValue(PortBlock.PORT_STATE, PortState.VALID), 3)
        }
    }

    override fun sync() {
        setChanged()
        sendData()
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder("cbbees").translate("gui.goggles.dock.${portType().serializedName}")
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)

        addNetworkTooltip(tooltip, 1)
        return true
    }
}
