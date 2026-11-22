package de.devin.cbbees.api.action

import de.devin.cbbees.content.domain.action.BeeAction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

/**
 * Saves and loads a custom [BeeAction] so queued jobs survive a restart. [id] must be unique, e.g. prefixed with your mod id.
 * @see de.devin.cbbees.compat.immersiveengineering.FormMultiblockAction.Codec
 */
interface BeeActionCodec<T : BeeAction> {
    val id: String
    val type: Class<T>
    fun save(action: T, registries: HolderLookup.Provider): CompoundTag
    fun load(tag: CompoundTag, registries: HolderLookup.Provider): T?
}
