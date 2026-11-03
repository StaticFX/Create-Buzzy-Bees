package de.devin.cbbees.content.domain.job

import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/** Why dispatch can't send a bee to a job. Listed from most to least fundamental. */
enum class StallReason(val translationKey: String) {
    NO_NETWORK("cbbees.stall.no_network"),
    OUT_OF_RANGE("cbbees.stall.out_of_range"),
    NO_FUEL("cbbees.stall.no_fuel"),
    NO_BEES("cbbees.stall.no_bees"),
    NO_BUMBLE_BEES("cbbees.stall.no_bumble_bees"),
    MISSING_MATERIALS("cbbees.stall.missing_materials"),
    NO_DROP_OFF("cbbees.stall.no_drop_off_port"),
    TASKS_FAILED("cbbees.stall.tasks_failed");

    companion object {
        val STREAM_CODEC: StreamCodec<ByteBuf, StallReason> =
            ByteBufCodecs.VAR_INT.map({ entries[it] }, { it.ordinal })
    }
}

/**
 * A job's [reason] for being stuck. For [StallReason.MISSING_MATERIALS] it also names the
 * first few things missing (e.g. "3x Stone") and how many more there are.
 */
data class Stall(val reason: StallReason, val missing: List<Component> = emptyList(), val moreMissing: Int = 0) {

    fun describe(): Component {
        if (missing.isEmpty()) return Component.translatable(reason.translationKey)
        val shown = Component.empty()
        missing.forEachIndexed { i, entry ->
            if (i > 0) shown.append(", ")
            shown.append(entry)
        }
        if (moreMissing > 0) shown.append(" ").append(Component.translatable("cbbees.stall.more", moreMissing))
        return Component.translatable("cbbees.stall.missing", shown)
    }

    companion object {
        const val MAX_NAMED = 5

        /** How long a reason has to last before players see it. */
        const val SHOW_AFTER_TICKS = 40L

        fun missing(entries: List<Component>) =
            Stall(StallReason.MISSING_MATERIALS, entries.take(MAX_NAMED), (entries.size - MAX_NAMED).coerceAtLeast(0))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Stall> = StreamCodec.composite(
            StallReason.STREAM_CODEC, Stall::reason,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NAMED)), Stall::missing,
            ByteBufCodecs.VAR_INT, Stall::moreMissing,
            ::Stall,
        )
    }
}
