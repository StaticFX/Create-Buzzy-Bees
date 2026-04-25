package de.devin.cbbees.gametest.portable

import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.dsl.beeTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

/**
 * Integration tests for the portable beehive (player backpack).
 *
 * Test 1 - bees_dont_use_player: Bees spawned from a STATIONARY beehive must use
 *   the logistics port in the beehive's network, not the player's inventory.
 *
 * Test 2 - portable_ignore_port: Bees spawned from the player's PORTABLE beehive
 *   must NOT use an isolated logistics port that's not in the player's network.
 *
 * Test 3 - use_logistics_first: Bees from a portable beehive that's joined a
 *   stationary network should use the logistics port first (higher priority than
 *   the player's inventory fallback).
 */
object PortableBeehiveTests {

    @GameTest(
        template = "cbbees:gametest/portable/cbbees_bees_dont_use_player",
        timeoutTicks = 6000,
        setupTicks = 20,
        batch = "portable",
    )
    @JvmStatic
    fun bees_dont_use_player(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 12)
        }

        // Set up the stationary beehive network (beehive + logistics port)
        setupNetwork(scan)
        deconstruct()

        // Add a player with portable beehive nearby (should NOT receive items)
        val portableHive = setupPortableBeehive(
            pos = helper.absolutePos(net.minecraft.core.BlockPos(3, 1, 2)),
            beeCount = 0,
            joinExistingNetwork = false,
        )

        awaitSuccess {
            assertTargetsDeconstructed()
            // Items should be in the vault, NOT in the player's inventory
            val playerDirt = portableHive.player.inventory.items.sumOf {
                if (it.`is`(Items.DIRT)) it.count else 0
            }
            check(playerDirt == 0) {
                "Player should have 0 dirt (stationary bees should use the logistics port), but has $playerDirt"
            }
        }
    }

    @GameTest(
        template = "cbbees:gametest/portable/cbbees_portable_ignore_port",
        timeoutTicks = 6000,
        setupTicks = 20,
        batch = "portable",
    )
    @JvmStatic
    fun portable_ignore_isolated_port(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 12)
        }

        // No stationary beehive in this structure — only the player's portable beehive
        // The logistics port in the structure is ISOLATED (not in the player's network)
        val portableHive = setupPortableBeehive(
            pos = helper.absolutePos(net.minecraft.core.BlockPos(3, 1, 2)),
            beeCount = 5,
        )
        deconstruct()

        awaitSuccess {
            assertAllAir(scan.targetPositions)
            // Items should be in the PLAYER's inventory, not the isolated port's vault
            val playerDirt = portableHive.player.inventory.items.sumOf {
                if (it.`is`(Items.DIRT)) it.count else 0
            }
            check(playerDirt >= 12) {
                "Expected 12 dirt in player inventory, found $playerDirt"
            }
        }
    }

    @GameTest(
        template = "cbbees:gametest/portable/cbbees_use_logistics_first",
        timeoutTicks = 6000,
        setupTicks = 20,
        batch = "portable",
    )
    @JvmStatic
    fun portable_useLogisticsFirst(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {
            target(Blocks.DIRT)
            expect(Items.DIRT, count = 12)
        }

        // Set up stationary beehive network
        setupNetwork(scan, beeCount = 0)

        // Join the player's portable beehive to the SAME network
        val portableHive = setupPortableBeehive(
            pos = helper.absolutePos(net.minecraft.core.BlockPos(3, 1, 2)),
            beeCount = 5,
            joinExistingNetwork = true,
        )
        deconstruct()

        awaitSuccess {
            assertTargetsDeconstructed()
            // Items should be in the vault (via logistics port), NOT the player
            val playerDirt = portableHive.player.inventory.items.sumOf {
                if (it.`is`(Items.DIRT)) it.count else 0
            }
            check(playerDirt == 0) {
                "Player should have 0 dirt (logistics port has higher priority), but has $playerDirt"
            }
        }
    }
}
