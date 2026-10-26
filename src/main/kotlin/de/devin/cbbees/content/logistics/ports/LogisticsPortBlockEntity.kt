package de.devin.cbbees.content.logistics.ports

import com.mojang.serialization.Codec
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.logistics.BigItemStack
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour
import de.devin.cbbees.content.domain.network.addNetworkTooltip
import de.devin.cbbees.content.domain.logistics.LogisticsPort
import de.devin.cbbees.content.logistics.ports.PortBlock.Companion.PORT_STATE
import de.devin.cbbees.content.logistics.storage.PortStorage
import de.devin.cbbees.content.logistics.storage.PortStorages
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
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

    override var networkId: UUID? = null
        set(value) {
            if (field == value) return
            val old = field
            field = value
            onNetworkIdChanged(old, value)
        }

    var filterStack: ItemStack = ItemStack.EMPTY
    var crafting: List<BigItemStack> = emptyList()
        private set
    var priority = 0

    private val reservationManager = de.devin.cbbees.content.domain.logistics.PortReservationManager()

    fun getInventory(level: Level): IItemHandler? {
        val attachedDir = PortBlock.getConnectedDirection(blockState).opposite
        val attachedPos = blockPos.relative(attachedDir)
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attachedPos, attachedDir.opposite)
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
        networkId = if (tag.hasUUID("NetworkId")) tag.getUUID("NetworkId") else null
        if (tag.contains("Filter")) {
            filterStack = ItemStack.parseOptional(registries, tag.getCompound("Filter"))
        }
        if (clientPacket) crafting = ORDER_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Crafting")).result().orElse(emptyList())
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("lp_id", id)
        networkId?.let { tag.putUUID("NetworkId", it) }
        if (!filterStack.isEmpty) {
            tag.put("Filter", filterStack.save(registries))
        }
        if (clientPacket) ORDER_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), crafting).result().ifPresent { tag.put("Crafting", it) }
    }

    override fun showCrafting(order: List<BigItemStack>) {
        if (order.map { it.stack.item to it.count } == crafting.map { it.stack.item to it.count }) return
        crafting = order
        sendData()
    }

    override fun getFilter(): ItemStack {
        return filterStack
    }

    override fun storage(): PortStorage? {
        val level = level ?: return null
        val attachedDir = PortBlock.getConnectedDirection(blockState).opposite
        return PortStorages.find(level, blockPos.relative(attachedDir), attachedDir.opposite)
    }

    override fun getPortType(): PortType = portType()

    override fun isValidForDropOff(): Boolean {
        if (portType() != PortType.INSERT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    private fun portState() = blockState.getValue(PORT_STATE)

    private fun portType() = blockState.getValue(PortBlock.PORT_TYPE)

    override fun isValidForPickup(): Boolean {
        if (portType() != PortType.EXTRACT) return false
        if (portState() == PortState.INVALID) return false
        return true
    }

    override fun testFilter(stack: ItemStack): Boolean {
        return filteringBehavior.test(stack)
    }

    override fun walkTarget(): WalkTarget {
        return WalkTarget(blockPos, 1.0f, 1)
    }

    override fun hasItemStack(stack: ItemStack): Boolean = (storage()?.count(stack) ?: 0) > 0

    override fun removeItemStack(stack: ItemStack): Boolean {
        if (storage()?.extract(stack) != true) return false
        pulseBusy()
        return true
    }

    override fun addItemStack(stack: ItemStack): ItemStack {
        val result = storage()?.insert(stack) ?: return stack
        if (result.count < stack.count) pulseBusy()
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

    override fun hasAvailableItemStack(stack: ItemStack, excludeBeeId: UUID?): Boolean =
        (storage()?.count(stack) ?: 0) - reservationManager.getReservedCount(stack, excludeBeeId) >= stack.count

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
        Lang.builder("cbbees").translate("gui.goggles.logistics.${portType().serializedName}")
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)

        if (crafting.isNotEmpty()) {
            Lang.builder("cbbees").translate("gui.goggles.logistics.crafting").style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
            crafting.take(MAX_SHOWN_CRAFTING_LINES).forEach {
                Lang.builder("cbbees").text("${it.count}x ").add(it.stack.hoverName.copy()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 2)
            }
        }

        addNetworkTooltip(tooltip, 1)
        return true
    }

    private companion object {
        val ORDER_CODEC: Codec<List<BigItemStack>> = BigItemStack.CODEC.listOf()
        const val MAX_SHOWN_CRAFTING_LINES = 4
    }
}