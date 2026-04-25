package de.devin.cbbees.gametest.logistics

import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

object LogisticsPortTests {

    @GameTest(
        template = "cbbees:gametest/logistic_ports/cbbees_priorities",
        timeoutTicks = 6000,
        setupTicks = 20,
    )
    @JvmStatic
    fun priority_higherPriorityPort_receivesAllItems(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 27)
        }
        setupNetwork(scan)
        deconstruct()

        val highVault = portsByPriority().first().vaultPos

        awaitSuccess {
            assertTargetsDeconstructed()
            assertItemCountAt(highVault, Items.DIRT, atLeast = 27)
        }
    }

    @GameTest(
        template = "cbbees:gametest/logistic_ports/cbbees_filtering",
        timeoutTicks = 6000,
        setupTicks = 20,
    )
    @JvmStatic
    fun filter_itemGoesToMatchingPort_despiteLowerPriority(helper: GameTestHelper) = helper.beeTest {
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
