package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.history.BlockSnapshot
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/**
 * Interface for actions bees perform on blocks.
 *
 * Uses [BeeWorker] instead of Entity — works with both the legacy entity system
 * and the lightweight [de.devin.cbbees.content.bee.server.ServerBeeData] system.
 */
interface BeeAction {
    val pos: BlockPos
    val changedPositions: List<BlockPos> get() = listOf(pos)
    fun changedPositions(level: Level): List<BlockPos> = changedPositions
    fun inversion(pos: BlockPos, before: BlockSnapshot): BeeAction? = null
    fun inversion(level: Level, pos: BlockPos, before: BlockSnapshot): BeeAction? = inversion(pos, before)
    fun getWorkTicks(level: Level, context: BeeContext): Int = 0

    fun onStart(worker: BeeWorker) {}
    fun onTick(worker: BeeWorker, tick: Int) {}

    /** Called when this action's task becomes the current task in its batch. */
    fun onActivate(worker: BeeWorker) {}

    fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean

    fun shouldReturnAfter(context: BeeContext): Boolean = true
    fun getPriorityOffset(): Int = 0
    fun getDescription(): String
}