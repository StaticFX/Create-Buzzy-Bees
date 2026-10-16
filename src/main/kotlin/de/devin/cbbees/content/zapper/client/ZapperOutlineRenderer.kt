package de.devin.cbbees.content.zapper.client

import com.simibubi.create.AllSpecialTextures
import de.devin.cbbees.content.zapper.ZapperBrush
import de.devin.cbbees.items.AllItems
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

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val player = Minecraft.getInstance().player ?: return
        val stack = InteractionHand.entries.map(player::getItemInHand).firstOrNull(AllItems.SCHEMATIC_ZAPPER::isIn) ?: return
        val hit = ZapperBrush.raycast(player) ?: return
        val shot = ZapperBrush.aim(stack, player.level(), player, hit) ?: return

        Outliner.getInstance().showCluster(SLOT, shot.positions)
            .colored(COLOR)
            .disableLineNormals()
            .lineWidth(1 / 32f)
            .withFaceTexture(AllSpecialTextures.CHECKERED)
    }
}
