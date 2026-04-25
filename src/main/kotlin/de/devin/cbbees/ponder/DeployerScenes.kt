package de.devin.cbbees.ponder

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import de.devin.cbbees.content.deployer.SchematicDeployerBlock
import de.devin.cbbees.items.AllItems
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

object DeployerScenes {

    /**
     * Chapter 1: What is the Schematic Deployer?
     */
    fun schematicDeployerIntro(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("schematic_deployer", "The Schematic Deployer")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val deployerPos = util.grid().at(2, 1, 2)
        val leverPos = util.grid().at(3, 1, 2)
        val hivePos = util.grid().at(4, 1, 2)
        val shaftPos = util.grid().at(4, 1, 3)

        // Introduce the block
        scene.world().showSection(util.select().position(deployerPos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("The Schematic Deployer executes programmed jobs automatically using a redstone signal.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(100)

        // How to obtain programmed schematics
        scene.overlay().showText(80)
            .text("It requires a Programmed Schematic to operate. These can be created using any Planner tool.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(50)

        // Show the programmed schematic item
        scene.overlay().showControls(util.vector().blockSurface(deployerPos, Direction.UP), Pointing.DOWN, 60)
            .withItem(ItemStack(AllItems.PROGRAMMED_SCHEMATIC.get()))

        scene.idle(80)

        scene.overlay().showText(80)
            .text("Use the Program key while holding a Planner with a selection to create a Programmed Schematic.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(100)

        // Insert the schematic
        scene.overlay().showControls(util.vector().blockSurface(deployerPos, Direction.UP), Pointing.DOWN, 60)
            .withItem(ItemStack(AllItems.PROGRAMMED_SCHEMATIC.get()))
            .rightClick()

        scene.overlay().showText(80)
            .text("Right-click the Deployer with a Programmed Schematic to load it.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(100)

        // Redstone activation
        scene.world().showSection(util.select().position(leverPos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("Apply a redstone signal to deploy the job. The Deployer activates on a rising edge.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(leverPos, Direction.WEST))

        scene.idle(60)

        scene.world().modifyBlock(deployerPos, { state ->
            state.setValue(SchematicDeployerBlock.POWERED, true) as BlockState
        }, false)

        scene.idle(20)

        // Beehive picks up the job
        scene.world().showSection(util.select().position(hivePos), Direction.DOWN)
        scene.world().showSection(util.select().position(shaftPos), Direction.DOWN)
        scene.world().setKineticSpeed(util.select().position(hivePos), 64f)
        scene.world().setKineticSpeed(util.select().position(shaftPos), 64f)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("Nearby Mechanical Beehives will pick up the job and dispatch bees to complete it.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(hivePos, Direction.WEST))

        scene.idle(100)

        // Comparator output
        scene.overlay().showText(80)
            .text("A comparator can read the Deployer's state: empty (0), loaded (1), active (8), or just deployed (15).")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.SOUTH))

        scene.idle(100)

        // Extraction
        scene.overlay().showText(80)
            .text("Shift+right-click to extract the schematic. Hoppers and pipes can also insert and extract for full automation.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(100)

        scene.markAsFinished()
    }

    /**
     * Chapter 2: Deploy Modes — Absolute vs Relative.
     */
    fun deployModes(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("schematic_deployer_automation", "Deploy Modes")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val absolutePos = util.grid().at(1, 1, 2)
        val relativePos = util.grid().at(3, 1, 2)

        scene.world().showSection(util.select().position(absolutePos), Direction.DOWN)
        scene.world().showSection(util.select().position(relativePos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("The Schematic Deployer has two deploy modes. Open the GUI by right-clicking without an item.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(absolutePos, Direction.UP))

        scene.idle(100)

        // Absolute mode
        scene.overlay().showOutlineWithText(util.select().position(absolutePos), 100)
            .text("In Absolute mode, the schematic is always built at the exact coordinates stored when it was programmed.")
            .placeNearTarget()
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector().blockSurface(absolutePos, Direction.WEST))

        scene.idle(120)

        val absoluteTarget = AABB(-1.0, 1.0, 0.0, 1.0, 3.0, 2.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "abs", absoluteTarget, 100)

        scene.overlay().showText(80)
            .text("No matter where the Deployer is placed, the build happens at the same world position.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(absolutePos, Direction.WEST))

        scene.idle(100)

        // Relative mode
        scene.overlay().showOutlineWithText(util.select().position(relativePos), 100)
            .text("In Relative mode, the build target is calculated as an offset from the Deployer's position.")
            .placeNearTarget()
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector().blockSurface(relativePos, Direction.EAST))

        scene.idle(120)

        val relativeTarget = AABB(3.0, 1.0, 3.0, 5.0, 3.0, 5.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "rel", relativeTarget, 100)

        scene.overlay().showText(80)
            .text("Move the Deployer and the build follows. Great for repeatable, portable setups.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(relativePos, Direction.EAST))

        scene.idle(100)

        scene.overlay().showText(80)
            .text("Relative mode also lets you override rotation and mirror from the GUI, without reprogramming.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(relativePos, Direction.UP))

        scene.idle(100)

        scene.markAsFinished()
    }

    /**
     * Chapter 3: Self-Replicating Schematics.
     *
     * Reuses the intro NBT structure since no dedicated structure exists yet.
     */
    fun selfReplicating(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("schematic_deployer_self_replicating", "Self-Replicating Schematics")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val deployerPos = util.grid().at(2, 1, 2)

        scene.world().showSection(util.select().position(deployerPos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showControls(util.vector().blockSurface(deployerPos, Direction.UP), Pointing.DOWN, 60)
            .withItem(ItemStack(AllItems.PROGRAMMED_SCHEMATIC.get()))

        scene.overlay().showText(80)
            .text("When a schematic contains a Schematic Deployer block, something special happens.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(100)

        scene.overlay().showText(100)
            .text("The parent Deployer's held item and settings are automatically injected into the newly built child Deployer.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(120)

        scene.overlay().showText(100)
            .text("The child Deployer will already contain the same Programmed Schematic and deploy mode — ready to trigger.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.EAST))

        scene.idle(120)

        scene.overlay().showText(100)
            .text("This creates a chain reaction: each Deployer builds the next, carrying the same program forward. Perfect for expanding bases automatically.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(120)

        scene.overlay().showText(80)
            .text("Use Relative mode so each child builds at its own position rather than the original coordinates.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(100)

        scene.markAsFinished()
    }
}
