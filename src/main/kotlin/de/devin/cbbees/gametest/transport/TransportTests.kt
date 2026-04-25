package de.devin.cbbees.gametest.transport

import de.devin.cbbees.gametest.dsl.beeTest
import de.devin.cbbees.items.AllItems
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Integration tests for transport (bumble bee item logistics).
 *
 * Structure layout (7x3x5):
 * - Provider vault at (3,1,3) with provider cargo port at (3,2,3)
 * - Requester vault at (1,1,3) with requester cargo port at (1,2,3)
 * - Beehive at (5,1,3) powered by creative motor
 *
 * Priority/filtering structures add a second requester vault at (1,1,1)
 * with a second requester cargo port at (1,2,1).
 *
 * Transport is handled by TransportDispatcher via the server tick loop.
 * The test inserts items into the provider vault, then verifies bumble bees
 * move them to the requester vault(s).
 */
object TransportTests {

    @GameTest(
        template = "cbbees:gametest/transport/cbbees_transport",
        timeoutTicks = 6000,
        setupTicks = 20,
    )
    @JvmStatic
    fun transport_movesItemsBetweenPorts(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork(scan, beeCount = 0)

        // Stock bumble bees instead of construction bees
        val bumbleItem = ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get())
        repeat(3) { scan.beehive!!.addBee(bumbleItem.copy()) }

        // Insert dirt into provider vault
        val providerVault = providerPorts().first().vaultPos
        insertItems(providerVault, Items.DIRT, 16)

        // Wait for bumble bees to transport items to requester
        val requesterVault = requesterPorts().first().vaultPos

        awaitSuccess {
            assertItemCount(scan.allPositions, Items.DIRT, atLeast = 16)
            assertItemCountAt(requesterVault, Items.DIRT, atLeast = 16)
        }
    }

    @GameTest(
        template = "cbbees:gametest/transport/cbbees_transport_priorities",
        timeoutTicks = 6000,
        setupTicks = 20,
    )
    @JvmStatic
    fun transport_priority_higherPriorityRequesterReceivesItems(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork(scan, beeCount = 0)

        val bumbleItem = ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get())
        repeat(3) { scan.beehive!!.addBee(bumbleItem.copy()) }

        val providerVault = providerPorts().first().vaultPos
        insertItems(providerVault, Items.DIRT, 16)

        // Highest-priority requester should get all items
        val highPriorityVault = requesterPorts().first().vaultPos

        awaitSuccess {
            assertItemCountAt(highPriorityVault, Items.DIRT, atLeast = 16)
        }
    }

    @GameTest(
        template = "cbbees:gametest/transport/cbbees_transport_filtering",
        timeoutTicks = 6000,
        setupTicks = 20,
    )
    @JvmStatic
    fun transport_filter_itemGoesToMatchingRequester(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork(scan, beeCount = 0)

        val bumbleItem = ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get())
        repeat(3) { scan.beehive!!.addBee(bumbleItem.copy()) }

        val providerVault = providerPorts().first().vaultPos
        insertItems(providerVault, Items.DIRT, 16)

        awaitSuccess {
            assertItemCount(scan.allPositions, Items.DIRT, atLeast = 16)
        }
    }
}
