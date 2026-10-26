package de.devin.cbbees.content.logistics.ports

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType

/**
 * Like Create's additive render type, but without depth writes, so the glow around a bulb
 * does not hide the bulb inside it.
 */
object PortRenderTypes : RenderStateShard("cbbees_port_render_types", {}, {}) {

    val GLOW: RenderType = RenderType.create(
        "cbbees_port_glow", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 256, true, true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_SOLID_SHADER)
            .setTextureState(BLOCK_SHEET)
            .setTransparencyState(ADDITIVE_TRANSPARENCY)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(true)
    )
}
