package de.devin.cbbees.compat.ae2

import appeng.api.networking.IGridNode
import appeng.api.networking.crafting.CalculationStrategy
import appeng.api.networking.crafting.ICraftingPlan
import appeng.api.networking.crafting.ICraftingService
import appeng.api.networking.crafting.ICraftingSimulationRequester
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEItemKey
import de.devin.cbbees.content.logistics.storage.CraftResult
import net.minecraft.world.level.Level
import java.util.WeakHashMap
import java.util.concurrent.Future

/**
 * AE2 plans crafting jobs asynchronously. Running plans are kept per grid until they finish,
 * then submitted on the next request for the same item.
 */
object MECrafting {

    private val source = IActionSource.empty()
    private val calculations = WeakHashMap<ICraftingService, MutableMap<AEItemKey, Future<ICraftingPlan>>>()

    fun request(level: Level, node: IGridNode, key: AEItemKey, amount: Long): CraftResult {
        val crafting = node.grid.craftingService
        val pending = calculations.getOrPut(crafting, ::HashMap)
        val calculation = pending[key]
        if (calculation == null) {
            if (crafting.isRequesting(key)) return CraftResult.CRAFTING
            pending[key] = crafting.beginCraftingCalculation(level, requester(node), key, amount, CalculationStrategy.CRAFT_LESS)
            return CraftResult.PLANNING
        }
        if (!calculation.isDone) return CraftResult.PLANNING

        pending.remove(key)
        val plan = runCatching { calculation.get() }.getOrNull()
        if (plan == null || plan.simulation()) return CraftResult.UNAVAILABLE
        val submitted = crafting.submitJob(plan, null, null, false, source).successful()
        return if (submitted) CraftResult.CRAFTING else CraftResult.UNAVAILABLE
    }

    private fun requester(node: IGridNode) = object : ICraftingSimulationRequester {
        override fun getActionSource(): IActionSource = source
        override fun getGridNode(): IGridNode = node
    }
}
