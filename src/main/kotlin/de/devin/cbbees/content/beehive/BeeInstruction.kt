package de.devin.cbbees.content.beehive

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component

/**
 * Represents an instruction for a stationary beehive.
 */
data class BeeInstruction(
    var type: InstructionType = InstructionType.DECONSTRUCT,
    var beeCount: Int = 1,
    var range: Int = 16
) {
    fun serializeNBT(nbt: CompoundTag) {
        nbt.putString("Type", type.name)
        nbt.putInt("BeeCount", beeCount)
        nbt.putInt("Range", range)
    }

    companion object {
        fun deserializeNBT(nbt: CompoundTag): BeeInstruction {
            return BeeInstruction(
                InstructionType.valueOf(nbt.getString("Type")),
                nbt.getInt("BeeCount"),
                nbt.getInt("Range")
            )
        }
    }
}

enum class InstructionType(val translationKey: String) {
    DECONSTRUCT("gui.cbbees.instruction.deconstruct"),
    // CLEAN_FALLEN_ITEMS("gui.cbbees.instruction.clean_items")
    ;

    val displayName: Component get() = Component.translatable(translationKey)
}
