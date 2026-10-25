package de.devin.cbbees.ponder

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags
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
            .addStoryBoard("mechanical_beehive/intro", { scene, util -> BeehiveScenes.intro(scene, util) })
            .addStoryBoard("mechanical_beehive/speed", { scene, util -> BeehiveScenes.speed(scene, util) })
            .addStoryBoard("mechanical_beehive/networking", { scene, util -> BeehiveScenes.networking(scene, util) })

        helper.forComponents(AllBlocks.LOGISTICS_PORT.id)
            .addStoryBoard("logistics_port/intro", { scene, util -> BeeScenes.introLogisticsPort(scene, util) })
            .addStoryBoard("logistics_port/with_bees", { scene, util -> BeeScenes.logisticsPortWithBees(scene, util) })


        helper.forComponents(AllBlocks.CARGO_PORT.id)
            .addStoryBoard("cargo_port/scene", { scene, util -> BeeScenes.cargoPortIntro(scene, util) })
            .addStoryBoard("cargo_port/scene2", { scene, util -> BeeScenes.cargoPortWithBees(scene, util) })


        helper.forComponents(AllBlocks.DOCK_PORT.id)
            .addStoryBoard("dock_port/intro", { scene, util -> PortScenes.dockPort(scene, util) })

        helper.forComponents(AllBlocks.WAGGLE_PORT.id)
            .addStoryBoard("waggle_port/intro", { scene, util -> PortScenes.wagglePort(scene, util) })

        val beeStoryBoard = { scene: SceneBuilder, util: SceneBuildingUtil -> BeeScenes.mechanicalBee(scene, util) }

        helper.forComponents(AllItems.MECHANICAL_BEE.id).addStoryBoard("base", beeStoryBoard)
        helper.forComponents(AllItems.MECHANICAL_BUMBLE_BEE.id).addStoryBoard("base", beeStoryBoard)

        helper.forComponents(AllBlocks.SCHEMATIC_DEPLOYER.id)
            .addStoryBoard("schematic_deployer/intro", { scene, util -> DeployerScenes.schematicDeployerIntro(scene, util) })
            .addStoryBoard("schematic_deployer/automation", { scene, util -> DeployerScenes.deployModes(scene, util) })
            .addStoryBoard("schematic_deployer/intro", { scene, util -> DeployerScenes.selfReplicating(scene, util) })

        helper.forComponents(AllItems.PROGRAMMED_SCHEMATIC.id)
            .addStoryBoard("schematic_deployer/intro", { scene, util -> DeployerScenes.schematicDeployerIntro(scene, util) })

    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag(BEES)
            .title("Mechanical Bees")
            .description("Hives and the bees that build, deconstruct and transport for you")
            .item(AllItems.MECHANICAL_BEE.get())
            .addToIndex()
            .register()

        helper.registerTag(BEE_LOGISTICS)
            .title("Bee Logistics")
            .description("Ports that connect bees to inventories, tanks and Create's logistics network")
            .item(AllBlocks.LOGISTICS_PORT.get().asItem())
            .addToIndex()
            .register()

        helper.registerTag(BEE_AUTOMATION)
            .title("Bee Automation")
            .description("Repeat bee jobs automatically with programmed schematics")
            .item(AllBlocks.SCHEMATIC_DEPLOYER.get().asItem())
            .addToIndex()
            .register()

        helper.addToTag(BEES)
            .add(AllBlocks.MECHANICAL_BEEHIVE.id)
            .add(AllItems.MECHANICAL_BEE.id)
            .add(AllItems.MECHANICAL_BUMBLE_BEE.id)

        helper.addToTag(BEE_LOGISTICS)
            .add(AllBlocks.LOGISTICS_PORT.id)
            .add(AllBlocks.CARGO_PORT.id)
            .add(AllBlocks.DOCK_PORT.id)
            .add(AllBlocks.WAGGLE_PORT.id)

        helper.addToTag(BEE_AUTOMATION)
            .add(AllBlocks.SCHEMATIC_DEPLOYER.id)
            .add(AllItems.PROGRAMMED_SCHEMATIC.id)

        helper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES).add(AllBlocks.MECHANICAL_BEEHIVE.id)
        helper.addToTag(AllCreatePonderTags.LOGISTICS).add(AllBlocks.LOGISTICS_PORT.id).add(AllBlocks.CARGO_PORT.id)
        helper.addToTag(AllCreatePonderTags.FLUIDS).add(AllBlocks.DOCK_PORT.id)
        helper.addToTag(AllCreatePonderTags.HIGH_LOGISTICS).add(AllBlocks.WAGGLE_PORT.id)
    }

    private companion object {
        val BEES: ResourceLocation = CreateBuzzyBeez.asResource("bees")
        val BEE_LOGISTICS: ResourceLocation = CreateBuzzyBeez.asResource("bee_logistics")
        val BEE_AUTOMATION: ResourceLocation = CreateBuzzyBeez.asResource("bee_automation")
    }
}
