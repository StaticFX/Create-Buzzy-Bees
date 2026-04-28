package de.devin.cbbees.content.beehive

import com.simibubi.create.content.kinetics.base.KineticBlock
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel
import com.simibubi.create.foundation.block.IBE
import de.devin.cbbees.registry.AllBlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.world.Containers
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MechanicalBeehiveBlock(properties: Properties) : KineticBlock(properties), IBE<MechanicalBeehiveBlockEntity>,
    ICogWheel {

    override fun hasShaftTowards(world: LevelReader, pos: BlockPos, state: BlockState, face: Direction): Boolean {
        return false;
    }

    override fun getRotationAxis(state: BlockState): Axis {
        return Axis.Y
    }

    // No right-click GUI — use goggles for network info, click job AABB for job details

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            val be = getBlockEntity(level, pos)
            if (be != null) {
                for (i in 0 until be.beeInventory.slots) {
                    val stack = be.beeInventory.getStackInSlot(i)
                    if (!stack.isEmpty) {
                        Containers.dropItemStack(level, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), stack)
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun getBlockEntityType(): BlockEntityType<out MechanicalBeehiveBlockEntity> {
        return AllBlockEntityTypes.MECHANICAL_BEEHIVE.get()
    }

    override fun getBlockEntityClass(): Class<MechanicalBeehiveBlockEntity> {
        return MechanicalBeehiveBlockEntity::class.java
    }
}
