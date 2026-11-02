package de.devin.cbbees.compat.ae2

import appeng.blockentity.networking.CableBusBlockEntity
import appeng.core.definitions.AEBlockEntities
import appeng.core.definitions.AEBlocks
import appeng.items.parts.FacadeItem
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry
import com.simibubi.create.api.schematic.nbt.SafeNbtWriterRegistry.SafeNbtWriter
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries
import com.simibubi.create.api.schematic.requirement.SchematicRequirementRegistries.BlockRequirement
import com.simibubi.create.content.schematics.requirement.ItemRequirement
import com.simibubi.create.content.schematics.requirement.ItemRequirement.ItemUseType
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.StringTag
import net.minecraft.world.item.ItemStack

object CableBusSchematics {

    private val PART_KEYS = setOf("cable", "down", "up", "north", "south", "west", "east")
    private val FACADE_KEYS = setOf("facadeDown", "facadeUp", "facadeNorth", "facadeSouth", "facadeWest", "facadeEast")
    private val GHOST_KEYS = setOf("config", "configuredItem")

    fun register() {
        SchematicRequirementRegistries.BLOCKS.registerProvider { block -> requirement.takeIf { block == AEBlocks.CABLE_BUS.block() } }
        SafeNbtWriterRegistry.REGISTRY.registerProvider { type -> writer.takeIf { type == AEBlockEntities.CABLE_BUS.get() } }
    }

    private val requirement = BlockRequirement { _, blockEntity ->
        val bus = (blockEntity as? CableBusBlockEntity)?.cableBus ?: return@BlockRequirement ItemRequirement.INVALID
        val stacks = bus.addPartDrops(mutableListOf()).map(::requiredStack)
        ItemRequirement(ItemUseType.CONSUME, merged(stacks))
    }

    private val writer = SafeNbtWriter { blockEntity, tag, registries ->
        val saved = blockEntity.saveWithoutMetadata(registries)
        saved.allKeys.forEach { key ->
            when (key) {
                in FACADE_KEYS -> tag.put(key, saved.get(key)!!.copy())
                in PART_KEYS -> tag.put(key, safePart(saved.getCompound(key)))
            }
        }
    }

    private fun safePart(part: CompoundTag) = CompoundTag().also { safe ->
        part.allKeys.forEach { key ->
            val value = part.get(key)!!
            if (value is NumericTag || value is StringTag || key in GHOST_KEYS) safe.put(key, value.copy())
        }
    }

    private fun requiredStack(drop: ItemStack): ItemStack =
        if (drop.item is FacadeItem) drop.copyWithCount(1) else ItemStack(drop.item)

    private fun merged(stacks: List<ItemStack>): List<ItemStack> = buildList {
        stacks.forEach { stack ->
            val existing = firstOrNull { ItemStack.isSameItemSameComponents(it, stack) }
            if (existing != null) existing.grow(stack.count) else add(stack.copy())
        }
    }
}
