package de.devin.cbbees.gametest.compat

import de.devin.cbbees.content.schematics.BlockTransforms
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

object RefinedStorageSchematicTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun block(id: String): Block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("refinedstorage", id))

    private fun directionProperty(state: BlockState) = state.properties.first { it.name == "direction" }

    private fun facing(state: BlockState): String = directionProperty(state).let { nameOf(state, it) }

    private fun <T : Comparable<T>> nameOf(state: BlockState, property: Property<T>) = property.getName(state.getValue(property))

    private fun facing(id: String, direction: String): BlockState = block(id).defaultBlockState().let { withName(it, directionProperty(it), direction) }

    private fun <T : Comparable<T>> withName(state: BlockState, property: Property<T>, name: String) = state.setValue(property, property.getValue(name).get())

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun facings_turnWithTheSchematic(helper: GameTestHelper) = helper.unitTest {
        assertEquals("north", facing(BlockTransforms.rotate(facing("importer", "north"), Rotation.NONE)!!), "no rotation keeps the facing")
        assertEquals("east", facing(BlockTransforms.rotate(facing("importer", "north"), Rotation.CLOCKWISE_90)!!), "importer turns clockwise")
        assertEquals("up", facing(BlockTransforms.rotate(facing("importer", "up"), Rotation.CLOCKWISE_90)!!), "up stays up")
        assertEquals("up_east", facing(BlockTransforms.rotate(facing("grid", "up_north"), Rotation.CLOCKWISE_90)!!), "grid lying down turns too")
        assertEquals("south", facing(BlockTransforms.mirror(facing("importer", "north"), Mirror.LEFT_RIGHT)!!), "mirroring flips the facing")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun placingASchematic_keepsTheFacing(helper: GameTestHelper) = helper.unitTest {
        val origin = helper.absolutePos(BlockPos(0, 1, 0))
        helper.level.setBlock(origin, facing("importer", "north"), Block.UPDATE_ALL)
        assertEquals("north", facing(helper.level.getBlockState(origin)), "the original faces north")
        val template = StructureTemplate().apply { fillFromWorld(helper.level, origin, Vec3i(1, 1, 1), false, null) }

        val unrotated = helper.absolutePos(BlockPos(2, 1, 0))
        template.placeInWorld(helper.level, unrotated, unrotated, StructurePlaceSettings(), helper.level.random, Block.UPDATE_ALL)
        assertEquals("north", facing(helper.level.getBlockState(unrotated)), "placed without rotation")

        val rotated = helper.absolutePos(BlockPos(2, 1, 2))
        template.placeInWorld(helper.level, rotated, rotated, StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), helper.level.random, Block.UPDATE_ALL)
        assertEquals("east", facing(helper.level.getBlockState(rotated)), "placed turned clockwise")
    }
}
