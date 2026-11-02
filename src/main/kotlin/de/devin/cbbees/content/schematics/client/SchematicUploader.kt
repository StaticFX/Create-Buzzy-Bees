package de.devin.cbbees.content.schematics.client

import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.network.PlannerUploadPacket
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.network.PacketDistributor
import java.io.InputStream
import java.nio.file.Files

/**
 * Client-side schematic upload manager for the Construction Planner.
 *
 * Reads a schematic file from the client's `schematics/` directory and sends it
 * to the server in chunks via [PlannerUploadPacket]. Displays a progress bar in
 * the actionbar during upload.
 *
 * Usage:
 * ```
 * SchematicUploader.startUpload("my_build.nbt") {
 *     // Called when upload is complete — send selection/construction packet
 * }
 * ```
 *
 * Call [tick] every client tick to pump chunks and update progress.
 */
@OnlyIn(Dist.CLIENT)
object SchematicUploader {

    private var activeUpload: UploadState? = null

    /** Number of chunks to send per tick. */
    private const val CHUNKS_PER_TICK = 5

    private class UploadState(
        val filename: String,
        val inputStream: InputStream,
        val totalSize: Long,
        val onComplete: () -> Unit
    ) {
        var bytesSent: Long = 0
        var finished = false
    }

    /**
     * Returns true if the schematic file already exists in the server's uploaded
     * directory. For integrated servers this is a local file check; for dedicated
     * servers the file was either previously uploaded or needs uploading now.
     */
    fun isAlreadyUploaded(owner: String, filename: String): Boolean {
        // Only meaningful for integrated server (singleplayer/LAN)
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            val uploaded = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner).resolve(filename)
            return Files.exists(uploaded)
        }
        // On a dedicated server, assume we need to upload unless we know otherwise
        return false
    }

    /**
     * Starts an asynchronous upload of the given schematic file.
     *
     * @param filename The schematic filename (e.g. "my_build.nbt")
     * @param onComplete Called on the client thread when the upload finishes.
     */
    fun startUpload(filename: String, onComplete: () -> Unit) {
        // Cancel any active upload
        cancel()

        val path = CreatePaths.SCHEMATICS_DIR.resolve(filename)
        if (!Files.exists(path)) {
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("Schematic file not found: $filename").withStyle { it.withColor(0xFF5555) },
                true
            )
            return
        }

        val totalSize = Files.size(path)
        val inputStream = Files.newInputStream(path)

        activeUpload = UploadState(filename, inputStream, totalSize, onComplete)

        // Send BEGIN packet
        PacketDistributor.sendToServer(
            PlannerUploadPacket(PlannerUploadPacket.BEGIN, filename, totalSize, ByteArray(0))
        )
    }

    /**
     * Call every client tick to send upload chunks and display progress.
     */
    fun tick() {
        val upload = activeUpload ?: return

        if (upload.finished) {
            activeUpload = null
            return
        }

        val chunkSize = PlannerUploadPacket.CHUNK_SIZE
        val buffer = ByteArray(chunkSize)

        for (i in 0 until CHUNKS_PER_TICK) {
            val bytesRead = upload.inputStream.read(buffer)

            if (bytesRead == -1) {
                // End of file — send FINISH
                PacketDistributor.sendToServer(
                    PlannerUploadPacket(PlannerUploadPacket.FINISH, upload.filename, 0, ByteArray(0))
                )

                upload.inputStream.close()
                upload.finished = true

                // Show completion message
                Minecraft.getInstance().player?.displayClientMessage(
                    Component.literal("Upload complete!").withStyle { it.withColor(0x55FF55) },
                    true
                )

                // Execute callback
                upload.onComplete()
                return
            }

            val data = if (bytesRead < chunkSize) buffer.copyOf(bytesRead) else buffer.clone()
            upload.bytesSent += bytesRead

            PacketDistributor.sendToServer(
                PlannerUploadPacket(PlannerUploadPacket.WRITE, upload.filename, 0, data)
            )

            // If we read less than a full chunk, we're at the end
            if (bytesRead < chunkSize) {
                PacketDistributor.sendToServer(
                    PlannerUploadPacket(PlannerUploadPacket.FINISH, upload.filename, 0, ByteArray(0))
                )

                upload.inputStream.close()
                upload.finished = true

                Minecraft.getInstance().player?.displayClientMessage(
                    Component.literal("Upload complete!").withStyle { it.withColor(0x55FF55) },
                    true
                )

                upload.onComplete()
                return
            }
        }

        // Show progress bar
        showProgress(upload)
    }

    private fun showProgress(upload: UploadState) {
        val progress = if (upload.totalSize > 0) {
            (upload.bytesSent.toFloat() / upload.totalSize).coerceIn(0f, 1f)
        } else 0f

        val barLength = 20
        val filled = (progress * barLength).toInt()
        val bar = "\u2588".repeat(filled) + "\u2591".repeat(barLength - filled)
        val percent = (progress * 100).toInt()
        val sizeKB = upload.totalSize / 1024

        val message = Component.literal("Uploading schematic ($sizeKB KB)  [$bar] $percent%")
            .withStyle { it.withColor(0xFFFF00) }

        Minecraft.getInstance().player?.displayClientMessage(message, true)
    }

    fun isUploading(): Boolean = activeUpload != null && activeUpload?.finished != true

    fun cancel() {
        activeUpload?.let {
            runCatching { it.inputStream.close() }
            activeUpload = null
        }
    }
}
