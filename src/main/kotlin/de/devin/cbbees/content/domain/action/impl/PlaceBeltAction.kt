package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Places an entire Create belt using the same flow as Schematicannon/BeltConnectorItem.
 *
 * This action consumes the required items (shaft + belt connector) from the bee,
 * calls [BeltConnectorItem.createBelts] to construct the full chain, and then reapplies
 * casing/cover data captured from the schematic.
 */
class PlaceBeltAction(
    override val pos: BlockPos,
    private val end: BlockPos,
    val chain: List<BlockPos>,
    /** Actual belt block states from the schematic, used for ghost rendering. */
    val chainStates: List<BlockState>,
    private val casings: List<BeltBlockEntity.CasingType>,
    private val covers: List<Boolean>,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction, ItemConsumingAction {

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        // Never replace a Mechanical Beehive — abort belt if any position overlaps
        if (chain.any { level.getBlockEntity(it) is BeeHive }) return true

        consumeItems(worker)

        BeltConnectorItem.createBelts(level, pos, end)

        val placedChain = BeltBlock.getBeltChain(level, pos)
        if (placedChain.size != chain.size) {
            placedChain.forEach { beltPos ->
                if (AllBlocks.BELT.has(level.getBlockState(beltPos))) {
                    level.destroyBlock(beltPos, false)
                }
            }
            return false
        }

        placedChain.forEachIndexed { index, beltPos ->
            val beltBE = level.getBlockEntity(beltPos) as? BeltBlockEntity ?: return@forEachIndexed
            val casing = casings.getOrNull(index) ?: BeltBlockEntity.CasingType.NONE
            if (casing != BeltBlockEntity.CasingType.NONE) {
                beltBE.setCasingType(casing)
            }
            beltBE.covered = covers.getOrNull(index) ?: false
            beltBE.setChanged()
            beltBE.sendData()
        }

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
        return "Placing belt from (${pos.x}, ${pos.y}, ${pos.z}) to (${end.x}, ${end.y}, ${end.z})"
    }
}