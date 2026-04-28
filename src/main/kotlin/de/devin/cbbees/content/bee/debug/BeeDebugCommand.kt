package de.devin.cbbees.content.bee.debug

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import de.devin.cbbees.content.bee.client.BeeClientTracker
import de.devin.cbbees.content.bee.flight.ClientBeeFlightData
import de.devin.cbbees.content.bee.flight.ClientCheckpoint
import de.devin.cbbees.content.bee.server.BeeType
import de.devin.cbbees.content.domain.job.JobCalculationProgress
import de.devin.cbbees.content.domain.network.ServerBeeNetworkManager
import de.devin.cbbees.network.JobProgressPacket
import de.devin.cbbees.util.ServerTickScheduler
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

object BeeDebugCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cbbees")
                .then(
                    Commands.literal("debug")
                        .executes { ctx ->
                            val player = ctx.source.playerOrException
                            val enabled = BeeDebug.toggle(player)
                            val msg = if (enabled)
                                Component.literal("Bee debug mode enabled").withStyle(ChatFormatting.GREEN)
                            else
                                Component.literal("Bee debug mode disabled").withStyle(ChatFormatting.RED)
                            ctx.source.sendSuccess({ msg }, false)
                            1
                        }
                )
                .then(
                    Commands.literal("preview")
                        .then(
                            Commands.argument("type", StringArgumentType.word())
                                .suggests { _, builder ->
                                    BeeType.entries.forEach { builder.suggest(it.name.lowercase()) }
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val typeName = StringArgumentType.getString(ctx, "type")
                                    val beeType = BeeType.entries.find { it.name.equals(typeName, ignoreCase = true) }
                                    if (beeType == null) {
                                        ctx.source.sendFailure(Component.literal("Unknown bee type: $typeName"))
                                        return@executes 0
                                    }
                                    spawnPreviewBee(ctx.source, beeType)
                                    ctx.source.sendSuccess({
                                        Component.literal("Spawned ${beeType.name.lowercase()} preview")
                                            .withStyle(ChatFormatting.GREEN)
                                    }, false)
                                    1
                                }
                        )
                        .executes { ctx ->
                            spawnPreviewBee(ctx.source, BeeType.CONSTRUCTION)
                            ctx.source.sendSuccess({
                                Component.literal("Spawned construction bee preview")
                                    .withStyle(ChatFormatting.GREEN)
                            }, false)
                            1
                        }
                )
                .then(
                    Commands.literal("clearpreview")
                        .executes { ctx ->
                            BeeClientTracker.clearPreviews()
                            ctx.source.sendSuccess({
                                Component.literal("Cleared all preview bees").withStyle(ChatFormatting.YELLOW)
                            }, false)
                            1
                        }
                )
                .then(
                    Commands.literal("toast")
                        .executes { ctx ->
                            simulateToast(ctx.source)
                            ctx.source.sendSuccess({
                                Component.literal("Playing toast animation").withStyle(ChatFormatting.GREEN)
                            }, false)
                            1
                        }
                )
                .then(
                    Commands.literal("resetnetworks")
                        .requires { it.hasPermission(2) }
                        .executes { ctx ->
                            val count = ServerBeeNetworkManager.getNetworks().size
                            ServerBeeNetworkManager.clear()
                            // Re-register all block entities — they'll rebuild networks from scratch on next tick
                            ctx.source.sendSuccess({
                                Component.literal("Cleared $count networks. Re-equip backpacks and reload chunks to rebuild.")
                                    .withStyle(ChatFormatting.YELLOW)
                            }, true)
                            1
                        }
                )
        )
    }

    private fun spawnPreviewBee(source: CommandSourceStack, type: BeeType) {
        val player = source.playerOrException
        val look = player.lookAngle
        val center = player.eyePosition.add(look.scale(4.0))
        val pos = BlockPos.containing(center)

        // Small orbit: 4 waypoints in a 2-block square with long pauses so the bee circles slowly
        val checkpoints = listOf(
            ClientCheckpoint(pos.offset(1, 0, 1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(-1, 0, 1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(-1, 0, -1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(1, 0, -1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(1, 0, 1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(-1, 0, 1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(-1, 0, -1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(1, 0, -1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(1, 0, 1), pauseTicks = 20),
            ClientCheckpoint(pos.offset(-1, 0, 1), pauseTicks = 20),
        )

        val id = UUID.randomUUID()
        val flightData = ClientBeeFlightData(
            id = id,
            type = type,
            speed = 0.15f,
            checkpoints = checkpoints,
        )

        BeeClientTracker.applyFlightPlan(flightData)
        BeeClientTracker.addPreviewId(id)
    }

    private fun simulateToast(source: CommandSourceStack) {
        val player = source.playerOrException as ServerPlayer
        val jobId = UUID.randomUUID()
        val expectedBlocks = 10_000
        val totalSteps = 20
        val blocksPerStep = expectedBlocks / totalSteps

        // STARTED
        PacketDistributor.sendToPlayer(player, JobProgressPacket(
            jobId, JobCalculationProgress.Phase.STARTED,
            "cbbees.progress.processing_schematic", 0, expectedBlocks,
        ))

        // Schedule progress updates over ~2 seconds (1 per tick)
        for (step in 1..totalSteps) {
            val processed = (blocksPerStep * step).coerceAtMost(expectedBlocks)
            val isLast = step == totalSteps

            // Delay each step by N ticks
            var remaining = step
            fun schedule(action: () -> Unit) {
                if (remaining <= 0) { action(); return }
                remaining--
                ServerTickScheduler.nextTick { schedule(action) }
            }
            schedule {
                if (isLast) {
                    PacketDistributor.sendToPlayer(player, JobProgressPacket(
                        jobId, JobCalculationProgress.Phase.COMPLETED,
                        "cbbees.progress.processing_schematic", expectedBlocks, expectedBlocks,
                        "cbbees.construction.started", 234,
                    ))
                } else {
                    PacketDistributor.sendToPlayer(player, JobProgressPacket(
                        jobId, JobCalculationProgress.Phase.IN_PROGRESS,
                        "cbbees.progress.processing_schematic", processed, expectedBlocks,
                    ))
                }
            }
        }
    }
}
