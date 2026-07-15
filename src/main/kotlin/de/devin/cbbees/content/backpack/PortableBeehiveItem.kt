package de.devin.cbbees.content.backpack

import de.devin.cbbees.content.bee.MechanicalBeeItem
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.content.backpack.client.PortableBeehiveRenderProvider
import de.devin.cbbees.content.upgrades.BeeUpgradeItem
import de.devin.cbbees.content.upgrades.BeeContext
import de.devin.cbbees.content.upgrades.UpgradeGrid
import de.devin.cbbees.content.upgrades.UpgradeType
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.registry.AllMenuTypes
import net.minecraft.ChatFormatting
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.Level
import java.util.Optional

import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterials
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.client.GeoRenderProvider
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * Data class for the backpack's tooltip preview.
 */
data class BeehiveTooltipData(val stack: ItemStack) : TooltipComponent

/**
 * Constructor Backpack - A wearable equipment item that manages construction bees and their upgrades.
 *
 * This item can be worn in the Curios "back" slot. When opened, it provides a GUI with:
 * - 4 Slots for bee items (MechanicalBeeItem or MechanicalBumbleBeeItem).
 * - 6 Slots for [BeeUpgradeItem]s.
 *
 * The backpack acts as the central hub for the mod's automated building system, storing the state
 * of the workforce and providing the interface to initiate construction tasks.
 */
