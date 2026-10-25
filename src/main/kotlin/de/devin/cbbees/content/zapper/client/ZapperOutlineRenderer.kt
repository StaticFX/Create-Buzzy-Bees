package de.devin.cbbees.content.zapper.client

import com.simibubi.create.AllDataComponents
import com.simibubi.create.AllSpecialTextures
import de.devin.cbbees.content.zapper.ZapperBrush
import de.devin.cbbees.content.zapper.shape.ZapperShapes
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllDataComponents as CBBeesDataComponents
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

@ClientSide
@OnlyIn(Dist.CLIENT)
object ZapperOutlineRenderer {

    private const val SLOT = "cbbees_schematic_zapper"
    private const val COLOR = 0xF5B83D
    private const val WORLD_REFRESH_TICKS = 10

    private var lastKey: Any? = null

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val player = Minecraft.getInstance().player ?: return
        val stack = InteractionHand.entries.map(player::getItemInHand).firstOrNull(AllItems.SCHEMATIC_ZAPPER::isIn) ?: return
        val hit = ZapperBrush.raycast(player) ?: return
        val settings = stack.get(CBBeesDataComponents.ZAPPER_SHAPE.get()) ?: return

        val refresh = if (ZapperShapes.byId(settings.shape).dependsOnWorld) player.level().gameTime / WORLD_REFRESH_TICKS else 0
        val key = listOf(
            hit.blockPos, hit.direction, player.direction, settings, refresh,
            stack.get(AllDataComponents.SHAPER_TOOL), stack.get(AllDataComponents.SHAPER_PLACEMENT_OPTIONS),
        )
        if (key == lastKey && Outliner.getInstance().edit(SLOT).isPresent) {
            Outliner.getInstance().keep(SLOT)
            return
        }

        val shot = ZapperBrush.aim(stack, player.level(), player, hit) ?: return
        lastKey = key
        Outliner.getInstance().showCluster(SLOT, shot.positions)
            .colored(COLOR)
            .disableLineNormals()
            .lineWidth(1 / 32f)
            .withFaceTexture(AllSpecialTextures.CHECKERED)
    }
}
