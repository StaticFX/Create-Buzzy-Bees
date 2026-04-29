package de.devin.cbbees.content.upgrades

import de.devin.cbbees.config.CBBeesConfig
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Enum defining all upgrade types for the Constructor Backpack.
 */
enum class UpgradeType(
    val descriptionKey: String,
    val shape: UpgradeShape,
    val logic: IUpgrade
) {
    RAPID_WINGS("tooltip.cbbees.upgrade.rapid_wings", UpgradeShape.L_SHAPE, IUpgrade { ctx, count ->
        ctx.speedMultiplier += count * CBBeesConfig.rapidWingsSpeedBonus.get()
    }),
    SWARM_INTELLIGENCE("tooltip.cbbees.upgrade.swarm_intelligence", UpgradeShape.T_SHAPE, IUpgrade { ctx, count ->
        val bonus = count * CBBeesConfig.swarmIntelligenceBeeBonus.get()
        ctx.maxActiveBees += bonus
        ctx.maxContributedBees += bonus
    }),
    HONEY_EFFICIENCY("tooltip.cbbees.upgrade.honey_efficiency", UpgradeShape.BAR_2, IUpgrade { ctx, count ->
        ctx.breakSpeedMultiplier -= count * CBBeesConfig.honeyEfficiencyBreakSpeedReduction.get()
        ctx.carryCapacity += count * CBBeesConfig.honeyEfficiencyCarryBonus.get()
        ctx.fuelConsumptionMultiplier -= count * CBBeesConfig.honeyEfficiencyFuelReduction.get()
    }),
    SOFT_TOUCH("tooltip.cbbees.upgrade.soft_touch", UpgradeShape.SINGLE, IUpgrade { ctx, count ->
        if (count > 0) ctx.silkTouchEnabled = true
    }),
    DROP_ITEMS("tooltip.cbbees.upgrade.drop_items", UpgradeShape.SINGLE, IUpgrade { ctx, count ->
        if (count > 0) ctx.dropItemsEnabled = true
    }),
    HONEY_TANK("tooltip.cbbees.upgrade.honey_tank", UpgradeShape.SQUARE_2X2, IUpgrade { ctx, count ->
        ctx.honeyCapacityBonus += count * CBBeesConfig.honeyTankCapacityBonus.get()
    }),
    DRONE_VIEW("tooltip.cbbees.upgrade.drone_view", UpgradeShape.BAR_2, IUpgrade { ctx, count ->
        if (count > 0) ctx.droneViewAvailable = true
    }),
    DRONE_RANGE("tooltip.cbbees.upgrade.drone_range", UpgradeShape.L_SHAPE, IUpgrade { ctx, count ->
        ctx.droneRange += count * CBBeesConfig.droneRangeBonus.get()
    }),
    INVENTORY_ACCESS("tooltip.cbbees.upgrade.inventory_access", UpgradeShape.BAR_2, IUpgrade { ctx, count ->
        if (count > 0) ctx.inventoryAccessEnabled = true
    });

    companion object {
        /**
         * Creates a [BeeContext] based on the upgrades found in the given backpack stack.
         * Reads from the UPGRADE_GRID data component.
         */
        fun fromBackpack(stack: ItemStack): BeeContext {
            val context = BeeContext()
            val item = stack.item as? de.devin.cbbees.content.backpack.PortableBeehiveItem ?: return context

            val upgradeCounts = item.getUpgrades(stack)
            for ((type, count) in upgradeCounts) {
                if (count > 0) {
                    type.logic.apply(context, count)
                }
            }
            return context
        }
    }
}

/**
 * Base class for all backpack upgrade items.
 *
 * Upgrades modify the behavior of bees deployed from the beehive:
 * - Rapid Wings: +25% bee speed per upgrade (max 4)
 * - Swarm Intelligence: +2 concurrent bees per upgrade (max 3)
 * - Honey Efficiency: Increased carry capacity and break speed (max 2)
 * - Soft Touch: Deconstruction preserves blocks (1 only)
 */
open class BeeUpgradeItem(
    val upgradeType: UpgradeType,
    properties: Properties
) : Item(properties)

// Specific upgrade implementations

class RapidWingsUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.RAPID_WINGS, properties)

class SwarmIntelligenceUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SWARM_INTELLIGENCE, properties)

class HoneyEfficiencyUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.HONEY_EFFICIENCY, properties)

class SoftTouchUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.SOFT_TOUCH, properties)

class DropItemsUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.DROP_ITEMS, properties)

class HoneyTankUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.HONEY_TANK, properties)

class DroneViewUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.DRONE_VIEW, properties)

class DroneRangeUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.DRONE_RANGE, properties)

class InventoryAccessUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.INVENTORY_ACCESS, properties)
