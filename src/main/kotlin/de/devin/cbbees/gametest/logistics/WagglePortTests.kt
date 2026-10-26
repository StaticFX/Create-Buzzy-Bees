package de.devin.cbbees.gametest.logistics

import com.simibubi.create.content.logistics.BigItemStack
import de.devin.cbbees.content.domain.network.PendingRequests
import de.devin.cbbees.gametest.dsl.unitTest
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

object WagglePortTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    private fun List<BigItemStack>.counts() = associate { it.stack.item to it.count }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    @JvmStatic
    fun pendingRequests_capsByStockAndWaitsForDeliveries(helper: GameTestHelper) = helper.unitTest {
        val pending = PendingRequests()
        val missing = listOf(BigItemStack(ItemStack(Items.STONE), 10), BigItemStack(ItemStack(Items.DIRT), 5))
        val stock = mapOf(Items.STONE to 6, Items.DIRT to 0)

        val first = pending.plan(missing, { stock.getValue(it.item) }, now = 0)
        assertEquals(mapOf(Items.STONE to 6), first.counts(), "first order is capped by stock")

        pending.record(UUID.randomUUID(), first, until = 100)
        assertEquals(mapOf(Items.STONE to 4), pending.plan(missing, { 64 }, now = 50).counts().filterKeys { it == Items.STONE }, "items on their way are not ordered twice")
        assertEquals(10, pending.plan(missing, { 64 }, now = 100).counts()[Items.STONE], "expired requests are ordered again")
    }
}
