package de.devin.cbbees.compat.immersiveengineering

import blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler
import de.devin.cbbees.api.action.BeeActionCodec
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/**
 * Forms an IE multiblock at its trigger block, as the Engineer's Hammer would when clicking [side].
 * If the structure doesn't match, e.g. because a part couldn't be placed, it is left unformed.
 */
class FormMultiblockAction(
    override val pos: BlockPos,
    val multiblock: ResourceLocation,
    val side: Direction,
) : BeeAction {

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        val formed = MultiblockHandler.getByUniqueName(multiblock)?.createStructure(level, pos, side, null) ?: false
        if (formed && level is ServerLevel) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 8, 0.6, 0.6, 0.6, 0.0)
        }
        return true
    }

    object Codec : BeeActionCodec<FormMultiblockAction> {
        override val id = "ie_form_multiblock"
        override val type = FormMultiblockAction::class.java

        override fun save(action: FormMultiblockAction, registries: HolderLookup.Provider) = CompoundTag().apply {
            put("Pos", NbtUtils.writeBlockPos(action.pos))
            putString("Multiblock", action.multiblock.toString())
            putString("Side", action.side.serializedName)
        }

        override fun load(tag: CompoundTag, registries: HolderLookup.Provider): FormMultiblockAction? {
            val pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null) ?: return null
            val multiblock = ResourceLocation.tryParse(tag.getString("Multiblock")) ?: return null
            val side = Direction.byName(tag.getString("Side")) ?: return null
            return FormMultiblockAction(pos, multiblock, side)
        }
    }
}
