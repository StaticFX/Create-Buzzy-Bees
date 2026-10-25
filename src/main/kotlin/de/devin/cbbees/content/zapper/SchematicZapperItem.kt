package de.devin.cbbees.content.zapper

import com.simibubi.create.content.equipment.zapper.PlacementPatterns
import com.simibubi.create.content.equipment.zapper.terrainzapper.WorldshaperItem
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer
import com.simibubi.create.foundation.utility.CreateLang
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.zapper.client.SchematicZapperItemRenderer
import de.devin.cbbees.content.zapper.client.ZapperShapeScreen
import de.devin.cbbees.registry.AllDataComponents
import net.createmod.catnip.gui.ScreenOpener
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import java.util.function.Consumer

class SchematicZapperItem(properties: Properties) : WorldshaperItem(properties) {

    override fun activate(
        level: Level,
        player: Player,
        item: ItemStack,
        stateToUse: BlockState,
        raytrace: BlockHitResult,
        data: CompoundTag?
    ): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        val shot = ZapperBrush.aim(item, level, player, raytrace) ?: return false

        val limit = CBBeesConfig.zapperMaxBlocksPerShot.get()
        if (shot.positions.size > limit) {
            player.displayClientMessage(Component.translatable("cbbees.zapper.too_big", shot.positions.size, limit), true)
            return false
        }

        PlacementPatterns.applyPattern(shot.positions, item, level.random)
        val edits = ShapingTools.of(shot.tool).plan(level, shot.positions, shot.face, stateToUse)
        ZapperJobRequest(shot.origin, edits, stateToUse, data).submit(serverPlayer)
        return true
    }

    override fun validateUsage(item: ItemStack): Component? {
        if (!item.has(AllDataComponents.ZAPPER_SHAPE.get())) return CreateLang.translateDirect("terrainzapper.shiftRightClickToSet")
        return super.validateUsage(item)
    }

    @OnlyIn(Dist.CLIENT)
    override fun openHandgunGUI(item: ItemStack, hand: InteractionHand) {
        ScreenOpener.open(ZapperShapeScreen(item, hand))
    }

    override fun initializeClient(consumer: Consumer<IClientItemExtensions>) {
        consumer.accept(SimpleCustomRenderer.create(this, SchematicZapperItemRenderer()))
    }

    override fun getZappingRange(stack: ItemStack) = ZapperBrush.RANGE

    override fun getCooldownDelay(item: ItemStack) = 20
}
