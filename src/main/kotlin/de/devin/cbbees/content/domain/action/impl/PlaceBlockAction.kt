package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity.CasingType
import com.simibubi.create.content.kinetics.belt.BeltHelper
import com.simibubi.create.foundation.utility.BlockHelper
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Places a block at a given position using Create's [BlockHelper.placeSchematicBlock].
 *
 * This delegates to the same placement method Create's SchematiCannon uses, which handles:
 * - State filtering via [SchematicStateFilterRegistry]
 * - Piston state reset and waterlogging
 * - Special placement for rails (update suppression) and belts
 * - Block entity data loading with coordinate fixup
 * - [Block.setPlacedBy] for multi-block structures (doors, beds, tall flowers)
 * - [IMergeableBE] support for existing block entities
 *
 * @see com.simibubi.create.foundation.utility.BlockHelper.placeSchematicBlock
 */
class PlaceBlockAction(
    override val pos: BlockPos,
    val blockState: BlockState,
    val blockEntityTag: CompoundTag? = null,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction, ItemConsumingAction {

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        if (level.getBlockEntity(pos) is BeeHive) return true

        // Belt tunnels require the belt below to have CASING=true before placement,
        // otherwise canSurvive() fails and the tunnel is immediately destroyed.
        // Replicate BeltTunnelItem.updateCustomBlockEntityTag behavior.
        // Check BEFORE consuming items so a retry doesn't waste materials.
        if (AllBlocks.ANDESITE_TUNNEL.has(blockState) || AllBlocks.BRASS_TUNNEL.has(blockState)) {
            val belt = BeltHelper.getSegmentBE(level, pos.below())
            if (belt == null) return false  // Belt not placed yet — retry later
            if (belt.casing == CasingType.NONE) {
                belt.setCasingType(
                    if (AllBlocks.ANDESITE_TUNNEL.has(blockState)) CasingType.ANDESITE
                    else CasingType.BRASS
                )
            }
        }

        if (!consumeItems(worker)) return false

        val representativeItem = requiredItems.firstOrNull() ?: ItemStack.EMPTY
        BlockHelper.placeSchematicBlock(level, blockState, pos, representativeItem, blockEntityTag)

        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                1, 0.2, 0.2, 0.2, 0.0
            )
        }

        return true
    }

    override fun getDescription(): String {
        val blockName = blockState.block.name.string
        return "Placing $blockName at (${pos.x}, ${pos.y}, ${pos.z})"
    }
}