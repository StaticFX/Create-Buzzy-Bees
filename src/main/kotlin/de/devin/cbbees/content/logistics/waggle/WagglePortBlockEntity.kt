package de.devin.cbbees.content.logistics.waggle

import com.mojang.serialization.Codec
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.logistics.BigItemStack
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.domain.job.MissingMaterials
import de.devin.cbbees.content.domain.network.INetworkComponent
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.network.addNetworkTooltip
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

class WagglePortBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    SmartBlockEntity(type, pos, state), INetworkComponent, IHaveGoggleInformation {

    lateinit var link: LogisticallyLinkedBehaviour

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

    var address = ""
        set(value) {
            field = value.take(MAX_ADDRESS_LENGTH)
            if (level?.isClientSide == false) ServerBeeNetworkManager.getNetworkFor(this)?.pendingRequests?.clear(id)
        }

    private var nextRequestTick = 0L
    var lastOrder: List<BigItemStack> = emptyList()
        private set

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        link = LogisticallyLinkedBehaviour(this, false)
        behaviours.add(link)
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

    override fun tick() {
        super.tick()
        val level = level ?: return
        if (level.isClientSide || level.gameTime < nextRequestTick) return
        nextRequestTick = level.gameTime + CBBeesConfig.waggleRequestInterval.get()
        requestMissing(level.gameTime)
    }

    fun requestMissing(now: Long): List<BigItemStack> {
        if (address.isBlank()) return emptyList()
        val network = ServerBeeNetworkManager.getNetworkFor(this) ?: return emptyList()
        val missing = MissingMaterials.forNetwork(network)
        if (missing.isEmpty()) return emptyList()

        val stock = LogisticsManager.getSummaryOfNetwork(link.freqId, true)
        val order = network.pendingRequests.plan(missing, stock::getCountOf, now)
        if (order.isEmpty()) return emptyList()
        if (!LogisticsManager.broadcastPackageRequest(link.freqId, RequestType.REDSTONE, PackageOrderWithCrafts.simple(order), null, address))
            return emptyList()

        network.pendingRequests.record(id, order, now + CBBeesConfig.waggleRequestTimeout.get())
        lastOrder = order
        sendData()
        return order
    }

    override fun isAnchor(): Boolean = false

    override fun getNetworkingRange(): Double = 0.0

    override fun isInWorkRange(pos: BlockPos): Boolean = false

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        if (tag.hasUUID("WaggleId")) id = tag.getUUID("WaggleId")
        networkId = if (tag.hasUUID("NetworkId")) tag.getUUID("NetworkId") else null
        address = tag.getString("Address")
        if (clientPacket) lastOrder = ORDER_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("LastOrder")).result().orElse(emptyList())
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putUUID("WaggleId", id)
        networkId?.let { tag.putUUID("NetworkId", it) }
        tag.putString("Address", address)
        if (clientPacket) ORDER_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), lastOrder).result().ifPresent { tag.put("LastOrder", it) }
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder("cbbees").translate("gui.goggles.waggle_port")
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)

        val addressLine = if (address.isBlank()) Lang.builder("cbbees").translate("gui.goggles.waggle_port.no_address").style(ChatFormatting.RED)
        else Lang.builder("cbbees").translate("gui.goggles.waggle_port.address", address).style(ChatFormatting.GRAY)
        addressLine.forGoggles(tooltip, 1)

        if (lastOrder.isNotEmpty()) {
            Lang.builder("cbbees").translate("gui.goggles.waggle_port.last_order").style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
            lastOrder.take(MAX_SHOWN_ORDER_LINES).forEach {
                Lang.builder("cbbees").text("${it.count}x ").add(it.stack.hoverName.copy()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 2)
            }
        }

        addNetworkTooltip(tooltip, 1)
        return true
    }

    private companion object {
        val ORDER_CODEC: Codec<List<BigItemStack>> = BigItemStack.CODEC.listOf()
        const val MAX_ADDRESS_LENGTH = 64
        const val MAX_SHOWN_ORDER_LINES = 4
    }
}
