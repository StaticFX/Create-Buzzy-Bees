package de.devin.cbbees.content.zapper.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import com.simibubi.create.content.equipment.zapper.ZapperItemRenderer
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.util.ClientSide
import dev.engine_room.flywheel.lib.model.baked.PartialModel
import net.createmod.catnip.animation.AnimationTickHolder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@ClientSide
@OnlyIn(Dist.CLIENT)
class SchematicZapperItemRenderer : ZapperItemRenderer() {

    override fun render(
        stack: ItemStack,
        model: CustomRenderedItemModel,
        renderer: PartialItemModelRenderer,
        transformType: ItemDisplayContext,
        ms: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        super.render(stack, model, renderer, transformType, ms, buffer, light, overlay)

        val pt = AnimationTickHolder.getPartialTicks()
        val worldTime = AnimationTickHolder.getRenderTime() / 20

        renderer.renderSolid(model.originalModel, light)

        val player = Minecraft.getInstance().player ?: return
        val mainHand = player.mainHandItem == stack
        val held = mainHand || player.offhandItem == stack
        val animation = getAnimationProgress(pt, player.mainArm == HumanoidArm.LEFT, mainHand)

        val multiplier = if (held) animation else Mth.sin(worldTime * 5)
        val intensity = (15 * Mth.clamp(multiplier, 0f, 1f)).toInt()
        val glowLight = LightTexture.pack(intensity, maxOf(intensity, 4))
        renderer.renderSolidGlowing(CORE.get(), glowLight)
        renderer.renderGlowing(CORE_GLOW.get(), glowLight)

        val angle = (worldTime * -25 + if (held) 360 * animation else 0f) % 360
        ms.translate(0f, ACCELERATOR_OFFSET, 0f)
        ms.mulPose(Axis.ZP.rotationDegrees(angle))
        ms.translate(0f, -ACCELERATOR_OFFSET, 0f)
        renderer.render(ACCELERATOR.get(), light)
    }

    companion object {
        private const val ACCELERATOR_OFFSET = -.155f

        private val CORE = partial("core")
        private val CORE_GLOW = partial("core_glow")
        private val ACCELERATOR = partial("accelerator")

        private fun partial(name: String) =
            PartialModel.of(CreateBuzzyBeez.asResource("item/schematic_zapper/$name"))
    }
}
