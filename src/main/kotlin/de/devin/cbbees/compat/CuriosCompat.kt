package de.devin.cbbees.compat

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList

/**
 * Optional Curios integration. All calls are guarded by [isLoaded] so the mod
 * works without Curios installed — no class references are resolved unless
 * Curios is actually present.
 */
object CuriosCompat {

    val isLoaded: Boolean by lazy { ModList.get().isLoaded("curios") }

    /**
     * Finds the first equipped Curios item matching the predicate.
     * Returns [ItemStack.EMPTY] if Curios is not installed or no match found.
     */
    fun findFirstCurio(player: Player, predicate: (ItemStack) -> Boolean): ItemStack {
        if (!isLoaded) return ItemStack.EMPTY
        return CuriosCompatImpl.findFirstCurio(player, predicate)
    }
}

/**
 * Isolated in its own object so [CuriosApi] is only class-loaded when
 * [CuriosCompat.isLoaded] is true.
 */
private object CuriosCompatImpl {
    fun findFirstCurio(player: Player, predicate: (ItemStack) -> Boolean): ItemStack {
        val result = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
            .flatMap { handler ->
                handler.findFirstCurio(java.util.function.Predicate { stack -> predicate(stack) })
            }
        return result.map { it.stack() }.orElse(ItemStack.EMPTY)
    }
}
