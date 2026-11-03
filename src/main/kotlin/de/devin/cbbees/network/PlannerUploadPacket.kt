package de.devin.cbbees.network

import com.simibubi.create.foundation.utility.CreatePaths
import com.simibubi.create.foundation.utility.FilesHelper
import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.io.OutputStream
import java.nio.file.Files

/**
 * Client → Server packet for uploading schematic files from the Construction Planner.
 *
 * The Construction Planner bypasses Create's SchematicTable upload flow, so we
 * implement our own chunked upload protocol. The packet has three actions:
 * - [BEGIN]: Opens an output stream on the server for the schematic file.
 * - [WRITE]: Writes a data chunk to the stream.
 * - [FINISH]: Closes the stream, completing the upload.
 */
class PlannerUploadPacket(
    val action: Int,
    val schematicName: String,
    val totalSize: Long,
    val data: ByteArray
) : CustomPacketPayload {

    companion object {
        const val BEGIN = 0
        const val WRITE = 1
        const val FINISH = 2

        /** Max bytes per chunk. */
        const val CHUNK_SIZE = 32_768

        val TYPE = CustomPacketPayload.Type<PlannerUploadPacket>(
            CreateBuzzyBeez.asResource("planner_upload")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PlannerUploadPacket> = StreamCodec.of(
            { buf, pkt ->
                buf.writeVarInt(pkt.action)
                buf.writeUtf(pkt.schematicName)
                buf.writeVarLong(pkt.totalSize)
                buf.writeByteArray(pkt.data)
            },
            { buf ->
                PlannerUploadPacket(
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readVarLong(),
                    buf.readByteArray()
                )
            }
        )

        /** Active upload streams keyed by "playerName/schematicName". */
        private val activeStreams = mutableMapOf<String, UploadEntry>()

        private class UploadEntry(
            val stream: OutputStream,
            val totalBytes: Long,
            var bytesWritten: Long = 0
        )

        fun handle(payload: PlannerUploadPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val owner = player.gameProfile.name
                val name = payload.schematicName

                // Sanitize
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return@enqueueWork
                if (!name.endsWith(".nbt")) return@enqueueWork

                val key = "$owner/$name"

                when (payload.action) {
                    BEGIN -> handleBegin(key, owner, name, payload.totalSize)
                    WRITE -> handleWrite(key, payload.data)
                    FINISH -> handleFinish(key)
                }
            }
        }

        private fun handleBegin(key: String, owner: String, name: String, totalSize: Long) {
            // Close any existing upload for this key
            activeStreams.remove(key)?.stream?.close()

            val baseDir = CreatePaths.UPLOADED_SCHEMATICS_DIR
            val playerDir = baseDir.resolve(owner).normalize()
            val uploadPath = playerDir.resolve(name).normalize()

            if (!playerDir.startsWith(baseDir) || !uploadPath.startsWith(playerDir)) {
                CreateBuzzyBeez.LOGGER.warn("Path traversal in planner upload: $key")
                return
            }

            try {
                FilesHelper.createFolderIfMissing(playerDir)
                Files.deleteIfExists(uploadPath)
                val stream = Files.newOutputStream(uploadPath)
                activeStreams[key] = UploadEntry(stream, totalSize)
                CreateBuzzyBeez.debug { "Started planner upload: $key ($totalSize bytes)" }
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Failed to start planner upload: $key", e)
            }
        }

        private fun handleWrite(key: String, data: ByteArray) {
            val entry = activeStreams[key] ?: return
            try {
                entry.stream.write(data)
                entry.bytesWritten += data.size

                if (entry.bytesWritten > entry.totalBytes) {
                    CreateBuzzyBeez.LOGGER.warn("Received more data than expected for $key, cancelling")
                    entry.stream.close()
                    activeStreams.remove(key)
                }
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Error writing planner upload chunk: $key", e)
                try { entry.stream.close() } catch (_: Exception) {}
                activeStreams.remove(key)
            }
        }

        private fun handleFinish(key: String) {
            val entry = activeStreams.remove(key) ?: return
            try {
                entry.stream.close()
                CreateBuzzyBeez.debug { "Completed planner upload: $key (${entry.bytesWritten} bytes)" }
            } catch (e: Exception) {
                CreateBuzzyBeez.LOGGER.error("Error finishing planner upload: $key", e)
            }
        }

        /** Cleans up any lingering streams (call on server stop). */
        fun shutdown() {
            activeStreams.values.forEach { runCatching { it.stream.close() } }
            activeStreams.clear()
        }
    }

    override fun type(): CustomPacketPayload.Type<PlannerUploadPacket> = TYPE
}
