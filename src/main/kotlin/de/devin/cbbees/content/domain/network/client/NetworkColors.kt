package de.devin.cbbees.content.domain.network.client

import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.theme.Color
import net.minecraft.util.Mth
import java.util.UUID
import kotlin.math.sin

@ClientSide
object NetworkColors {

    private const val GOLDEN_RATIO = 0.6180339887
    private const val SATURATION = 0.55f
    private const val DARK_VALUE = 0.7f
    private const val LIGHT_VALUE = 1f
    private const val BREATH_SPEED = 0.15f

    fun hue(networkId: UUID): Float = Mth.frac(networkId.hashCode().toUInt().toDouble() * GOLDEN_RATIO).toFloat()

    fun dark(networkId: UUID): Int = Mth.hsvToRgb(hue(networkId), SATURATION, DARK_VALUE)

    fun light(networkId: UUID): Int = Mth.hsvToRgb(hue(networkId), SATURATION, LIGHT_VALUE)

    fun breathing(networkId: UUID, time: Float): Int {
        val blend = (sin(time * BREATH_SPEED) + 1f) / 2f
        return Color.mixColors(dark(networkId), light(networkId), blend)
    }
}
