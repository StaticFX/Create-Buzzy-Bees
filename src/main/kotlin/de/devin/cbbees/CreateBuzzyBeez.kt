package de.devin.cbbees

import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.foundation.item.ItemDescription
import com.simibubi.create.foundation.item.KineticStats
import com.simibubi.create.foundation.item.TooltipModifier
import de.devin.cbbees.blocks.AllBlocks
import de.devin.cbbees.config.CBBeesClientConfig
import de.devin.cbbees.config.CBBeesConfig
import de.devin.cbbees.content.backpack.client.CCRClientEvents
import de.devin.cbbees.content.backpack.client.BeeNetworkClientEvents
import de.devin.cbbees.content.bee.brain.BeeMemoryModules
import de.devin.cbbees.content.bee.brain.BeeSensors
import de.devin.cbbees.content.bee.client.BeeTargetLineHandler
import de.devin.cbbees.content.beehive.client.BeehiveRangeHandler
import de.devin.cbbees.content.domain.network.client.NetworkHighlightHandler
import de.devin.cbbees.content.logistics.transport.client.CargoPortLinkRenderer
import de.devin.cbbees.content.schematics.client.ConstructionPlannerClientEvents
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHUD
import de.devin.cbbees.content.schematics.client.ConstructionRenderer
import de.devin.cbbees.content.domain.events.PlayerTickEvent
import de.devin.cbbees.content.drone.client.DroneRangeRenderer
import de.devin.cbbees.content.drone.client.DroneViewClientEvents
import de.devin.cbbees.content.drone.client.DroneViewHUD
import de.devin.cbbees.content.schematics.client.DeconstructionClientEvents
import de.devin.cbbees.content.schematics.client.DeconstructionRenderer
import de.devin.cbbees.content.schematics.external.CreateModSchematicSource
import de.devin.cbbees.content.schematics.external.ExternalSchematicSource
import de.devin.cbbees.datagen.CCRDatagen
import de.devin.cbbees.items.AllItems
import de.devin.cbbees.network.AllPackets
import de.devin.cbbees.network.CCRServerEvents
import de.devin.cbbees.ponder.CBBPonderPlugin
import de.devin.cbbees.registry.AllBlockEntityTypes
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.registry.AllCBeesFanProcessingTypes
import de.devin.cbbees.registry.AllEffects
import de.devin.cbbees.registry.AllEntityTypes
import de.devin.cbbees.registry.AllKeys
import de.devin.cbbees.registry.AllMenuTypes
import de.devin.cbbees.tabs.AllCreativeModeTabs
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.createmod.catnip.lang.FontHelper
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(CreateBuzzyBeez.ID)
object CreateBuzzyBeez {
    const val ID = "cbbees"
    const val MOD_VERSION = "1.1.0"

    val REGISTRATE: CreateRegistrate = CreateRegistrate.create(ID)
        .setTooltipModifierFactory { item ->
            ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
        }

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.SERVER, CBBeesConfig.SPEC)
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.CLIENT, CBBeesClientConfig.SPEC)

        REGISTRATE.registerEventListeners(MOD_BUS)

        AllDataComponents.register(MOD_BUS)
        AllCreativeModeTabs.register()
        REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_MOD_TAB)
        AllBlocks.register()
        AllItems.register()
        AllEntityTypes.register()
        AllBlockEntityTypes.register()
        AllMenuTypes.register()
        AllEffects.register()
        de.devin.cbbees.registry.AllCBeesRecipeTypes.register(MOD_BUS)
        AllCBeesFanProcessingTypes.register(MOD_BUS)
        BeeMemoryModules.register()
        BeeSensors.register()

        MOD_BUS.addListener<RegisterPayloadHandlersEvent> {
            AllPackets.register(it)
        }

        MOD_BUS.addListener<net.neoforged.neoforge.event.RegisterGameTestsEvent> { event ->
            LOGGER.info("[cbbees] Registering game tests...")
            de.devin.cbbees.gametest.CBBeesGameTests.onRegisterGameTests(event)
        }

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient) {
            MOD_BUS.register(CCRClientEvents::class.java)
            NeoForge.EVENT_BUS.register(BeeNetworkClientEvents::class.java)
            NeoForge.EVENT_BUS.register(DeconstructionClientEvents::class.java)
            NeoForge.EVENT_BUS.register(ConstructionPlannerClientEvents::class.java)
            NeoForge.EVENT_BUS.register(BeeTargetLineHandler::class.java)
            NeoForge.EVENT_BUS.register(de.devin.cbbees.content.bee.client.BeeWorldRenderer::class.java)
            NeoForge.EVENT_BUS.register(BeehiveRangeHandler::class.java)
            NeoForge.EVENT_BUS.register(NetworkHighlightHandler::class.java)
            NeoForge.EVENT_BUS.register(ConstructionRenderer::class.java)
            NeoForge.EVENT_BUS.register(CargoPortLinkRenderer::class.java)
            NeoForge.EVENT_BUS.register(DroneViewClientEvents::class.java)
            NeoForge.EVENT_BUS.register(DroneRangeRenderer::class.java)
            NeoForge.EVENT_BUS.register(de.devin.cbbees.content.deployer.client.ProgrammedSchematicRenderer::class.java)
            NeoForge.EVENT_BUS.register(de.devin.cbbees.content.deployer.client.DeployerPreviewRenderer::class.java)
            MOD_BUS.addListener<FMLClientSetupEvent> { onClientSetup(it) }
            MOD_BUS.addListener<RegisterKeyMappingsEvent> { AllKeys.register(it) }
            MOD_BUS.addListener<RegisterGuiLayersEvent> { event ->
                event.registerAbove(
                    VanillaGuiLayers.CHAT,
                    asResource("construction_planner_hud")
                ) { guiGraphics, deltaTracker ->
                    ConstructionPlannerHUD.renderHUD(guiGraphics, deltaTracker)
                }
                event.registerAbove(
                    VanillaGuiLayers.CHAT,
                    asResource("deconstruction_planner_hud")
                ) { guiGraphics, deltaTracker ->
                    DeconstructionRenderer.renderHUD(guiGraphics, deltaTracker)
                }
                event.registerAbove(
                    VanillaGuiLayers.CHAT,
                    asResource("drone_view_hud")
                ) { guiGraphics, deltaTracker ->
                    DroneViewHUD.renderHUD(guiGraphics, deltaTracker)
                }
            }
        }

        MOD_BUS.addListener<GatherDataEvent> { CCRDatagen.gatherData(it) }
        MOD_BUS.addListener<FMLCommonSetupEvent> { onCommonSetup(it) }

        MOD_BUS.addListener<RegisterCapabilitiesEvent> { event ->
            de.devin.cbbees.compat.NeoForgeCapabilityCompat.register(event)
        }

        NeoForge.EVENT_BUS.addListener<RegisterCommandsEvent> {
            de.devin.cbbees.content.bee.debug.BeeDebugCommand.register(it.dispatcher)
        }

        // Register server-side event handlers on the NeoForge event bus
        NeoForge.EVENT_BUS.register(CCRServerEvents::class.java)
        NeoForge.EVENT_BUS.register(PlayerTickEvent())
    }

    fun asResource(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(ID, path)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")

        // Register Ponder plugin
        PonderIndex.addPlugin(CBBPonderPlugin())

        // Register external schematic source (visibility gated by server config in ConstructionPlannerScreen)
        ExternalSchematicSource.active = CreateModSchematicSource(
            hmacSecret = "5a0841453e5c2588583da1fb215f4af88a5a7d4ee86a720aea4ae27c4065dace"
        )
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Common setup...")
    }
}