class PortableBeehiveItem(properties: Properties) : ArmorItem(ArmorMaterials.IRON, Type.CHESTPLATE, properties),
    GeoItem {

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun createGeoRenderer(consumer: Consumer<GeoRenderProvider>) {
        consumer.accept(PortableBeehiveRenderProvider())
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "controller", 0) { event ->
            event.setAndContinue(RawAnimation.begin().thenLoop("jorking"))
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return this.cache
    }

    companion object {
        const val BEE_SLOTS = 2
        const val UPGRADE_SLOTS = 0
        const val TOTAL_SLOTS = BEE_SLOTS

        // NBT keys for the container
        const val TAG_INVENTORY = "BackpackInventory"

        /** Legacy slot count for migration from old flat upgrade layout */
        const val LEGACY_TOTAL_SLOTS = 6
        const val LEGACY_UPGRADE_SLOTS = 4

        /**
         * One-time migration: reads old upgrades from CONTAINER slots 2-5,
         * auto-places them into a new UpgradeGrid, and trims the CONTAINER to bee-only slots.
         */
        fun migrateUpgradesToGrid(stack: ItemStack) {
            // Skip if grid already exists
            if (stack.has(AllDataComponents.UPGRADE_GRID.get())) return

            val contents = stack.get(DataComponents.CONTAINER) ?: return
            val items = NonNullList.withSize(LEGACY_TOTAL_SLOTS, ItemStack.EMPTY)
            contents.copyInto(items)

            // Check if there are any upgrades in legacy slots (indices 2-5)
            val legacyUpgrades = mutableListOf<UpgradeType>()
            for (i in BEE_SLOTS until LEGACY_TOTAL_SLOTS) {
                val s = items[i]
                val upgradeItem = s.item as? BeeUpgradeItem
                if (upgradeItem != null && !s.isEmpty) {
                    legacyUpgrades.add(upgradeItem.upgradeType)
                }
            }

            if (legacyUpgrades.isEmpty()) return

            // Auto-place into grid
            val grid = UpgradeGrid()
            for (type in legacyUpgrades) {
                // Try all positions and rotations to find a valid placement
                var placed = false
                for (rotation in 0 until 4) {
                    if (placed) break
                    for (y in 0 until UpgradeGrid.ROWS) {
                        if (placed) break
                        for (x in 0 until UpgradeGrid.COLS) {
                            if (grid.canPlace(type, x, y, rotation)) {
                                grid.place(type, x, y, rotation)
                                placed = true
                                break
                            }
                        }
                    }
                }
            }

            // Save grid
            stack.set(AllDataComponents.UPGRADE_GRID.get(), grid)

            // Trim CONTAINER to bee-only slots
            val beeItems = mutableListOf<ItemStack>()
            for (i in 0 until BEE_SLOTS) {
                beeItems.add(items[i])
            }
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(beeItems))
        }
    }

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)

        // Shift+RMB: try to load honey from the other hand
        if (player.isShiftKeyDown) {
            val otherHand =
                if (usedHand == InteractionHand.MAIN_HAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
            val fuelStack = player.getItemInHand(otherHand)
            val fuelValue = getHoneyFuelValue(fuelStack)
            if (fuelValue > 0) {
                if (!level.isClientSide) {
                    val current = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
                    val max = getMaxHoney(stack)
                    if (current >= max) {
                        player.displayClientMessage(Component.translatable("cbbees.beehive.honey_full"), true)
                    } else {
                        val newValue = minOf(current + fuelValue, max)
                        stack.set(AllDataComponents.HONEY_FUEL.get(), newValue)
                        fuelStack.shrink(1)
                        // Return empty bottle for honey bottles
                        if (fuelStack.item == Items.HONEY_BOTTLE) {
                            player.addItem(ItemStack(Items.GLASS_BOTTLE))
                        }
                        player.displayClientMessage(
                            Component.translatable("cbbees.beehive.honey_loaded", newValue, max), true
                        )
                    }
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
            }
        }

        // Normal RMB: open GUI
        if (!level.isClientSide && player is ServerPlayer) {
            openBackpackScreen(player, usedHand)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    private fun getHoneyFuelValue(stack: ItemStack): Int = when (stack.item) {
        Items.HONEY_BOTTLE -> CBBeesConfig.honeyBottleFuelValue.get()
        Items.HONEYCOMB -> CBBeesConfig.honeycombFuelValue.get()
        Items.HONEY_BLOCK -> CBBeesConfig.honeyBlockFuelValue.get()
        else -> 0
    }

    private fun openBackpackScreen(player: ServerPlayer, hand: InteractionHand) {
        // Find the slot index of the backpack in player's inventory
        val slotIndex = if (hand == InteractionHand.MAIN_HAND) {
            player.inventory.selected
        } else {
            40 // Offhand slot
        }

        val stack = player.getItemInHand(hand)

        player.openMenu(object : MenuProvider {
            override fun getDisplayName(): Component {
                return Component.translatable("container.cbbees.portable_beehive")
            }

            override fun createMenu(
                containerId: Int,
                playerInventory: Inventory,
                player: Player
            ): AbstractContainerMenu {
                return BeehiveContainer(
                    AllMenuTypes.CONSTRUCTOR_BACKPACK.get(),
                    containerId,
                    playerInventory,
                    stack
                )
            }
        }) { buf ->
            // Write the slot index to the buffer for client-side reconstruction
            buf.writeVarInt(slotIndex)
        }
    }

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> {
        return Optional.of(BeehiveTooltipData(stack))
    }

    override fun isBarVisible(stack: ItemStack): Boolean {
        val honey = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        return honey < getMaxHoney(stack)
    }

    override fun getBarWidth(stack: ItemStack): Int {
        val max = getMaxHoney(stack)
        if (max <= 0) return 0
        val honey = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        return (13.0f * honey / max).roundToInt()
    }

    override fun getBarColor(stack: ItemStack): Int {
        return 0xD97F00
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)

        val honey = stack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
        val maxHoney = getMaxHoney(stack)
        tooltipComponents.add(
            Component.translatable("tooltip.cbbees.beehive.honey", honey, maxHoney)
                .withStyle(ChatFormatting.GOLD)
        )
    }

    private fun isBeeItem(item: net.minecraft.world.item.Item): Boolean {
        return item is MechanicalBeeItem || item is MechanicalBumbleBeeItem
    }

    /**
     * Retrieves all upgrades and their counts from the backpack's upgrade grid.
     */
    fun getUpgrades(stack: ItemStack): Map<UpgradeType, Int> {
        val grid = stack.get(AllDataComponents.UPGRADE_GRID.get()) ?: return emptyMap()
        return grid.getUpgradeCounts()
    }

    /**
     * Gets the total number of bees in the backpack (sum of all stacks).
     */
    fun getTotalBeeCount(stack: ItemStack): Int {
        val contents = stack.get(DataComponents.CONTAINER) ?: return 0
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)

        var count = 0
        for (i in 0 until BEE_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && isBeeItem(s.item)) {
                count += s.count
            }
        }
        return count
    }

    /**
     * Consumes a single bee from the backpack inventory.
     * @return the consumed bee ItemStack (count 1), or ItemStack.EMPTY if none available
     */
    fun consumeBee(stack: ItemStack): ItemStack {
        val contents = stack.get(DataComponents.CONTAINER) ?: return ItemStack.EMPTY
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)

        for (i in 0 until BEE_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && isBeeItem(s.item)) {
                val consumed = s.copyWithCount(1)
                s.shrink(1)
                if (s.isEmpty) {
                    items[i] = ItemStack.EMPTY
                }
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return consumed
            }
        }
        return ItemStack.EMPTY
    }

    fun consumeBeeOfType(stack: ItemStack, itemClass: Class<*>): ItemStack {
        val contents = stack.get(DataComponents.CONTAINER) ?: return ItemStack.EMPTY
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents.copyInto(items)

        for (i in 0 until BEE_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && itemClass.isInstance(s.item)) {
                val consumed = s.copyWithCount(1)
                s.shrink(1)
                if (s.isEmpty) items[i] = ItemStack.EMPTY
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return consumed
            }
        }
        return ItemStack.EMPTY
    }

    /**
     * Adds a single bee to the backpack inventory.
     * @return true if successful, false if backpack is full
     */
    fun addBee(stack: ItemStack, beeItem: ItemStack): Boolean {
        val contents = stack.get(DataComponents.CONTAINER)
        val items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY)
        contents?.copyInto(items)

        // Try to stack with existing bees first
        for (i in 0 until BEE_SLOTS) {
            val s = items[i]
            if (!s.isEmpty && ItemStack.isSameItemSameComponents(s, beeItem) && s.count < s.maxStackSize) {
                s.grow(1)
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return true
            }
        }

        // Try to find an empty slot
        for (i in 0 until BEE_SLOTS) {
            if (items[i].isEmpty) {
                items[i] = beeItem.copyWithCount(1)
                stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
                return true
            }
        }

        return false
    }

    /**
     * Gets the count of a specific upgrade type from the upgrade grid.
     */
    fun getUpgradeCount(stack: ItemStack, type: UpgradeType): Int {
        val grid = stack.get(AllDataComponents.UPGRADE_GRID.get()) ?: return 0
        return grid.getUpgradeCounts().getOrDefault(type, 0)
    }

    /**
     * Gets the calculated [BeeContext] for this backpack.
     */
    fun getBeeContext(stack: ItemStack): BeeContext {
        return UpgradeType.fromBackpack(stack)
    }

    /**
     * Gets the maximum honey capacity, including bonuses from Honey Tank upgrades.
     */
    fun getMaxHoney(stack: ItemStack): Int {
        val base = CBBeesConfig.portableMaxHoney.get()
        val context = getBeeContext(stack)
        return base + context.honeyCapacityBonus
    }

}
