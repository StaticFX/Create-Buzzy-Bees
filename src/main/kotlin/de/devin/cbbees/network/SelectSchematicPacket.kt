package de.devin.cbbees.network

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.schematics.SchematicItem
import com.simibubi.create.foundation.utility.CreatePaths
import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.content.schematics.ConstructionPlannerItem
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Client → Server packet sent when the player confirms a schematic selection
 * in the Construction Planner. Sets ALL data components on the server item
 * so the server-side state matches the client and inventory syncs don't
 * overwrite the client-side deployed state.
 */
class SelectSchematicPacket(
    val schematicName: String,
    val anchor: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror
) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<SelectSchematicPacket>(
            CreateBuzzyBeez.asResource("select_schematic")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SelectSchematicPacket> = StreamCodec.of(
            { buf, p ->
                buf.writeUtf(p.schematicName)
                buf.writeBlockPos(p.anchor)
                buf.writeEnum(p.rotation)
                buf.writeEnum(p.mirror)
            },
            { buf ->
                SelectSchematicPacket(
                    buf.readUtf(),
                    buf.readBlockPos(),
                    buf.readEnum(Rotation::class.java),
                    buf.readEnum(Mirror::class.java)
                )
            }
        )

        fun handle(payload: SelectSchematicPacket, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val player = ctx.player() as? ServerPlayer ?: return@enqueueWork
                val stack = ConstructionPlannerItem.findPlanner(player)

                if (stack.isEmpty) return@enqueueWork

                // Sanitize filename
                val name = payload.schematicName
                if (name.contains("..") || name.contains("/") || name.contains("\\")) return@enqueueWork

                val owner = player.gameProfile.name

                // Ensure the schematic file exists in the server's uploaded directory.
                // The Construction Planner bypasses Create's SchematicTable upload flow,
                // so we copy the file from schematics/ to schematics/uploaded/<owner>/.
                ensureSchematicUploaded(owner, name)

                // Set ALL data components so inventory sync doesn't clobber client state
                stack.set(AllDataComponents.SCHEMATIC_FILE, name)
                stack.set(AllDataComponents.SCHEMATIC_OWNER, owner)
                stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
                stack.set(AllDataComponents.SCHEMATIC_ANCHOR, payload.anchor)
                stack.set(AllDataComponents.SCHEMATIC_ROTATION, payload.rotation)
                stack.set(AllDataComponents.SCHEMATIC_MIRROR, payload.mirror)

                // Write bounds now that the file is available
                try {
                    SchematicItem.writeSize(player.level(), stack)
                } catch (_: Exception) {
                    CreateBuzzyBeez.LOGGER.warn("Failed to write schematic bounds for $name")
                }
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}

/**
 * Ensures a schematic file exists in the server's uploaded directory.
 *
 * Create's [SchematicItem.loadSchematic] on the server looks for files at
 * `schematics/uploaded/<owner>/<filename>`. The Construction Planner bypasses
 * Create's SchematicTable upload flow, so the file may only exist in the
 * client-side `schematics/` directory. For integrated servers (singleplayer/LAN),
 * both directories are on the same machine, so we can simply copy the file.
 */
fun ensureSchematicUploaded(owner: String, schematicName: String) {
    val uploadedDir = CreatePaths.UPLOADED_SCHEMATICS_DIR.resolve(owner)
    val uploadedFile = uploadedDir.resolve(schematicName).normalize()

    // Security: ensure resolved path is within the uploaded directory
    if (!uploadedFile.startsWith(CreatePaths.UPLOADED_SCHEMATICS_DIR)) return

    if (Files.exists(uploadedFile)) return

    // Try to copy from the client-side schematics directory
    val sourceFile = CreatePaths.SCHEMATICS_DIR.resolve(schematicName).normalize()
    if (!sourceFile.startsWith(CreatePaths.SCHEMATICS_DIR)) return

    if (!Files.exists(sourceFile)) {
        CreateBuzzyBeez.LOGGER.warn("Schematic file not found: $sourceFile")
        return
    }

    try {
        Files.createDirectories(uploadedDir)
        Files.copy(sourceFile, uploadedFile, StandardCopyOption.REPLACE_EXISTING)
        CreateBuzzyBeez.LOGGER.info("Copied schematic $schematicName to uploaded directory for $owner")
    } catch (e: Exception) {
        CreateBuzzyBeez.LOGGER.error("Failed to copy schematic $schematicName: ${e.message}")
    }
}
