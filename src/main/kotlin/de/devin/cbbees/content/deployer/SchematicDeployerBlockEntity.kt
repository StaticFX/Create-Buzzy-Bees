package de.devin.cbbees.content.deployer

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import de.devin.cbbees.content.bee.debug.BeeDebug
import de.devin.cbbees.content.domain.GlobalJobPool
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.ClientBatchInfo
import de.devin.cbbees.content.domain.job.ClientJobInfo
import de.devin.cbbees.content.domain.job.ClientNetworkInfo
import de.devin.cbbees.content.domain.job.HiveSnapshot
import de.devin.cbbees.content.domain.job.JobStatus
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.SchematicJobKey
import de.devin.cbbees.network.HiveJobsSyncPacket
import de.devin.cbbees.registry.AllDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

class SchematicDeployerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : SmartBlockEntity(type, pos, state) {

    /**
     * Deploy result states — synced to client for renderer feedback.
     * Cleared automatically after a short duration.
     */
    enum class DeployResult {
        NONE,
        SUCCESS,
        FAIL_BUSY,
        FAIL_EMPTY,
        FAIL_NO_TASKS
    }

    var heldItem: ItemStack = ItemStack.EMPTY
    var activeJobId: UUID? = null
        private set
    private var lazyTickCounter = 0

    /**
     * Deploy mode: ABSOLUTE uses stored coordinates as-is,
     * RELATIVE computes target from deployer position + relativeOffset.
     */
    var deployMode: DeployMode = DeployMode.ABSOLUTE

    /**
     * Offset from the deployer to the build reference point (in RELATIVE mode).
     * The actual build anchor = deployerPos + relativeOffset.
     */
    var relativeOffset: BlockPos = BlockPos.ZERO

    /** Rotation override for relative mode (construction only). */
    var relativeRotation: Rotation = Rotation.NONE

    /** Mirror override for relative mode (construction only). */
    var relativeMirror: Mirror = Mirror.NONE

    /** Resets deploy settings when the held item is manually changed. */
    fun resetSettings() {
        deployMode = DeployMode.ABSOLUTE
        relativeOffset = BlockPos.ZERO
        relativeRotation = Rotation.NONE
        relativeMirror = Mirror.NONE
    }

    /** Current deploy result — synced to client via sendData(). */
    var deployResult: DeployResult = DeployResult.NONE
        private set

