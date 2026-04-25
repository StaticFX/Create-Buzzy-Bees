package de.devin.cbbees.gametest.deconstruction

import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object DeconstructionTests {

    private const val TEMPLATE = "cbbees:gametest/cbbees_deconstruct"

    @GameTest(template = TEMPLATE, timeoutTicks = 6000, setupTicks = 20)
    @JvmStatic
    fun deconstruct_dirtBlocks_endsUpInVault(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 27)
        }
        setupNetwork(scan)
        deconstruct()

        awaitSuccess {
            assertTargetsDeconstructed()
        }
    }
}
