package de.devin.cbbees.gametest.dock

import com.simibubi.create.AllBlocks as CreateAllBlocks
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.content.domain.task.BeeTask
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.logistics.dock.DockPortBlockEntity
import de.devin.cbbees.content.logistics.ports.PortBlock
import de.devin.cbbees.content.logistics.ports.PortState
import de.devin.cbbees.content.logistics.ports.PortType
import de.devin.cbbees.content.schematics.BlockPlacementClassifier
import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.dsl.BeeTestScope
import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestAssertException
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import java.util.UUID

/**
 * Integration tests for fluid handling through Dock Ports.
 *
 * Uses the construction structure (see [de.devin.cbbees.gametest.construction.ConstructionTests])
 * and adds a fluid tank with a Dock Port on top at (5,1,3).
 */
object DockPortTests {

    private const val TEMPLATE = "cbbees:gametest/construction/cbbees_construction"
    private val TANK_POS = BlockPos(5, 1, 3)

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun construct_placesWaterSourceFromDockPort(helper: GameTestHelper) = helper.beeTest {
        scanStructure {}
        setupNetwork()
        val tankPos = helper.absolutePos(TANK_POS)
        placeDockPort(tankPos, PortType.EXTRACT)
        val target = helper.absolutePos(BlockPos(1, 1, 1))
        level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState())

        helper.runAfterDelay(5) {
            fillTank(tankPos, 2000)
            dispatch(JobType.Construction, target) { job ->
                listOf(fluidBatch(target, job))
            }
        }

        awaitSuccess {
            val fluid = level.getFluidState(target)
            check(fluid.isSource && fluid.`is`(Fluids.WATER)) { "Expected a water source at $target" }
            check(tankAmount(level, tankPos) == 1000) { "Tank should hold 1000mb, has ${tankAmount(level, tankPos)}mb" }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun construct_fillsWaterloggedBlock(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork()
        val tankPos = helper.absolutePos(TANK_POS)
        placeDockPort(tankPos, PortType.EXTRACT)
        val target = helper.absolutePos(BlockPos(2, 1, 1))
        val slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true)

        val extractPort = scan.ports.first { it.isValidForPickup() }
        insertItems(extractPort.blockPos.below(), Items.OAK_SLAB, 1)

        helper.runAfterDelay(5) {
            fillTank(tankPos, 1000)
            dispatch(JobType.Construction, target) { job ->
                val place = BeeTask.place(target, slab, listOf(ItemStack(Items.OAK_SLAB)), job = job)
                listOf(TaskBatch(listOf(place), job, target), fluidBatch(target, job))
            }
        }

        awaitSuccess {
            val state = level.getBlockState(target)
            check(state.`is`(Blocks.OAK_SLAB) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                "Expected a waterlogged oak slab at $target, got $state"
            }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun deconstruct_storesWaterInDockPort(helper: GameTestHelper) = helper.beeTest {
        scanStructure {}
        setupNetwork()
        val tankPos = helper.absolutePos(TANK_POS)
        placeDockPort(tankPos, PortType.INSERT)
        val target = helper.absolutePos(BlockPos(1, 1, 1))
        level.setBlockAndUpdate(target, Blocks.WATER.defaultBlockState())

        helper.runAfterDelay(5) {
            GameTestSetup.dispatchDeconstruction(listOf(target), level, jobPool)
        }

        awaitSuccess {
            check(!level.getFluidState(target).isSource) { "Water source at $target should be removed" }
            check(tankAmount(level, tankPos) == 1000) { "Tank should hold 1000mb, has ${tankAmount(level, tankPos)}mb" }
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun deconstruct_storesWholePoolInDockPort(helper: GameTestHelper) = helper.beeTest {
        scanStructure {}
        setupNetwork()
        val tankPos = helper.absolutePos(TANK_POS)
        placeDockPort(tankPos, PortType.INSERT)
        val pool = listOf(BlockPos(1, 1, 1), BlockPos(2, 1, 1), BlockPos(1, 1, 2), BlockPos(2, 1, 2)).map(helper::absolutePos)
        pool.forEach {
            level.setBlockAndUpdate(it.below(), Blocks.STONE.defaultBlockState())
            level.setBlockAndUpdate(it, Blocks.WATER.defaultBlockState())
        }

        helper.runAfterDelay(5) {
            GameTestSetup.dispatchDeconstruction(pool, level, jobPool)
        }

        awaitSuccess {
            check(pool.none { level.getFluidState(it).isSource }) { "All water sources should be removed" }
            check(tankAmount(level, tankPos) == 4000) { "Tank should hold 4000mb, has ${tankAmount(level, tankPos)}mb" }
        }
    }

    private fun BeeTestScope.placeDockPort(tankPos: BlockPos, type: PortType) {
        level.setBlockAndUpdate(tankPos, CreateAllBlocks.FLUID_TANK.defaultState)
        val portPos = tankPos.above()
        val portState = AllBlocks.DOCK_PORT.defaultState
            .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
            .setValue(PortBlock.PORT_TYPE, type)
            .setValue(PortBlock.PORT_STATE, PortState.VALID)
        level.setBlockAndUpdate(portPos, portState)

        val dock = level.getBlockEntity(portPos) as? DockPortBlockEntity
            ?: throw GameTestAssertException("No Dock Port at $portPos")
        ServerBeeNetworkManager.unregisterComponent(dock)
        dock.networkId = jobPool.network.id
        jobPool.network.addComponent(dock)
    }

    private fun BeeTestScope.fillTank(tankPos: BlockPos, amount: Int) {
        val tank = level.getCapability(Capabilities.FluidHandler.BLOCK, tankPos, null)
            ?: throw GameTestAssertException("No fluid tank at $tankPos")
        tank.fill(FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE)
    }

    private fun tankAmount(level: Level, tankPos: BlockPos): Int =
        level.getCapability(Capabilities.FluidHandler.BLOCK, tankPos, null)?.getFluidInTank(0)?.amount ?: 0

    private fun fluidBatch(pos: BlockPos, job: BeeJob): TaskBatch {
        val task = BeeTask.placeFluid(pos, FluidStack(Fluids.WATER, ServerBeeData.FLUID_CAPACITY), job = job)
        return TaskBatch(listOf(task), job, pos, BlockPlacementClassifier.FLUID_PHASE)
    }

    private fun BeeTestScope.dispatch(type: JobType, center: BlockPos, batches: (BeeJob) -> List<TaskBatch>) {
        val job = BeeJob(UUID.randomUUID(), center, level, type)
        job.addBatches(batches(job))
        jobPool.dispatchNewJob(job)
    }
}
