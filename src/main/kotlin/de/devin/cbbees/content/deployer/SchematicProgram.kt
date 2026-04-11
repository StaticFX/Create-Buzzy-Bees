package de.devin.cbbees.content.deployer

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.simibubi.create.AllDataComponents
import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.task.TaskBatch
import de.devin.cbbees.content.schematics.SchematicCreateBridge
import de.devin.cbbees.network.ensureSchematicUploaded
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation

/**
 * Sealed class representing a programmed schematic — either a construction or deconstruction job.
 * Stored as a data component on a [ProgrammedSchematicItem].
 */
sealed class SchematicProgram {

    abstract fun generateBatches(level: Level, job: BeeJob): List<TaskBatch>
    abstract fun displayName(): String

    /**
     * Returns a copy of this program with all world coordinates offset by [delta].
     * Used by the Schematic Deployer for self-populating schematics — when bees
     * recreate a deployer at a new position, the delta shifts the build target
     * so the schematic is placed relative to the new deployer.
     */
    abstract fun relocate(delta: BlockPos): SchematicProgram

    /**
     * Construction program: loads a schematic file and generates build tasks.
     */
    data class Construction(
        val schematicName: String,
        val anchor: BlockPos,
        val rotation: Rotation,
        val mirror: Mirror,
        val owner: String
    ) : SchematicProgram() {

        override fun generateBatches(level: Level, job: BeeJob): List<TaskBatch> {
            ensureSchematicUploaded(owner, schematicName)

            val stack = ItemStack(com.simibubi.create.AllItems.SCHEMATIC.get())
            stack.set(AllDataComponents.SCHEMATIC_FILE, schematicName)
            stack.set(AllDataComponents.SCHEMATIC_OWNER, owner)
            stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, true)
            stack.set(AllDataComponents.SCHEMATIC_ANCHOR, anchor)
            stack.set(AllDataComponents.SCHEMATIC_ROTATION, rotation)
            stack.set(AllDataComponents.SCHEMATIC_MIRROR, mirror)

            val bridge = SchematicCreateBridge(level)
            if (!bridge.loadSchematic(stack)) return emptyList()
            return bridge.generateBuildTasks(job)
        }

        override fun displayName(): String = schematicName.removeSuffix(".nbt")

        override fun relocate(delta: BlockPos): Construction {
            if (delta == BlockPos.ZERO) return this
            return copy(anchor = anchor.offset(delta))
        }

