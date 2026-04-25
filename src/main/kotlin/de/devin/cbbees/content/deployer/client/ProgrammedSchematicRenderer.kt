package de.devin.cbbees.content.deployer.client

import com.simibubi.create.AllSpecialTextures
import com.simibubi.create.content.schematics.SchematicItem
import de.devin.cbbees.content.deployer.SchematicProgram
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.phys.AABB
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent

/**
 * Renders a translucent bounding box outline in the world when the player
 * is holding a Programmed Schematic, so they can see exactly where the
 * construction or deconstruction will happen.
 */
@ClientSide
@OnlyIn(Dist.CLIENT)
object ProgrammedSchematicRenderer {

    private val outlineSlot = Any()
    private const val CONSTRUCTION_COLOR = 0x6886c5 // blue
    private const val DECONSTRUCTION_COLOR = 0xc56868 // red
    private const val PICKUP_COLOR = 0x68c588 // green

    /** Cached AABB so we don't recompute every frame. */
    private var cachedBounds: AABB? = null
    private var cachedColor: Int = CONSTRUCTION_COLOR
    private var cachedProgram: SchematicProgram? = null
    private var wasActive = false

    @SubscribeEvent
    @JvmStatic
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        val heldProgram = findHeldProgram(player.mainHandItem)
            ?: findHeldProgram(player.offhandItem)

        if (heldProgram == null) {
            if (wasActive) {
                cachedBounds = null
                cachedProgram = null
                wasActive = false
            }
            return
        }

        // Recompute bounds only when program changes
        if (heldProgram != cachedProgram) {
            cachedProgram = heldProgram
            cachedBounds = computeBounds(heldProgram, mc)
            cachedColor = when (heldProgram) {
                is SchematicProgram.Construction -> CONSTRUCTION_COLOR
                is SchematicProgram.Deconstruction -> DECONSTRUCTION_COLOR
                is SchematicProgram.Pickup -> PICKUP_COLOR
            }
        }

        val bounds = cachedBounds ?: return
        wasActive = true

        Outliner.getInstance()
            .chaseAABB(outlineSlot, bounds)
            .colored(cachedColor)
            .withFaceTextures(AllSpecialTextures.CHECKERED, AllSpecialTextures.HIGHLIGHT_CHECKERED)
            .lineWidth(1 / 16f)
    }

    private fun findHeldProgram(stack: ItemStack): SchematicProgram? {
        if (!AllItems.PROGRAMMED_SCHEMATIC.isIn(stack)) return null
        return stack.get(AllDataComponents.SCHEMATIC_PROGRAM)
    }

    private fun computeBounds(program: SchematicProgram, mc: Minecraft): AABB? {
        return when (program) {
            is SchematicProgram.Deconstruction -> {
                val c1 = program.corner1
                val c2 = program.corner2
                AABB(
                    minOf(c1.x, c2.x).toDouble(),
                    minOf(c1.y, c2.y).toDouble(),
                    minOf(c1.z, c2.z).toDouble(),
                    (maxOf(c1.x, c2.x) + 1).toDouble(),
                    (maxOf(c1.y, c2.y) + 1).toDouble(),
                    (maxOf(c1.z, c2.z) + 1).toDouble()
                )
            }
            is SchematicProgram.Construction -> {
                computeConstructionBounds(program, mc)
            }
            is SchematicProgram.Pickup -> {
                val c1 = program.corner1
                val c2 = program.corner2
                AABB(
                    minOf(c1.x, c2.x).toDouble(),
                    minOf(c1.y, c2.y).toDouble(),
                    minOf(c1.z, c2.z).toDouble(),
                    (maxOf(c1.x, c2.x) + 1).toDouble(),
                    (maxOf(c1.y, c2.y) + 1).toDouble(),
                    (maxOf(c1.z, c2.z) + 1).toDouble()
                )
            }
        }
    }

    /**
     * Computes the world-space AABB for a construction program by loading
     * the schematic template and applying rotation/mirror.
     */
    private fun computeConstructionBounds(program: SchematicProgram.Construction, mc: Minecraft): AABB? {
        val level = mc.level ?: return null
        val player = mc.player ?: return null

        try {
            // Build a temporary schematic stack to load the template
            val fakeStack = com.simibubi.create.AllItems.SCHEMATIC.asStack()
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_FILE, program.schematicName)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_OWNER, program.owner)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ANCHOR, program.anchor)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_ROTATION, program.rotation)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_MIRROR, program.mirror)
            fakeStack.set(com.simibubi.create.AllDataComponents.SCHEMATIC_DEPLOYED, true)

            val template = SchematicItem.loadSchematic(level, fakeStack)
            val rawSize = template.size
            if (rawSize.x == 0 && rawSize.y == 0 && rawSize.z == 0) return null

            // Apply rotation to the size to get the effective dimensions
            val effectiveSize = transformSize(rawSize.x, rawSize.y, rawSize.z, program.rotation)
            val anchor = program.anchor

            // Compute world AABB from anchor + effective size
            // Anchor is the min corner after Create's transform
            val minX = anchor.x.toDouble()
            val minY = anchor.y.toDouble()
            val minZ = anchor.z.toDouble()
            return AABB(
                minX, minY, minZ,
                minX + effectiveSize.x, minY + effectiveSize.y, minZ + effectiveSize.z
            )
        } catch (_: Exception) {
            // Schematic file not found on client — fall back to anchor point only
            return null
        }
    }

    /**
     * Transforms a schematic's size vector by rotation.
     * 90/270 rotations swap X and Z dimensions.
     */
    private fun transformSize(sizeX: Int, sizeY: Int, sizeZ: Int, rotation: Rotation): BlockPos {
        return when (rotation) {
            Rotation.NONE, Rotation.CLOCKWISE_180 -> BlockPos(sizeX, sizeY, sizeZ)
            Rotation.CLOCKWISE_90, Rotation.COUNTERCLOCKWISE_90 -> BlockPos(sizeZ, sizeY, sizeX)
        }
    }
}
