package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.content.schematics.BlocksInTheWay
import de.devin.cbbees.content.schematics.BuildMode
import de.devin.cbbees.content.schematics.BuildModes
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import de.devin.cbbees.network.StartConstructionPacket
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.network.PacketDistributor

@ClientSide
object ConstructPrompt {
    private const val SLOT = "cbbees_in_the_way_"
    private const val MAX_OUTLINES = 256
    private const val COLOR = 0xFFAA33

    private data class Placement(val anchor: BlockPos, val rotation: Rotation, val mirror: Mirror) {
        val settings: StructurePlaceSettings get() = StructurePlaceSettings().setRotation(rotation).setMirror(mirror)
    }

    private var placement: Placement? = null
    private var stack: ItemStack = ItemStack.EMPTY
    private var inTheWay: List<BlockPos> = emptyList()
    private var shownSlots = 0

    @JvmStatic
    val isOpen get() = placement != null

    val blocksInTheWay get() = inTheWay.size

    @JvmStatic
    fun construct(planner: ItemStack) {
        val current = currentPlacement()
        val level = Minecraft.getInstance().level ?: return
        val blocks = runCatching {
            BlocksInTheWay.find(level, SchematicItem.loadSchematic(level, planner), current.anchor, current.settings)
        }.getOrDefault(emptyList())

        if (blocks.isEmpty()) {
            start(planner, current, BuildModes.REPLACE)
            return
        }
        placement = current
        stack = planner
        inTheWay = blocks
        BuildModeBar.open()
    }

    @JvmStatic
    fun confirm() {
        val current = placement ?: return
        BuildModeBar.selected()?.let { start(stack, current, it) }
        close()
    }

    @JvmStatic
    fun close() {
        placement = null
        stack = ItemStack.EMPTY
        inTheWay = emptyList()
    }

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val handler = CreateClient.SCHEMATIC_HANDLER
        if (isOpen && (!handler.isActive || !handler.isDeployed || currentPlacement() != placement)) close()

        val outliner = Outliner.getInstance()
        val shown = inTheWay.take(MAX_OUTLINES)
        shown.forEachIndexed { i, pos ->
            outliner.showAABB("$SLOT$i", AABB(pos)).colored(COLOR).lineWidth(1 / 16f)
        }
        for (i in shown.size until shownSlots) outliner.remove("$SLOT$i")
        shownSlots = shown.size
    }

    private fun currentPlacement(): Placement {
        val transform = CreateClient.SCHEMATIC_HANDLER.transformation
        val settings = transform.toSettings()
        return Placement(transform.anchor, settings.rotation, settings.mirror)
    }

    private fun start(planner: ItemStack, placement: Placement, mode: BuildMode) {
        PacketDistributor.sendToServer(StartConstructionPacket(placement.anchor, placement.rotation, placement.mirror, mode))
        ConstructionPlannerItem.releaseSchematic(planner)
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("gui.cbbees.schematic.construction_started").withStyle { it.withColor(0x00FF00) },
            true,
        )
    }
}
