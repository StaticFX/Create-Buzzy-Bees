package de.devin.cbbees.content.bee.flight.checkpoint

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.bee.flight.CheckpointAction
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.bee.server.ServerBeeData
import de.devin.cbbees.content.bee.server.ServerBeeManager
import de.devin.cbbees.items.AllItems as CBeesItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/**
 * Return the bee to its hive. Charges return fuel, adds the bee item back
 * to the hive inventory, and removes the bee from [ServerBeeManager].
 *
 * Placed after [CheckForNextWork] in the flight plan — only reached when
 * there's no more work available.
 *
 * @see CheckForNextWork
 * @see CheckpointAction
 */
class EnterHive : CheckpointAction {

    override fun onArrival(bee: ServerBeeData, level: ServerLevel, gameTime: Long): Boolean {
        val hive = bee.hiveInstance ?: run {
            CreateBuzzyBeez.debug { "[EnterHive] Bee ${bee.id.toString().substring(0, 6)}: no hiveInstance" }
            return false
        }

        val deficit = 1.0f - bee.springTension
        hive.chargeReturnFuel(deficit, bee.getBeeContext())
        bee.returnCarriedFluid()

        val beeItem = ItemStack(
            if (bee.type == BeeType.CONSTRUCTION)
                CBeesItems.MECHANICAL_BEE.get()
            else
                CBeesItems.MECHANICAL_BUMBLE_BEE.get()
        )

        if (hive.returnBee(beeItem)) {
            hive.onBeeRemovedById(bee.id)
            CreateBuzzyBeez.debug { "[EnterHive] Bee ${bee.id.toString().substring(0, 6)} returned to ${hive.javaClass.simpleName}, activeBees=${hive.getActiveBeeCount()}" }
            ServerBeeManager.removeBee(bee.id)
            return true
        }

        bee.dropInventory()
        level.addFreshEntity(ItemEntity(level, bee.pos.x, bee.pos.y, bee.pos.z, beeItem))
        hive.onBeeRemovedById(bee.id)
        CreateBuzzyBeez.debug { "[EnterHive] Bee ${bee.id.toString().substring(0, 6)} dropped as item (hive full), activeBees=${hive.getActiveBeeCount()}" }
        ServerBeeManager.removeBee(bee.id)
        return true
    }
}