        companion object {
            val CODEC: MapCodec<Construction> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    Codec.STRING.fieldOf("schematic_name").forGetter { it.schematicName },
                    BlockPos.CODEC.fieldOf("anchor").forGetter { it.anchor },
                    Rotation.CODEC.fieldOf("rotation").forGetter { it.rotation },
                    Mirror.CODEC.fieldOf("mirror").forGetter { it.mirror },
                    Codec.STRING.fieldOf("owner").forGetter { it.owner }
                ).apply(instance, ::Construction)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Construction> = StreamCodec.of(
                { buf, p ->
                    buf.writeUtf(p.schematicName)
                    buf.writeBlockPos(p.anchor)
                    buf.writeEnum(p.rotation)
                    buf.writeEnum(p.mirror)
                    buf.writeUtf(p.owner)
                },
                { buf ->
                    Construction(
                        buf.readUtf(),
                        buf.readBlockPos(),
                        buf.readEnum(Rotation::class.java),
                        buf.readEnum(Mirror::class.java),
                        buf.readUtf()
                    )
                }
            )
        }
    }

    /**
     * Pickup program: scans an area for loose [ItemEntity] objects and sends
     * bumble bees to collect them and deposit at the nearest logistics port.
     */
    data class Pickup(
        val corner1: BlockPos,
        val corner2: BlockPos,
    ) : SchematicProgram() {

        override fun generateBatches(level: Level, job: BeeJob): List<TaskBatch> {
            val bridge = SchematicCreateBridge(level)
            return bridge.generatePickupBatches(corner1, corner2, job).batches
        }

        override fun displayName(): String = "Item Pickup"

        override fun relocate(delta: BlockPos): Pickup {
            if (delta == BlockPos.ZERO) return this
            return copy(corner1 = corner1.offset(delta), corner2 = corner2.offset(delta))
        }

        companion object {
            val CODEC: MapCodec<Pickup> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("corner1").forGetter { it.corner1 },
                    BlockPos.CODEC.fieldOf("corner2").forGetter { it.corner2 }
                ).apply(instance, ::Pickup)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Pickup> = StreamCodec.of(
                { buf, p ->
                    buf.writeBlockPos(p.corner1)
                    buf.writeBlockPos(p.corner2)
                },
                { buf -> Pickup(buf.readBlockPos(), buf.readBlockPos()) }
            )
        }
    }

    /**
     * Deconstruction program: removes blocks between two corners.
     */
    data class Deconstruction(
        val corner1: BlockPos,
        val corner2: BlockPos
    ) : SchematicProgram() {

        override fun generateBatches(level: Level, job: BeeJob): List<TaskBatch> {
            val bridge = SchematicCreateBridge(level)
            return bridge.generateRemovalTasks(corner1, corner2, job)
        }

        override fun displayName(): String = "Deconstruction"

        override fun relocate(delta: BlockPos): Deconstruction {
            if (delta == BlockPos.ZERO) return this
            return copy(corner1 = corner1.offset(delta), corner2 = corner2.offset(delta))
        }

        companion object {
            val CODEC: MapCodec<Deconstruction> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    BlockPos.CODEC.fieldOf("corner1").forGetter { it.corner1 },
                    BlockPos.CODEC.fieldOf("corner2").forGetter { it.corner2 }
                ).apply(instance, ::Deconstruction)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Deconstruction> = StreamCodec.of(
                { buf, p ->
                    buf.writeBlockPos(p.corner1)
                    buf.writeBlockPos(p.corner2)
                },
                { buf ->
                    Deconstruction(buf.readBlockPos(), buf.readBlockPos())
                }
            )
        }
    }

    companion object {
        private const val TYPE_CONSTRUCTION = "construction"
        private const val TYPE_DECONSTRUCTION = "deconstruction"
        private const val TYPE_PICKUP = "pickup"

        val CODEC: Codec<SchematicProgram> = Codec.STRING.fieldOf("type").codec().dispatch(
            { program ->
                when (program) {
                    is Construction -> TYPE_CONSTRUCTION
                    is Deconstruction -> TYPE_DECONSTRUCTION
                    is Pickup -> TYPE_PICKUP
                }
            },
            { type ->
                when (type) {
                    TYPE_CONSTRUCTION -> Construction.CODEC
                    TYPE_DECONSTRUCTION -> Deconstruction.CODEC
                    TYPE_PICKUP -> Pickup.CODEC
                    else -> throw IllegalArgumentException("Unknown SchematicProgram type: $type")
                }
            }
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SchematicProgram> = object : StreamCodec<RegistryFriendlyByteBuf, SchematicProgram> {
            override fun decode(buf: RegistryFriendlyByteBuf): SchematicProgram {
                return when (buf.readUtf()) {
                    TYPE_CONSTRUCTION -> Construction.STREAM_CODEC.decode(buf)
                    TYPE_DECONSTRUCTION -> Deconstruction.STREAM_CODEC.decode(buf)
                    TYPE_PICKUP -> Pickup.STREAM_CODEC.decode(buf)
                    else -> throw IllegalArgumentException("Unknown SchematicProgram type")
                }
            }

            override fun encode(buf: RegistryFriendlyByteBuf, value: SchematicProgram) {
                when (value) {
                    is Construction -> {
                        buf.writeUtf(TYPE_CONSTRUCTION)
                        Construction.STREAM_CODEC.encode(buf, value)
                    }
                    is Deconstruction -> {
                        buf.writeUtf(TYPE_DECONSTRUCTION)
                        Deconstruction.STREAM_CODEC.encode(buf, value)
                    }
                    is Pickup -> {
                        buf.writeUtf(TYPE_PICKUP)
                        Pickup.STREAM_CODEC.encode(buf, value)
                    }
                }
            }
        }
    }
}
