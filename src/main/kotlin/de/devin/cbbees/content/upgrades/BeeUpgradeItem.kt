package de.devin.cbbees.content.upgrades

import de.devin.cbbees.config.CBBeesConfig
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * Enum defining all upgrade types for the Constructor Backpack.
 */
enum class UpgradeType(
    val maxStackInBackpack: Int,
    val descriptionKey: String,
    val shape: UpgradeShape,
    val logic: IUpgrade
) {
    RAPID_WINGS(4, "tooltip.cbbees.upgrade.rapid_wings", UpgradeShape.L_SHAPE, IUpgrade { ctx, count ->
        ctx.speedMultiplier += count * CBBeesConfig.rapidWingsSpeedBonus.get()
    }),
    SWARM_INTELLIGENCE(3, "tooltip.cbbees.upgrade.swarm_intelligence", UpgradeShape.T_SHAPE, IUpgrade { ctx, count ->
        ctx.maxActiveBees += count * CBBeesConfig.swarmIntelligenceBeeBonus.get()
    }),
    HONEY_EFFICIENCY(2, "tooltip.cbbees.upgrade.honey_efficiency", UpgradeShape.BAR_2, IUpgrade { ctx, count ->
        ctx.breakSpeedMultiplier -= count * CBBeesConfig.honeyEfficiencyBreakSpeedReduction.get()
        ctx.carryCapacity += count * CBBeesConfig.honeyEfficiencyCarryBonus.get()
        ctx.fuelConsumptionMultiplier -= count * CBBeesConfig.honeyEfficiencyFuelReduction.get()
    }),
    SOFT_TOUCH(1, "tooltip.cbbees.upgrade.soft_touch", UpgradeShape.SINGLE, IUpgrade { ctx, count ->
        if (count > 0) ctx.silkTouchEnabled = true
    }),
    DROP_ITEMS(1, "tooltip.cbbees.upgrade.drop_items", UpgradeShape.SINGLE, IUpgrade { ctx, count ->
        if (count > 0) ctx.dropItemsEnabled = true
    }),
    HONEY_TANK(2, "tooltip.cbbees.upgrade.honey_tank", UpgradeShape.SQUARE_2X2, IUpgrade { ctx, count ->
        ctx.honeyCapacityBonus += count * CBBeesConfig.honeyTankCapacityBonus.get()
    }),
    REINFORCED_PLATING(2, "tooltip.cbbees.upgrade.reinforced_plating", UpgradeShape.S_SHAPE, IUpgrade { ctx, count ->
        ctx.springEfficiency += count * CBBeesConfig.reinforcedPlatingSpringBonus.get()
    }),
    DRONE_VIEW(1, "tooltip.cbbees.upgrade.drone_view", UpgradeShape.BAR_2, IUpgrade { ctx, count ->
        if (count > 0) ctx.droneViewAvailable = true
    }),
    DRONE_RANGE(3, "tooltip.cbbees.upgrade.drone_range", UpgradeShape.L_SHAPE, IUpgrade { ctx, count ->
        ctx.droneRange += count * CBBeesConfig.droneRangeBonus.get()
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

class ReinforcedPlatingUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.REINFORCED_PLATING, properties)

class DroneViewUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.DRONE_VIEW, properties)

class DroneRangeUpgrade(properties: Properties) : BeeUpgradeItem(UpgradeType.DRONE_RANGE, properties)
