package de.devin.cbbees.content.zapper.shape

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class ShapeSettings(
    val shape: String,
    val params: List<Int>,
    val hollow: Boolean = false,
    val diagonals: Boolean = false,
    val fuzzy: Boolean = false,
) {
    fun param(index: Int): Int = params.getOrElse(index) { 1 }

    fun clampedTo(shape: ZapperShape): ShapeSettings = copy(
        shape = shape.id,
        params = shape.params.mapIndexed { i, param -> param(i).coerceIn(param.min, param.max()) },
        hollow = hollow && shape.canBeHollow,
    )

    companion object {
        val DEFAULT = ShapeSettings("cuboid", listOf(3, 3, 3))

        val CODEC: Codec<ShapeSettings> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("shape").forGetter(ShapeSettings::shape),
                Codec.INT.listOf().fieldOf("params").forGetter(ShapeSettings::params),
                Codec.BOOL.optionalFieldOf("hollow", false).forGetter(ShapeSettings::hollow),
                Codec.BOOL.optionalFieldOf("diagonals", false).forGetter(ShapeSettings::diagonals),
                Codec.BOOL.optionalFieldOf("fuzzy", false).forGetter(ShapeSettings::fuzzy),
            ).apply(instance, ::ShapeSettings)
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, ShapeSettings> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ShapeSettings::shape,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ShapeSettings::params,
            ByteBufCodecs.BOOL, ShapeSettings::hollow,
            ByteBufCodecs.BOOL, ShapeSettings::diagonals,
            ByteBufCodecs.BOOL, ShapeSettings::fuzzy,
            ::ShapeSettings
        )
    }
}
