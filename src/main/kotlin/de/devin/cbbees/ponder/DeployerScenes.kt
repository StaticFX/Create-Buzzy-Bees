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
     * Ponder scene: Schematic Deployer introduction.
     *
     * NBT structure required at `ponder/schematic_deployer/intro.nbt`:
     * - 5x5x5 base plate
     * - (2,1,2): Schematic Deployer (facing north)
     * - (3,1,2): Lever (attached to side of deployer)
     * - (4,1,2): Mechanical Beehive
     * - (4,1,3): Shaft (providing rotation)
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

        // Show the deployer
        scene.world().showSection(util.select().position(deployerPos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("The Schematic Deployer automates construction and deconstruction jobs using programmed schematics.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(100)

        // Show inserting a programmed schematic
        val programmedSchematic = ItemStack(AllItems.PROGRAMMED_SCHEMATIC.get())
        scene.overlay().showControls(util.vector().blockSurface(deployerPos, Direction.UP), Pointing.DOWN, 60)
            .withItem(programmedSchematic)
            .rightClick()

        scene.overlay().showText(80)
            .text("Right-click with a Programmed Schematic to insert it. Create one using the Program tool in the Construction or Deconstruction Planner.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.WEST))

        scene.idle(100)

        // Show the lever
        scene.world().showSection(util.select().position(leverPos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("Apply a redstone signal to deploy the programmed job to nearby beehives.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(leverPos, Direction.WEST))

        scene.idle(60)

        // Activate — show powered state
        scene.world().modifyBlock(deployerPos, { state ->
            state.setValue(SchematicDeployerBlock.POWERED, true) as BlockState
        }, false)

        scene.idle(20)

        // Show beehive + shaft
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
            .text("A comparator reads the deployer's state: empty (0), loaded (1), job active (8), or just deployed (15).")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.SOUTH))

        scene.idle(100)

        // Automation
        scene.overlay().showText(80)
            .text("Shift+right-click to extract the schematic. Hoppers and pipes can also insert and extract for full automation.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(deployerPos, Direction.UP))

        scene.idle(100)

        scene.markAsFinished()
    }

    /**
     * Ponder scene: Deploy modes — Absolute vs Relative.
     *
     * NBT structure required at `ponder/schematic_deployer/automation.nbt`:
     * - 5x3x5 base plate
     * - (1,1,2): Schematic Deployer (facing north) — "absolute" example
     * - (3,1,2): Schematic Deployer (facing north) — "relative" example
     */
    fun selfPopulatingBases(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)

        scene.title("schematic_deployer_automation", "Deploy Modes")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        val absolutePos = util.grid().at(1, 1, 2)
        val relativePos = util.grid().at(3, 1, 2)

        // Show both deployers
        scene.world().showSection(util.select().position(absolutePos), Direction.DOWN)
        scene.world().showSection(util.select().position(relativePos), Direction.DOWN)
        scene.idle(10)

        scene.overlay().showText(80)
            .text("The Schematic Deployer supports two deploy modes: Absolute and Relative. Right-click to open the settings GUI.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(absolutePos, Direction.UP))

        scene.idle(100)

        // Absolute mode
        scene.overlay().showOutlineWithText(util.select().position(absolutePos), 100)
            .text("In Absolute mode, the schematic is built at the exact coordinates stored when it was programmed.")
            .placeNearTarget()
            .attachKeyFrame()
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector().blockSurface(absolutePos, Direction.WEST))

        scene.idle(120)

        val absoluteTarget = AABB(-1.0, 1.0, 0.0, 1.0, 3.0, 2.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "abs", absoluteTarget, 100)

        scene.overlay().showText(80)
            .text("The build always happens at the same world position, no matter where the deployer is placed.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(absolutePos, Direction.WEST))

        scene.idle(100)

        // Relative mode
        scene.overlay().showOutlineWithText(util.select().position(relativePos), 100)
            .text("In Relative mode, the build target is calculated as an offset from the deployer's position.")
            .placeNearTarget()
            .attachKeyFrame()
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector().blockSurface(relativePos, Direction.EAST))

        scene.idle(120)

        val relativeTarget = AABB(3.0, 1.0, 3.0, 5.0, 3.0, 5.0)
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "rel", relativeTarget, 100)

        scene.overlay().showText(80)
            .text("Move the deployer and the build follows — great for repeatable, portable setups.")
            .placeNearTarget()
            .pointAt(util.vector().blockSurface(relativePos, Direction.EAST))

        scene.idle(100)

        // Rotation/Mirror
        scene.overlay().showText(80)
            .text("Relative mode also lets you override rotation and mirror settings from the GUI, without reprogramming the schematic.")
            .placeNearTarget()
            .attachKeyFrame()
            .pointAt(util.vector().blockSurface(relativePos, Direction.UP))

        scene.idle(100)

        scene.markAsFinished()
    }
}
