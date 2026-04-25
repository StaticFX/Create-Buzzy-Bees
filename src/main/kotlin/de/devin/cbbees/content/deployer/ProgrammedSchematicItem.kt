package de.devin.cbbees.content.deployer

import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

class ProgrammedSchematicItem(properties: Properties) : Item(properties) {

    override fun isFoil(stack: ItemStack): Boolean {
        return stack.has(AllDataComponents.SCHEMATIC_PROGRAM)
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        val program = stack.get(AllDataComponents.SCHEMATIC_PROGRAM) ?: return

        when (program) {
            is SchematicProgram.Construction -> {
                tooltipComponents.add(
                    Component.translatable("cbbees.program.construction", program.displayName())
                        .withStyle(ChatFormatting.GOLD)
                )
                val a = program.anchor
                tooltipComponents.add(
                    Component.translatable("cbbees.program.anchor", a.x, a.y, a.z)
                        .withStyle(ChatFormatting.GRAY)
                )
                if (program.rotation != net.minecraft.world.level.block.Rotation.NONE) {
                    tooltipComponents.add(
                        Component.translatable("cbbees.program.rotation", program.rotation.name)
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
                }
                if (program.mirror != net.minecraft.world.level.block.Mirror.NONE) {
                    tooltipComponents.add(
                        Component.translatable("cbbees.program.mirror", program.mirror.name)
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
                }
            }
            is SchematicProgram.Deconstruction -> {
                tooltipComponents.add(
                    Component.translatable("cbbees.program.deconstruction")
                        .withStyle(ChatFormatting.GOLD)
                )
                val c1 = program.corner1
                val c2 = program.corner2
                tooltipComponents.add(
                    Component.translatable("cbbees.program.corner1", c1.x, c1.y, c1.z)
                        .withStyle(ChatFormatting.GRAY)
                )
                tooltipComponents.add(
                    Component.translatable("cbbees.program.corner2", c2.x, c2.y, c2.z)
                        .withStyle(ChatFormatting.GRAY)
                )
                val sizeX = kotlin.math.abs(c1.x - c2.x) + 1
                val sizeY = kotlin.math.abs(c1.y - c2.y) + 1
                val sizeZ = kotlin.math.abs(c1.z - c2.z) + 1
                tooltipComponents.add(
                    Component.translatable("cbbees.program.dimensions", sizeX, sizeY, sizeZ)
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
            is SchematicProgram.Pickup -> {
                tooltipComponents.add(
                    Component.translatable("cbbees.program.pickup")
                        .withStyle(ChatFormatting.GOLD)
                )
                val c1 = program.corner1
                val c2 = program.corner2
                tooltipComponents.add(
                    Component.translatable("cbbees.program.corner1", c1.x, c1.y, c1.z)
                        .withStyle(ChatFormatting.GRAY)
                )
                tooltipComponents.add(
                    Component.translatable("cbbees.program.corner2", c2.x, c2.y, c2.z)
                        .withStyle(ChatFormatting.GRAY)
                )
                val sizeX = kotlin.math.abs(c1.x - c2.x) + 1
                val sizeY = kotlin.math.abs(c1.y - c2.y) + 1
                val sizeZ = kotlin.math.abs(c1.z - c2.z) + 1
                tooltipComponents.add(
                    Component.translatable("cbbees.program.dimensions", sizeX, sizeY, sizeZ)
                        .withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }
    }
}
