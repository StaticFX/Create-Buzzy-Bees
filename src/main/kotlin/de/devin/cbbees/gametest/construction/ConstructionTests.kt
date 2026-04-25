package de.devin.cbbees.gametest.construction

import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

/**
 * Integration tests for construction (block placement from materials).
 *
 * Structure layout (cbbees_construction.nbt, 7x3x8):
 * - Extract port at (3,2,6) on vault at (3,1,6) — material source
 * - Insert port at (1,2,6) on vault at (1,1,6) — drop-off
 * - Beehive at (5,1,6) powered by creative motor
 * - Empty area at z=0-4 for building
 *
 * Tests create placement tasks programmatically (no schematic file needed).
 * The bee must gather materials from the extract port, fly to the target,
 * and place the block — consuming the item in the process.
 */
object ConstructionTests {

    private const val TEMPLATE = "cbbees:gametest/construction/cbbees_construction"

    /**
     * Bees place 9 dirt blocks in a 3x3 grid, consuming dirt from the vault.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun construct_placesBlocksFromVault(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        val jobPool = setupNetwork(scan)

        // Insert 9 dirt into the extract vault (material source)
        val extractVault = scan.ports.find { it.isValidForPickup() }
            ?: throw net.minecraft.gametest.framework.GameTestAssertException("No extract port found")
        insertItems(extractVault.blockPos.below(), Items.DIRT, 9)

        // Create placement tasks: 3x3 dirt grid at y=1, z=2
        val placements = mutableListOf<GameTestSetup.PlacementTask>()
        for (x in 1..3) {
            for (z in 1..3) {
                val worldPos = helper.absolutePos(BlockPos(x, 1, z))
                placements.add(GameTestSetup.PlacementTask(
                    pos = worldPos,
                    state = Blocks.DIRT.defaultBlockState(),
                    items = listOf(ItemStack(Items.DIRT, 1)),
                ))
            }
        }
        construct(placements)

        awaitSuccess {
            // All 9 blocks should be placed
            for (placement in placements) {
                val state = level.getBlockState(placement.pos)
                if (!state.`is`(Blocks.DIRT)) {
                    throw net.minecraft.gametest.framework.GameTestAssertException(
                        "Expected dirt at ${placement.pos}, got ${state.block}"
                    )
                }
            }

            // Materials should be consumed from the vault
            val remaining = GameTestSetup.countItem(level, extractVault.blockPos.below(), Items.DIRT)
            check(remaining == 0) { "Vault should be empty after construction, but has $remaining dirt" }
        }
    }

    /**
     * Bees stall when the vault has insufficient materials.
     * Only the blocks with available materials should be placed.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun construct_stallsWhenMissingMaterials(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        val jobPool = setupNetwork(scan)

        // Insert only 4 dirt — not enough for 9 placements
        val extractVault = scan.ports.find { it.isValidForPickup() }
            ?: throw net.minecraft.gametest.framework.GameTestAssertException("No extract port found")
        insertItems(extractVault.blockPos.below(), Items.DIRT, 4)

        val placements = mutableListOf<GameTestSetup.PlacementTask>()
        for (x in 1..3) {
            for (z in 1..3) {
                val worldPos = helper.absolutePos(BlockPos(x, 1, z))
                placements.add(GameTestSetup.PlacementTask(
                    pos = worldPos,
                    state = Blocks.DIRT.defaultBlockState(),
                    items = listOf(ItemStack(Items.DIRT, 1)),
                ))
            }
        }
        construct(placements)

        // Wait a bit for bees to place what they can, then verify partial progress
        awaitSuccess {
            // Count how many dirt blocks were placed
            val placed = placements.count { level.getBlockState(it.pos).`is`(Blocks.DIRT) }
            // Should have placed exactly 4 (the available materials)
            check(placed >= 4) { "Expected at least 4 blocks placed with 4 materials, got $placed" }
            // Should NOT have placed all 9
            check(placed < 9) { "Placed $placed blocks but only 4 materials were available" }
            // Vault should be empty
            val remaining = GameTestSetup.countItem(level, extractVault.blockPos.below(), Items.DIRT)
            check(remaining == 0) { "Vault should be empty, has $remaining" }
        }
    }
}
