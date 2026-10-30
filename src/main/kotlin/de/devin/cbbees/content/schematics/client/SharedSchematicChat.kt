package de.devin.cbbees.content.schematics.client

import com.simibubi.create.CreateClient
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.external.RemoteSchematic
import de.devin.cbbees.content.schematics.share.SchematicSummary
import de.devin.cbbees.content.schematics.share.SharedSchematics
import de.devin.cbbees.network.RequestSharedSchematicPacket
import de.devin.cbbees.network.SharedSchematicPacket
import net.createmod.catnip.levelWrappers.SchematicLevel
import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil
import kotlin.math.floor

@OnlyIn(Dist.CLIENT)
object SharedSchematicChat {

    private const val KEPT = 8
    private const val BOX_WIDTH = 100

    private class Ready(val renderer: GhostSchematicRenderer, val size: Vec3i)

    private class Shared(val name: String, val bytes: ByteArray) {
        var template: CompletableFuture<StructureTemplate>? = null
        var preview: Ready? = null
        var failed = false
    }

    private class Line(val id: String, val index: Int, val y: Int, val lineHeight: Int)

    private val incoming = mutableMapOf<Pair<String, Boolean>, ByteArrayOutputStream>()
    private val visible = mutableListOf<Line>()
    private val shared = object : LinkedHashMap<String, Shared>(KEPT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Shared>) = size > KEPT
    }

    fun receive(packet: SharedSchematicPacket) {
        val key = packet.id to packet.save
        val buffer = incoming.getOrPut(key) { ByteArrayOutputStream(packet.totalSize) }
        buffer.write(packet.data)
        if (buffer.size() < packet.totalSize) return
        incoming.remove(key)
        val name = packet.filename.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".nbt")
        if (packet.save) save(name, buffer.toByteArray()) else shared[packet.id] = Shared(name, buffer.toByteArray())
    }

    fun collect(text: FormattedCharSequence, y: Int, lineHeight: Int) {
        var marker: String? = null
        text.accept { _, style, _ ->
            marker = style.insertion?.takeIf { it.startsWith(SharedSchematics.MARKER) }
            marker == null
        }
        val parts = marker?.removePrefix(SharedSchematics.MARKER)?.split(':') ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return
        visible += Line(parts[0], index, y, lineHeight)
    }

    fun flush(graphics: GuiGraphics) {
        visible.groupBy { it.id }.forEach { (id, lines) -> draw(graphics, id, lines) }
        visible.clear()
    }

    fun open(id: String): Boolean {
        val entry = shared[id] ?: return false
        val mc = Minecraft.getInstance()
        val summary = runCatching { SchematicSummary.read(NbtIo.readCompressed(ByteArrayInputStream(entry.bytes), NbtAccounter.unlimitedHeap())) }
            .getOrNull() ?: return false
        val schematic = RemoteSchematic(entry.name, entry.name, "", "", 0, summary.sizeX, summary.sizeY, summary.sizeZ, blockCount = summary.blockCount)
        mc.setScreen(SchematicDetailScreen(null, mc.screen, schematic, mutableMapOf(entry.name to entry.bytes), mutableMapOf()))
        return true
    }

    fun save(id: String): Boolean {
        val entry = shared[id]
        if (entry == null) PacketDistributor.sendToServer(RequestSharedSchematicPacket(id)) else save(entry.name, entry.bytes)
        return true
    }

    private fun save(name: String, bytes: ByteArray) {
        val filename = SchematicFiles.saveUnique(name, bytes)
        CreateClient.SCHEMATIC_SENDER.refresh()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("message.cbbees.share.saved", filename.removeSuffix(".nbt")).withStyle(ChatFormatting.GREEN),
            false
        )
    }

    private fun draw(graphics: GuiGraphics, id: String, lines: List<Line>) {
        val ready = shared[id]?.let(::preview) ?: return
        val first = lines.first()
        val lineHeight = first.lineHeight
        val top = first.y - 1 - first.index * lineHeight
        val matrix = graphics.pose().last().pose()
        val from = matrix.transformPosition(Vector3f(0f, lines.minOf { it.y } - 1f, 0f))
        val to = matrix.transformPosition(Vector3f(BOX_WIDTH.toFloat(), lines.maxOf { it.y } - 1f + lineHeight, 0f))
        graphics.enableScissor(floor(from.x).toInt(), floor(from.y).toInt(), ceil(to.x).toInt(), ceil(to.y).toInt())
        SpinningPreview.render(graphics, ready.renderer, ready.size, 0, top, BOX_WIDTH, SharedSchematics.PREVIEW_LINES * lineHeight)
        graphics.disableScissor()
    }

    private fun preview(entry: Shared): Ready? {
        entry.preview?.let { return it }
        if (entry.failed) return null
        val template = entry.template ?: load(entry)?.also { entry.template = it } ?: return null
        if (!template.isDone) return null
        entry.preview = runCatching { build(template.join()) }
            .onFailure {
                entry.failed = true
                CreateBuzzyBeez.LOGGER.warn("Failed to build shared schematic preview ${entry.name}", it)
            }
            .getOrNull()
        return entry.preview
    }

    private fun load(entry: Shared): CompletableFuture<StructureTemplate>? {
        val blocks = Minecraft.getInstance().level?.holderLookup(Registries.BLOCK) ?: return null
        return CompletableFuture.supplyAsync({
            StructureTemplate().apply { load(blocks, NbtIo.readCompressed(ByteArrayInputStream(entry.bytes), NbtAccounter.unlimitedHeap())) }
        }, Util.backgroundExecutor())
    }

    private fun build(template: StructureTemplate): Ready {
        require(template.size != Vec3i.ZERO) { "Schematic is empty" }
        val level = SchematicLevel(Minecraft.getInstance().level ?: error("No client level"))
        template.placeInWorld(level, BlockPos.ZERO, BlockPos.ZERO, StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS)
        level.blockEntities.forEach { it.setLevel(level) }
        val renderer = GhostSchematicRenderer(level, renderAllBlockEntities = true).also { it.prebuildGeometry() }
        return Ready(renderer, template.size)
    }
}
