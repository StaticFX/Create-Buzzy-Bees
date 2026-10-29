package de.devin.cbbees.gametest.zapper

import com.simibubi.create.content.equipment.zapper.terrainzapper.CuboidBrush
import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import com.simibubi.create.content.equipment.zapper.terrainzapper.SphereBrush
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.zapper.shape.ShapeAim
import de.devin.cbbees.content.zapper.shape.ShapeSettings
import de.devin.cbbees.content.zapper.shape.ZapperShapes
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper

object ZapperShapeTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun settings(shape: String, vararg params: Int, hollow: Boolean = false, open: Boolean = false) =
        ShapeSettings(shape, params.toList(), hollow, open = open)

    private fun Collection<BlockPos>.neighborsOf(pos: BlockPos) = Direction.entries.map(pos::relative).filter { it in this }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun cuboid_matchesCreatesCuboidBrush(helper: GameTestHelper) = helper.unitTest {
        val create = ArrayList<BlockPos>().also {
            CuboidBrush().apply { set(3, 4, 5) }.addToGlobalPositions(helper.level, BlockPos.ZERO, Direction.UP, it, TerrainTools.Fill)
        }
        assertEquals(create.toSet(), ZapperShapes.CUBOID.offsets(settings("cuboid", 3, 4, 5)).toSet(), "cuboid positions")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun ellipsoid_withEqualRadiiMatchesCreatesSphere(helper: GameTestHelper) = helper.unitTest {
        val create = ArrayList<BlockPos>().also {
            SphereBrush().apply { set(4, 0, 0) }.addToGlobalPositions(helper.level, BlockPos.ZERO, Direction.UP, it, TerrainTools.Fill)
        }
        assertEquals(create.toSet(), ZapperShapes.ELLIPSOID.offsets(settings("ellipsoid", 4, 4, 4)).toSet(), "sphere positions")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun hollow_keepsOnlyTheShell(helper: GameTestHelper) = helper.unitTest {
        val solid = ZapperShapes.ELLIPSOID.offsets(settings("ellipsoid", 6, 4, 5)).toSet()
        val shell = ZapperShapes.ELLIPSOID.offsets(settings("ellipsoid", 6, 4, 5, hollow = true)).toSet()

        assertTrue(shell.size < solid.size, "shell (${shell.size}) should be smaller than solid (${solid.size})")
        assertTrue(solid.containsAll(shell), "shell must be part of the solid shape")
        assertTrue(shell.all { solid.neighborsOf(it).size < 6 }, "every shell block touches the outside")
        assertTrue((solid - shell).all { solid.neighborsOf(it).size == 6 }, "every removed block was fully enclosed")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun wallsOnly_dropsCeilingAndFloor(helper: GameTestHelper) = helper.unitTest {
        val room = ZapperShapes.CUBOID.offsets(settings("cuboid", 5, 5, 5, open = true)).toSet()
        assertEquals(5 * 16, room.size, "five layers of a 5x5 ring")
        assertFalse(BlockPos(0, 2, 0) in room, "no ceiling")
        assertFalse(BlockPos(0, -2, 0) in room, "no floor")
        assertTrue(BlockPos(2, 2, 2) in room, "walls reach the top corner")

        val shell = ZapperShapes.ELLIPSOID.offsets(settings("ellipsoid", 6, 6, 6, hollow = true)).toSet()
        val sphere = ZapperShapes.ELLIPSOID.offsets(settings("ellipsoid", 6, 6, 6, open = true)).toSet()
        assertTrue(shell.containsAll(sphere), "walls are part of the shell")
        assertFalse(BlockPos(0, 6, 0) in sphere, "sphere is open at the top")
        assertTrue(BlockPos(6, 0, 0) in sphere, "sphere keeps its equator")
        assertTrue((-5..5).all { y -> sphere.any { it.y == y } }, "every layer keeps its wall")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun hollowDome_isAnEnvelopeOpenAtTheBottom(helper: GameTestHelper) = helper.unitTest {
        val dome = ZapperShapes.DOME.offsets(settings("dome", 5, 6, 5, hollow = true)).toSet()

        assertTrue(dome.all { it.y in 0..5 }, "dome stays between its base and height")
        assertTrue(BlockPos(0, 5, 0) in dome, "dome is closed at the top")
        assertFalse(BlockPos(0, 0, 0) in dome, "dome is open at the bottom")
        assertTrue(BlockPos(5, 0, 0) in dome, "dome rim reaches the base")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun capsule_followsWhereThePlayerFaces(helper: GameTestHelper) = helper.unitTest {
        val capsule = settings("capsule", 2, 10)
        val east = ZapperShapes.CAPSULE.offsets(capsule, Direction.EAST)
        val north = ZapperShapes.CAPSULE.offsets(capsule, Direction.NORTH)

        assertTrue(east.maxOf { it.x } - east.minOf { it.x } > east.maxOf { it.z } - east.minOf { it.z }, "east capsule runs along X")
        assertTrue(north.maxOf { it.z } - north.minOf { it.z } > north.maxOf { it.x } - north.minOf { it.x }, "north capsule runs along Z")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun torus_hasAHole(helper: GameTestHelper) = helper.unitTest {
        val torus = ZapperShapes.TORUS.offsets(settings("torus", 6, 2)).toSet()
        assertFalse(BlockPos.ZERO in torus, "torus center is empty")
        assertTrue(BlockPos(6, 0, 0) in torus, "ring passes through its radius")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun placement_attachesOrInsertsAlongTheFace(helper: GameTestHelper) = helper.unitTest {
        val target = BlockPos(10, 64, 10)
        val sphere = settings("ellipsoid", 3, 3, 3)
        fun placed(option: PlacementOptions) = ZapperShapes.ELLIPSOID
            .place(helper.level, ShapeAim(target, Direction.UP, Direction.NORTH, option, TerrainTools.Fill), sphere).positions

        assertTrue(placed(PlacementOptions.Attached).all { it.y > target.y }, "attached shape sits on top of the face")
        assertEquals(target.y + 1, placed(PlacementOptions.Attached).minOf { it.y }, "attached shape touches the face")
        assertEquals(target.y, placed(PlacementOptions.Inserted).maxOf { it.y }, "inserted shape ends at the clicked block")
        assertTrue(target in placed(PlacementOptions.Merged), "merged shape is centered on the clicked block")
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun settings_areClampedToTheConfiguredLimits(helper: GameTestHelper) = helper.unitTest {
        val clamped = settings("ellipsoid", 999, -5, 4, hollow = true).clampedTo(ZapperShapes.ELLIPSOID)
        assertEquals(listOf(CBBeesConfig.zapperMaxRadius.get(), 0, 4), clamped.params, "radii")
        assertFalse(settings("surface", 3, hollow = true).clampedTo(ZapperShapes.SURFACE).hollow, "flood shapes cannot be hollow")
    }
}
