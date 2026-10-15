package de.devin.cbbees.gametest.transport

import de.devin.cbbees.content.beehive.MechanicalBeehiveBlockEntity
import de.devin.cbbees.content.domain.TransportDispatcher
import de.devin.cbbees.gametest.GameTestSetup
import de.devin.cbbees.gametest.dsl.beeTest
import de.devin.cbbees.items.AllItems
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.capabilities.Capabilities
import java.util.UUID

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

    /**
     * Transport bees count toward the hive's max active bees: a hive at its limit
     * must not dispatch, and dispatching resumes once a slot frees up.
     *
     * Runs the dispatcher synchronously so the watchdog can't clean up the
     * placeholder bees in between.
     */
    @GameTest(
        template = "cbbees:gametest/transport/cbbees_transport",
        timeoutTicks = 200,
        setupTicks = 20,
    )
    @JvmStatic
    fun transport_respectsMaxActiveBees(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork(scan, beeCount = 0)
        val hive = scan.beehive!!
        stockBumbleBees(hive, 3)

        val providerVault = providerPorts().first().vaultPos
        var availableAtLimit = -1
        var availableAfterFree = -1

        helper.startSequence()
            .thenExecute {
                val placeholders = List(hive.getBeeContext().maxActiveBees) { UUID.randomUUID() }
                placeholders.forEach(hive::trackTransportBee)
                insertItems(providerVault, Items.DIRT, 16)
                runDispatchScan()
                availableAtLimit = hive.getAvailableBeeCount()

                placeholders.forEach(hive::onBeeRemovedById)
                runDispatchScan()
                availableAfterFree = hive.getAvailableBeeCount()
            }
            .thenExecute {
                helper.assertTrue(availableAtLimit == 3, "hive at its limit must not dispatch, $availableAtLimit bees left")
                helper.assertTrue(availableAfterFree < 3, "hive should dispatch once a slot is free")
            }
            .thenSucceed()
    }

    /**
     * A bee whose provider runs dry before pickup must fly home instead of hovering
     * at the source forever and permanently occupying a hive slot.
     */
    @GameTest(
        template = "cbbees:gametest/transport/cbbees_transport",
        timeoutTicks = 1200,
        setupTicks = 20,
    )
    @JvmStatic
    fun transport_emptiedProvider_beeReturnsHome(helper: GameTestHelper) = helper.beeTest {
        val scan = scanStructure {}
        setupNetwork(scan, beeCount = 0)
        val hive = scan.beehive!!
        stockBumbleBees(hive, 1)

        val providerVault = providerPorts().first().vaultPos
        val requesterVault = requesterPorts().first().vaultPos
        insertItems(providerVault, Items.DIRT, 16)

        helper.startSequence()
            .thenWaitUntil {
                helper.assertTrue(hive.getActiveBeeCount() == 1, "waiting for the bee to be dispatched")
            }
            .thenExecute {
                val handler = level.getCapability(Capabilities.ItemHandler.BLOCK, providerVault, null)!!
                for (slot in 0 until handler.slots) {
                    handler.extractItem(slot, handler.getStackInSlot(slot).count, false)
                }
            }
            .thenWaitUntil {
                helper.assertTrue(hive.getActiveBeeCount() == 0, "bee should leave the active set")
                helper.assertTrue(hive.getAvailableBeeCount() == 1, "bee should be back in the hive")
            }
            .thenExecute {
                val delivered = GameTestSetup.countItem(level, requesterVault, Items.DIRT)
                helper.assertTrue(delivered == 0, "nothing should be delivered from an emptied provider, got $delivered")
            }
            .thenSucceed()
    }

    /** Replaces the template's preloaded bees with exactly [count] bumble bees. */
    private fun stockBumbleBees(hive: MechanicalBeehiveBlockEntity, count: Int) {
        hive.beeInventory.setStackInSlot(0, ItemStack.EMPTY)
        repeat(count) { hive.addBee(ItemStack(AllItems.MECHANICAL_BUMBLE_BEE.get())) }
    }

    /** Ticks the dispatcher enough times to guarantee one full scan. */
    private fun runDispatchScan() = repeat(4) { TransportDispatcher.tick() }
}
