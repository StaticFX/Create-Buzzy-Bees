package de.devin.cbbees.content.backpack

import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.bee.MechanicalBeeItem
import de.devin.cbbees.content.bee.MechanicalBumbleBeeItem
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import top.theillusivec4.curios.api.CuriosApi

/**
 * Container/Menu for the Constructor Backpack.
 *
 * Layout:
 * - Robot slots (4 slots)
 * - Upgrade slots (6 slots)
 * - Bottom section: Player inventory
 */
class BeehiveContainer : AbstractContainerMenu {

    private val playerInventory: Inventory
    val backpackStack: ItemStack
    private val backpackInventory: Container
    val fuelData: ContainerData

    // Constructor for client-side (from network) - used by Registrate
    constructor(type: MenuType<*>, containerId: Int, playerInventory: Inventory, extraData: RegistryFriendlyByteBuf)
            : super(type, containerId) {
        this.playerInventory = playerInventory
        // On client, we read the slot index where the backpack is located
        val slotIndex = extraData.readVarInt()
        this.backpackStack = playerInventory.getItem(slotIndex)
        this.backpackInventory = SimpleContainer(PortableBeehiveItem.TOTAL_SLOTS)
        this.fuelData = SimpleContainerData(2)

        setupSlots()
    }

    // Constructor for server-side (when opening from item)
    constructor(type: MenuType<*>, containerId: Int, playerInventory: Inventory, backpackStack: ItemStack)
            : super(type, containerId) {
        this.playerInventory = playerInventory
        this.backpackStack = backpackStack
        this.backpackInventory = SimpleContainer(PortableBeehiveItem.TOTAL_SLOTS)
        this.fuelData = object : ContainerData {
            override fun get(index: Int): Int = when (index) {
                0 -> backpackStack.getOrDefault(AllDataComponents.HONEY_FUEL.get(), 0)
                1 -> (backpackStack.item as? PortableBeehiveItem)?.getMaxHoney(backpackStack) ?: CBBeesConfig.portableMaxHoney.get()
                else -> 0
            }

            override fun set(index: Int, value: Int) {}
            override fun getCount(): Int = 2
        }

        // Migrate old flat upgrades to grid if needed
        PortableBeehiveItem.migrateUpgradesToGrid(backpackStack)

        // Load contents from the backpack item (robot slots only)
        val contents = backpackStack.get(DataComponents.CONTAINER)
        if (contents != null) {
            val items = NonNullList.withSize(PortableBeehiveItem.TOTAL_SLOTS, ItemStack.EMPTY)
            contents.copyInto(items)
            for (i in 0 until items.size) {
                backpackInventory.setItem(i, items[i])
            }
        }

        setupSlots()
    }

    private fun setupSlots() {
        addDataSlots(fuelData)

        // Robot slots (2 slots, stacked vertically)
        val beeSlotPositions = listOf(8 to 24, 8 to 51)
        for (i in 0 until PortableBeehiveItem.ROBOT_SLOTS) {
            val (x, y) = beeSlotPositions[i]
            addSlot(RobotSlot(backpackInventory, i, x, y))
        }

        // Upgrades are now managed via the grid system (no container slots)

        // Add player inventory slots
        // Must match screen's renderPlayerInventory position: slots start at renderX+8, renderY+18
        // Screen renders at x = (imageWidth - 176) / 2 = 11, y = BG_HEIGHT + 3 = 115
        val invX = 11 + 8  // 19
        val invY = 115 + 18  // 133
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, invX + col * 18, invY + row * 18))
            }
        }

        // Add player hotbar slots
        for (col in 0 until 9) {
            addSlot(Slot(playerInventory, col, invX + col * 18, invY + 58))
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        var result = ItemStack.EMPTY
        val slot = slots.getOrNull(index) ?: return result

        if (slot.hasItem()) {
            val slotStack = slot.item
            result = slotStack.copy()

            val backpackSlotCount = PortableBeehiveItem.TOTAL_SLOTS

            if (index < backpackSlotCount) {
                // Moving from backpack to player inventory
                if (!moveItemStackTo(slotStack, backpackSlotCount, slots.size, true)) {
                    return ItemStack.EMPTY
                }
            } else {
                // Moving from player inventory to backpack — only robot slots
                // Upgrades are placed via the grid system, not shift-click
                if (slotStack.item is MechanicalBeeItem || slotStack.item is MechanicalBumbleBeeItem) {
                    if (!moveItemStackTo(slotStack, 0, PortableBeehiveItem.ROBOT_SLOTS, false)) {
                        return ItemStack.EMPTY
                    }
                } else {
                    return ItemStack.EMPTY
                }
            }

            if (slotStack.isEmpty) {
                slot.set(ItemStack.EMPTY)
            } else {
                slot.setChanged()
            }
        }

        return result
    }

    override fun stillValid(player: Player): Boolean {
        // Check if player still has the backpack
        return playerInventory.contains(backpackStack) ||
                isBackpackEquippedInCurios(player)
    }

    private fun isBackpackEquippedInCurios(player: Player): Boolean {
        val curiosResult = CuriosApi.getCuriosHelper().findFirstCurio(player) { it == backpackStack }
        return curiosResult.isPresent
    }

    override fun removed(player: Player) {
        super.removed(player)
        saveBackpackContents()
    }

    private fun saveBackpackContents() {
        // Save robot slot contents back to the backpack item
        val items = mutableListOf<ItemStack>()
        for (i in 0 until backpackInventory.containerSize) {
            items.add(backpackInventory.getItem(i))
        }
        backpackStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items))
    }

    /**
     * Slot that only accepts robot items
     */
    inner class RobotSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean {
            return stack.item is MechanicalBeeItem || stack.item is MechanicalBumbleBeeItem
        }

        override fun getMaxStackSize(): Int = MechanicalBeeItem.MAX_STACK_SIZE
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (id == 0) {
            // Accept button — save and close
            saveBackpackContents()
            player.closeContainer()
            return true
        }
        return false
    }

}