    /** Server tick when the result was set — used for auto-clear. */
    private var resultSetTick: Long = 0

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        // No special behaviours needed
    }

    override fun tick() {
        super.tick()
        val level = level ?: return
        if (level.isClientSide) return

        // Auto-clear deploy result after 2 seconds (40 ticks)
        if (deployResult != DeployResult.NONE) {
            if (level.gameTime - resultSetTick >= 40) {
                deployResult = DeployResult.NONE
                sendData()
            }
        }

        // Lazy tick every ~2 seconds (40 ticks)
        lazyTickCounter++
        if (lazyTickCounter >= 40) {
            lazyTickCounter = 0
            checkJobStatus()
        }
    }

    private fun checkJobStatus() {
        val jobId = activeJobId ?: return
        val level = level ?: return

        // Check if the job is completed or cancelled
        val job = GlobalJobPool.getAllJobs().find { it.jobId == jobId }
        if (job == null || job.status == JobStatus.COMPLETED || job.status == JobStatus.CANCELLED) {
            val reason = if (job == null) "job not found" else "status=${job.status}"
            BeeDebug.logAtPos(level, blockPos, "Deployer", "Job ${jobId.toString().substring(0, 8)} finished ($reason), ready for next deploy")
            activeJobId = null
            syncJobClearedToClients()
            setChanged()
            sendData()
            level.updateNeighbourForOutputSignal(blockPos, blockState.block)
        }
    }

    /**
     * Returns the comparator signal strength for this deployer.
     * 0 = empty, 1 = has schematic (idle), 8 = job active, 15 = just deployed
     */
    fun getComparatorOutput(): Int {
        if (deployResult == DeployResult.SUCCESS) return 15
        if (activeJobId != null) {
            val job = GlobalJobPool.getAllJobs().find { it.jobId == activeJobId }
            if (job != null && job.status != JobStatus.COMPLETED && job.status != JobStatus.CANCELLED) return 8
        }
        if (!heldItem.isEmpty && heldItem.has(AllDataComponents.SCHEMATIC_PROGRAM)) return 1
        return 0
    }

    private fun setResult(result: DeployResult) {
        deployResult = result
        resultSetTick = level?.gameTime ?: 0
        sendData()
    }

    /**
     * Resolves the final program to deploy based on the current deploy mode.
     * ABSOLUTE: uses stored coordinates as-is
     * RELATIVE: computes target from deployer position + offset
     */
    private fun resolveProgram(storedProgram: SchematicProgram): SchematicProgram {
        return when (deployMode) {
            DeployMode.ABSOLUTE -> storedProgram
            DeployMode.RELATIVE -> {
                val targetPoint = blockPos.offset(relativeOffset)
                when (storedProgram) {
                    is SchematicProgram.Construction -> {
                        // In relative mode, override anchor, rotation, and mirror
                        storedProgram.copy(
                            anchor = targetPoint,
                            rotation = relativeRotation,
                            mirror = relativeMirror
                        )
                    }
                    is SchematicProgram.Deconstruction -> {
                        val referencePoint = BlockPos(
                            (storedProgram.corner1.x + storedProgram.corner2.x) / 2,
                            (storedProgram.corner1.y + storedProgram.corner2.y) / 2,
                            (storedProgram.corner1.z + storedProgram.corner2.z) / 2
                        )
                        val delta = targetPoint.subtract(referencePoint)
                        storedProgram.relocate(delta)
                    }
                    is SchematicProgram.Pickup -> {
                        val referencePoint = BlockPos(
                            (storedProgram.corner1.x + storedProgram.corner2.x) / 2,
                            (storedProgram.corner1.y + storedProgram.corner2.y) / 2,
                            (storedProgram.corner1.z + storedProgram.corner2.z) / 2
                        )
                        val delta = targetPoint.subtract(referencePoint)
                        storedProgram.relocate(delta)
                    }
                }
            }
        }
    }

    /**
     * Attempts to deploy the programmed schematic as a bee job.
     * Called on redstone rising edge.
     */
    fun deploy() {
        val level = level ?: return
        if (level.isClientSide) return
        val serverLevel = level as? ServerLevel ?: return
        val center = blockPos.above()
        val cx = center.x + 0.5
        val cy = center.y + 0.5
        val cz = center.z + 0.5
        val tag = "Deployer"

        BeeDebug.logAtPos(level, blockPos, tag, "Redstone signal received, attempting deploy (mode=$deployMode)")

        // Check if a job is already active
        if (activeJobId != null) {
            val existingJob = GlobalJobPool.getAllJobs().find { it.jobId == activeJobId }
            if (existingJob != null && existingJob.status != JobStatus.COMPLETED && existingJob.status != JobStatus.CANCELLED) {
                // Busy — orange flame particles + villager "no" sound
                BeeDebug.logAtPos(level, blockPos, tag, "FAIL: Job already active (id=${activeJobId.toString().substring(0, 8)}, status=${existingJob.status})")
                serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 6, 0.2, 0.1, 0.2, 0.02)
                level.playSound(null, blockPos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6f, 1.2f)
                setResult(DeployResult.FAIL_BUSY)
                return
            }
            BeeDebug.logAtPos(level, blockPos, tag, "Previous job finished, clearing activeJobId")
            activeJobId = null
        }

        if (heldItem.isEmpty || !heldItem.has(AllDataComponents.SCHEMATIC_PROGRAM)) {
            // Empty — puff of smoke + click
            BeeDebug.logAtPos(level, blockPos, tag, "FAIL: No programmed schematic inserted")
            serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 4, 0.15, 0.1, 0.15, 0.01)
            level.playSound(null, blockPos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 1.2f)
            setResult(DeployResult.FAIL_EMPTY)
            return
        }

        val storedProgram = heldItem.get(AllDataComponents.SCHEMATIC_PROGRAM)!!
        val program = resolveProgram(storedProgram)
        BeeDebug.logAtPos(level, blockPos, tag, "Resolved program: ${program.displayName()}")

        val jobId = UUID.randomUUID()
        val centerPos = program.getCenterPos()

        val job = BeeJob(jobId, centerPos, level, program.jobType).apply {
            uniquenessKey = SchematicJobKey(
                UUID(blockPos.asLong(), blockPos.asLong()),
                "deployer_${blockPos.x}_${blockPos.y}_${blockPos.z}",
                centerPos.x, centerPos.y, centerPos.z
            )
            program.configureJob(this)
        }

        val batches = program.generateBatches(level, job)
        if (batches.isEmpty()) {
            // No tasks — small smoke + note block bass
            BeeDebug.logAtPos(level, blockPos, tag, "FAIL: No tasks generated (all blocks already placed or area empty)")
            serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 4, 0.15, 0.1, 0.15, 0.01)
            level.playSound(null, blockPos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6f, 0.5f)
            setResult(DeployResult.FAIL_NO_TASKS)
            return
        }

        // Inject this deployer's config into any deployer blocks in the build
        // so they are placed already programmed with the same schematic
        if (!heldItem.isEmpty) {
            injectDeployerConfig(batches, tag)
        }

        job.addBatches(batches)
        activeJobId = job.jobId
        GlobalJobPool.dispatchNewJob(job)

        // Immediately sync job info to nearby clients so ghost blocks render
        syncJobToClients(job, batches)

        val totalTasks = batches.sumOf { it.tasks.size }
        BeeDebug.logAtPos(level, blockPos, tag, "SUCCESS: Deployed job ${jobId.toString().substring(0, 8)} with ${batches.size} batch(es), $totalTasks task(s)")

        // Success — happy particles + bell chime
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy, cz, 8, 0.3, 0.2, 0.3, 1.0)
        serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.25, cz, 4, 0.05, 0.4, 0.05, 0.01)
        level.playSound(null, blockPos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.5f)
        setResult(DeployResult.SUCCESS)

        setChanged()
        level.updateNeighbourForOutputSignal(blockPos, blockState.block)
    }

    /**
     * Injects this deployer's held item and deploy settings into any
     * [PlaceBlockAction] tasks that place a [SchematicDeployerBlock],
     * so child deployers are already programmed when placed by bees.
     */
    private fun injectDeployerConfig(batches: List<TaskBatch>, debugTag: String) {
        val registries = level?.registryAccess() ?: return
        val configNbt = CompoundTag().apply {
            put("HeldItem", heldItem.save(registries))
            putInt("DeployMode", deployMode.ordinal)
            putLong("RelativeOffset", relativeOffset.asLong())
            putInt("RelativeRotation", relativeRotation.ordinal)
            putInt("RelativeMirror", relativeMirror.ordinal)
        }

        var injected = 0
        for (batch in batches) {
            for (task in batch.tasks) {
                val action = task.action
                if (action is PlaceBlockAction && action.blockState.block is SchematicDeployerBlock) {
                    val existingTag = action.blockEntityTag
                    if (existingTag != null) {
                        existingTag.merge(configNbt)
                    }
                    injected++
                }
            }
        }
        if (injected > 0) {
            BeeDebug.logAtPos(level!!, blockPos, debugTag, "Injected deployer config into $injected child deployer task(s)")
        }
    }

    /**
     * Sends the newly deployed job to clients via [HiveJobsSyncPacket]
     * so [ConstructionRenderer] can render ghost blocks immediately.
     *
     * Sable/physics worlds can expose block entities through a level wrapper that is
     * not the exact same ServerLevel instance as the player's normal world. The old
     * `player.level() == level` check could therefore prevent the client from ever
     * receiving the active deployer job, causing the unfinished-job frame/ghost to
     * disappear only when the deployer was on Sable.
     */
    private fun syncJobToClients(job: BeeJob, batches: List<TaskBatch>) {
        val serverLevel = level as? ServerLevel ?: return
        val clientBatches = batches.map { b ->
            val ghostBlocks = b.tasks.mapNotNull { task ->
                val action = task.action
                if (action is PlaceBlockAction) action.pos to action.blockState else null
            }.toMap()

            ClientBatchInfo(
                status = b.status.name,
                target = b.targetPosition,
                required = emptyList(),
                assignedBeeIds = emptyList(),
                ghostBlocks = ghostBlocks
            )
        }
        val clientJob = ClientJobInfo(
            jobId = job.jobId,
            name = job.jobId.toString().substring(0, 6).uppercase(),
            status = job.status.name,
            completed = 0,
            total = job.tasks.size,
            reason = null,
            batches = clientBatches,
            schematicPlacement = job.schematicPlacement,
            jobType = job.jobType
        )
        val snapshot = HiveSnapshot(ClientNetworkInfo("Deployer", 0, 0, 0), listOf(clientJob))
        sendDeployerSnapshotToClients(serverLevel, snapshot)
    }

    /**
     * Sends an empty snapshot for this deployer's position to clear stale job entries
     * from [ClientJobCache] when the job completes.
     */
    private fun syncJobClearedToClients() {
        val serverLevel = level as? ServerLevel ?: return
        val snapshot = HiveSnapshot(ClientNetworkInfo("Deployer", 0, 0, 0), emptyList())
        sendDeployerSnapshotToClients(serverLevel, snapshot)
    }

    private fun sendDeployerSnapshotToClients(serverLevel: ServerLevel, snapshot: HiveSnapshot) {
        val packet = HiveJobsSyncPacket(blockPos, snapshot)
        val players = serverLevel.server.playerList.players

        val nearbyPlayers = players.filter { player ->
            shouldReceiveDeployerSnapshot(player, serverLevel)
        }

        // If Sable/shipyard coordinates prevent a normal distance match, still send
        // this small snapshot to all connected clients so the owner can render/clear
        // the active deployer job. This only runs on deploy and on job clear.
        val targets = nearbyPlayers.ifEmpty { players }

        for (player in targets) {
            PacketDistributor.sendToPlayer(player, packet)
        }
    }

    private fun shouldReceiveDeployerSnapshot(player: ServerPlayer, serverLevel: ServerLevel): Boolean {
        if (player.level() == serverLevel && player.blockPosition().closerThan(blockPos, 128.0)) {
            return true
        }

        // Some Sable/physics wrappers keep the same dimension key but do not compare
        // equal as level instances. Accept same-dimension nearby players too.
        if (player.level().dimension() == serverLevel.dimension() && player.blockPosition().closerThan(blockPos, 128.0)) {
            return true
        }

        // Final coordinate-space fallback for wrapper levels that report a different
        // dimension object but still use visible world-space coordinates.
        return player.blockPosition().closerThan(blockPos, 128.0)
    }

    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        if (!heldItem.isEmpty) {
            tag.put("HeldItem", heldItem.save(registries))
        }
        activeJobId?.let { tag.putUUID("ActiveJobId", it) }
        tag.putInt("DeployMode", deployMode.ordinal)
        tag.putLong("RelativeOffset", relativeOffset.asLong())
        tag.putInt("RelativeRotation", relativeRotation.ordinal)
        tag.putInt("RelativeMirror", relativeMirror.ordinal)
        if (clientPacket) {
            tag.putInt("DeployResult", deployResult.ordinal)
        }
    }

    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        heldItem = if (tag.contains("HeldItem")) {
            ItemStack.parseOptional(registries, tag.getCompound("HeldItem"))
        } else {
            ItemStack.EMPTY
        }
        activeJobId = if (tag.hasUUID("ActiveJobId")) tag.getUUID("ActiveJobId") else null
        deployMode = if (tag.contains("DeployMode")) {
            DeployMode.entries.getOrElse(tag.getInt("DeployMode")) { DeployMode.ABSOLUTE }
        } else {
            DeployMode.ABSOLUTE
        }
        relativeOffset = if (tag.contains("RelativeOffset")) {
            BlockPos.of(tag.getLong("RelativeOffset"))
        } else {
            BlockPos.ZERO
        }
        relativeRotation = if (tag.contains("RelativeRotation")) {
            Rotation.entries.getOrElse(tag.getInt("RelativeRotation")) { Rotation.NONE }
        } else {
            Rotation.NONE
        }
        relativeMirror = if (tag.contains("RelativeMirror")) {
            Mirror.entries.getOrElse(tag.getInt("RelativeMirror")) { Mirror.NONE }
        } else {
            Mirror.NONE
        }
        if (clientPacket && tag.contains("DeployResult")) {
            val ord = tag.getInt("DeployResult")
            deployResult = DeployResult.entries.getOrElse(ord) { DeployResult.NONE }
        }
    }
}
