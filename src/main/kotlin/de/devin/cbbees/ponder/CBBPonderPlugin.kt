package de.devin.cbbees.ponder

import de.devin.cbbees.CreateBuzzyBeez
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.items.AllItems
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.createmod.ponder.api.scene.PonderStoryBoard
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.resources.ResourceLocation

class CBBPonderPlugin : PonderPlugin {
    override fun getModId(): String = CreateBuzzyBeez.ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        helper.forComponents(AllBlocks.MECHANICAL_BEEHIVE.id)
            .addStoryBoard("mechanical_beehive/intro", { scene, util -> BeeScenes.mechanicalBeehive(scene, util) })
            .addStoryBoard(
                "mechanical_beehive/networking",
                { scene, util -> BeeScenes.mechanicalBeehiveNetworks(scene, util) })

        helper.forComponents(AllBlocks.LOGISTICS_PORT.id)
            .addStoryBoard("logistics_port/intro", { scene, util -> BeeScenes.introLogisticsPort(scene, util) })
            .addStoryBoard("logistics_port/with_bees", { scene, util -> BeeScenes.logisticsPortWithBees(scene, util) })


        helper.forComponents(AllBlocks.CARGO_PORT.id)
            .addStoryBoard("cargo_port/scene", { scene, util -> BeeScenes.cargoPortIntro(scene, util) })
            .addStoryBoard("cargo_port/scene2", { scene, util -> BeeScenes.cargoPortWithBees(scene, util) })


        val beeStoryBoard = { scene: SceneBuilder, util: SceneBuildingUtil -> BeeScenes.mechanicalBee(scene, util) }

        helper.forComponents(AllItems.MECHANICAL_BEE.id).addStoryBoard("base", beeStoryBoard)
        helper.forComponents(AllItems.MECHANICAL_BUMBLE_BEE.id).addStoryBoard("base", beeStoryBoard)

        helper.forComponents(AllBlocks.SCHEMATIC_DEPLOYER.id)
            .addStoryBoard("schematic_deployer/intro", { scene, util -> DeployerScenes.schematicDeployerIntro(scene, util) })
            .addStoryBoard("schematic_deployer/automation", { scene, util -> DeployerScenes.selfPopulatingBases(scene, util) })

        helper.forComponents(AllItems.PROGRAMMED_SCHEMATIC.id)
            .addStoryBoard("schematic_deployer/intro", { scene, util -> DeployerScenes.schematicDeployerIntro(scene, util) })

    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag("buzzy_bees")
            .title("Buzzy Bees")
            .description("Automated construction and deconstruction with mechanical bees")
            .item(AllBlocks.MECHANICAL_BEEHIVE.get().asItem())
            .addToIndex()
            .register()
    }
}
