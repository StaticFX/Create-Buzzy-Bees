package de.devin.cbbees.compat.refinedstorage

import com.refinedmods.refinedstorage.api.autocrafting.calculation.CancellationToken
import com.refinedmods.refinedstorage.api.core.Action
import com.refinedmods.refinedstorage.api.network.Network
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent.EnsureResult
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent
import com.refinedmods.refinedstorage.api.storage.Actor
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider
import com.refinedmods.refinedstorage.common.support.resource.ItemResource
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi
import de.devin.cbbees.content.logistics.storage.CraftResult
import de.devin.cbbees.content.logistics.storage.PortStorage
import de.devin.cbbees.content.logistics.storage.PortStorageProvider
import de.devin.cbbees.content.logistics.storage.toIntClamped
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** The storage of the Refined Storage network the attached block belongs to. */
class RSPortStorage(private val provider: NetworkNodeContainerProvider) : PortStorage {

    private val network: Network?
        get() = provider.containers.firstNotNullOfOrNull { it.node.network }

    private val storage: StorageNetworkComponent?
        get() = network?.getComponent(StorageNetworkComponent::class.java)

    private val autocrafting: AutocraftingNetworkComponent?
        get() = network?.getComponent(AutocraftingNetworkComponent::class.java)

    override fun stacks(): List<ItemStack> = storage?.all.orEmpty().mapNotNull { amount ->
        (amount.resource() as? ItemResource)?.toItemStack(amount.amount().coerceAtMost(Int.MAX_VALUE.toLong()))
    }

    override fun count(stack: ItemStack): Int = storage?.get(ItemResource.ofItemStack(stack))?.toIntClamped() ?: 0

    override fun extract(stack: ItemStack): Boolean {
        val storage = storage ?: return false
        val resource = ItemResource.ofItemStack(stack)
        val amount = stack.count.toLong()
        if (storage.extract(resource, amount, Action.SIMULATE, Actor.EMPTY) < amount) return false
        return storage.extract(resource, amount, Action.EXECUTE, Actor.EMPTY) == amount
    }

    override fun insert(stack: ItemStack, simulate: Boolean): ItemStack {
        val storage = storage ?: return stack
        val action = if (simulate) Action.SIMULATE else Action.EXECUTE
        val inserted = storage.insert(ItemResource.ofItemStack(stack), stack.count.toLong(), action, Actor.EMPTY)
        return stack.copyWithCount(stack.count - inserted.toInt())
    }

    override fun hasSpace(): Boolean = storage != null

    override val canCraft: Boolean get() = autocrafting != null

    override fun isCrafting(stack: ItemStack): Boolean {
        val resource = ItemResource.ofItemStack(stack)
        return autocrafting?.statuses.orEmpty().any { it.info().resource() == resource }
    }

    override fun craft(stack: ItemStack, amount: Int): CraftResult {
        val autocrafting = autocrafting ?: return CraftResult.UNAVAILABLE
        val resource = ItemResource.ofItemStack(stack)
        if (autocrafting.getPatternsByOutput(resource).isEmpty()) return CraftResult.UNAVAILABLE
        val result = autocrafting.ensureTask(resource, amount.toLong(), Actor.EMPTY, CancellationToken.NONE)
        return if (result == EnsureResult.MISSING_RESOURCES) CraftResult.UNAVAILABLE else CraftResult.CRAFTING
    }

    companion object : PortStorageProvider {
        override fun find(level: Level, pos: BlockPos, side: Direction): PortStorage? = level.getCapability(
            RefinedStorageNeoForgeApi.INSTANCE.networkNodeContainerProviderCapability, pos, side
        )?.let(::RSPortStorage)
    }
}
