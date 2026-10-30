package de.devin.cbbees.content.schematics.share

import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.network.SharedSchematicPacket
import net.minecraft.ChatFormatting
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min
import kotlin.random.Random

object SharedSchematics {

    const val MARKER = "cbbees:shared:"
    const val PREVIEW_LINES = 6

    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_PREVIEW_BYTES = 4 * 1024 * 1024
    private val ID_PATTERN = Regex("[0-9a-f]{8}")
    private const val CHUNK_SIZE = 32_768
    private const val PREVIEW_PADDING = 27
    private val sharedDir: Path = CreatePaths.SCHEMATICS_DIR.resolve("shared")

    fun share(player: ServerPlayer, filename: String) {
        val playerDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(player.gameProfile.name).normalize()
        val upload = playerDir.resolve(filename).normalize()
        if (!upload.startsWith(playerDir) || !filename.endsWith(".nbt") || !Files.isRegularFile(upload)) return
        if (Files.size(upload) > MAX_BYTES) {
            player.sendSystemMessage(Component.translatable("message.cbbees.share.too_large", MAX_BYTES / 1024 / 1024).withStyle(ChatFormatting.RED))
            return
        }
        val id = generateSequence { "%08x".format(Random.nextInt()) }.first { !Files.exists(sharedDir.resolve(it)) }
        val copy = sharedDir.resolve(id).resolve(filename)
        try {
            Files.createDirectories(copy.parent)
            Files.copy(upload, copy)
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("Failed to share schematic $filename", e)
            return
        }
        val preview = Files.size(copy) <= MAX_PREVIEW_BYTES
        if (preview) send(id, copy, save = false, PacketDistributor::sendToAllPlayers)
        player.server.playerList.broadcastSystemMessage(announcement(player, id, filename, copy, preview), false)
    }

    fun requestSave(player: ServerPlayer, id: String) {
        val file = find(id)
        if (file == null) {
            player.sendSystemMessage(Component.translatable("message.cbbees.share.missing").withStyle(ChatFormatting.RED))
            return
        }
        send(id, file, save = true) { PacketDistributor.sendToPlayer(player, it) }
    }

    private fun send(id: String, file: Path, save: Boolean, deliver: (SharedSchematicPacket) -> Unit) {
        val bytes = runCatching { Files.readAllBytes(file) }
            .onFailure { CreateBuzzyBeez.LOGGER.error("Failed to read shared schematic $file", it) }
            .getOrNull() ?: return
        val filename = file.fileName.toString()
        for (offset in bytes.indices step CHUNK_SIZE) {
            deliver(SharedSchematicPacket(id, filename, save, bytes.size, bytes.copyOfRange(offset, min(offset + CHUNK_SIZE, bytes.size))))
        }
    }

    private fun find(id: String): Path? {
        val dir = sharedDir.resolve(id)
        if (!ID_PATTERN.matches(id) || !Files.isDirectory(dir)) return null
        return Files.list(dir).use { files -> files.filter { it.toString().endsWith(".nbt") }.findFirst().orElse(null) }
    }

    private fun announcement(player: ServerPlayer, id: String, filename: String, file: Path, preview: Boolean): Component {
        val name = Component.literal(filename.removeSuffix(".nbt")).withStyle(ChatFormatting.GOLD)
        val message = Component.translatable("message.cbbees.share.announcement", player.displayName, name)
        details(file)?.let { message.append("\n").append(it) }
        if (preview) repeat(PREVIEW_LINES) { message.append("\n").append(previewSlot(id, it)) }
        return message.append("\n").append(saveButton(id))
    }

    private fun previewSlot(id: String, index: Int): MutableComponent =
        Component.literal(" ".repeat(PREVIEW_PADDING)).withStyle {
            it.withInsertion("$MARKER$id:$index")
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cbbees shared $id open"))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.cbbees.share.open.hover")))
        }

    private fun saveButton(id: String): MutableComponent =
        Component.translatable("message.cbbees.share.save").withStyle {
            it.withColor(ChatFormatting.GREEN)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cbbees shared $id save"))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.cbbees.share.save.hover")))
        }

    private fun details(file: Path): Component? {
        val summary = runCatching { SchematicSummary.read(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())) }.getOrNull()
            ?: return null
        return Component.translatable("message.cbbees.share.size", summary.sizeX, summary.sizeY, summary.sizeZ)
            .append(Component.literal("  "))
            .append(Component.translatable("message.cbbees.share.blocks", summary.blockCount).withStyle(ChatFormatting.GRAY))
    }
}
