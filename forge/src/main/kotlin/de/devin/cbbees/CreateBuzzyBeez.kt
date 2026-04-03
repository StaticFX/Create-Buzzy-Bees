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
import de.devin.cbbees.content.domain.events.PlayerTickEvent
import de.devin.cbbees.content.domain.network.client.NetworkHighlightHandler
import de.devin.cbbees.content.logistics.transport.client.CargoPortLinkRenderer
import de.devin.cbbees.content.schematics.client.ConstructionPlannerClientEvents
import de.devin.cbbees.content.schematics.client.ConstructionPlannerHUD
import de.devin.cbbees.content.schematics.client.ConstructionRenderer
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
import de.devin.cbbees.registry.AllCBeesFanProcessingTypes
import de.devin.cbbees.registry.AllDataComponents
import de.devin.cbbees.registry.AllEffects
import de.devin.cbbees.registry.AllEntityTypes
import de.devin.cbbees.registry.AllKeys
import de.devin.cbbees.registry.AllMenuTypes
import de.devin.cbbees.tabs.AllCreativeModeTabs
import net.createmod.catnip.lang.FontHelper
import net.createmod.ponder.foundation.PonderIndex
import de.devin.cbbees.compat.DeltaTrackerShim
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/**
 * Forge 1.20.1 entry point.
 */
@Mod(CreateBuzzyBeez.ID)
object CreateBuzzyBeez {
    const val ID = "cbbees"
    const val MOD_VERSION = "1.2.2"

    val REGISTRATE: CreateRegistrate = de.devin.cbbees.compat.KFFCreateRegistrate.create(ID)
        .setTooltipModifierFactory { item ->
            ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
        }

    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CBBeesConfig.SPEC)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CBBeesClientConfig.SPEC)

        REGISTRATE.registerEventListeners(MOD_BUS)

        AllDataComponents.register(MOD_BUS)
        AllCreativeModeTabs.register()
        REGISTRATE.setCreativeTab(net.minecraftforge.registries.RegistryObject.create(
            AllCreativeModeTabs.BASE_MOD_TAB.getId(),
            net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
            CreateBuzzyBeez.ID
        ))
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

        // Forge: register packets directly (no RegisterPayloadHandlersEvent)
        AllPackets.register()

        if (FMLEnvironment.dist.isClient) {
            // Register event handlers — @SubscribeEvent scanning handles InputEvent, RenderLevelStageEvent, etc.
            // ClientTickEvent.Post handlers won't fire (shim type), so we register manual tick listeners below.
            MOD_BUS.register(CCRClientEvents::class.java)
            MinecraftForge.EVENT_BUS.register(BeeNetworkClientEvents::class.java)
            MinecraftForge.EVENT_BUS.register(DeconstructionClientEvents::class.java)
            MinecraftForge.EVENT_BUS.register(ConstructionPlannerClientEvents::class.java)
            MinecraftForge.EVENT_BUS.register(BeeTargetLineHandler::class.java)
            MinecraftForge.EVENT_BUS.register(BeehiveRangeHandler::class.java)
            MinecraftForge.EVENT_BUS.register(NetworkHighlightHandler::class.java)
            MinecraftForge.EVENT_BUS.register(ConstructionRenderer::class.java)
            MinecraftForge.EVENT_BUS.register(CargoPortLinkRenderer::class.java)

            // Manual client tick listener: calls tick() directly since the shim ClientTickEvent.Post
            // type won't receive dispatched TickEvent.ClientTickEvent events
            MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.event.TickEvent.ClientTickEvent> { event ->
                if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                    BeeTargetLineHandler.tick()
                    BeehiveRangeHandler.tick()
                    NetworkHighlightHandler.tick()
                    CargoPortLinkRenderer.tick()
                    ConstructionPlannerClientEvents.tick()
                    DeconstructionClientEvents.tick()
                }
            }

            MOD_BUS.addListener<FMLClientSetupEvent> { onClientSetup(it) }
            MOD_BUS.addListener<RegisterKeyMappingsEvent> { AllKeys.register(it) }

            // Forge 1.20.1: register GUI overlays via RegisterGuiOverlaysEvent
            MOD_BUS.addListener<RegisterGuiOverlaysEvent> { event ->
                LOGGER.info("Registering GUI overlays: construction_planner_hud, deconstruction_planner_hud")
                event.registerAbove(
                    VanillaGuiOverlay.CHAT_PANEL.id(),
                    "construction_planner_hud"
                ) { _, guiGraphics, partialTick, _, _ ->
                    ConstructionPlannerHUD.renderHUD(guiGraphics, DeltaTrackerShim(partialTick))
                }
                event.registerAbove(
                    VanillaGuiOverlay.CHAT_PANEL.id(),
                    "deconstruction_planner_hud"
                ) { _, guiGraphics, partialTick, _, _ ->
                    DeconstructionRenderer.renderHUD(guiGraphics, DeltaTrackerShim(partialTick))
                }
            }
        }

        MOD_BUS.addListener<GatherDataEvent> { CCRDatagen.gatherData(it) }
        MOD_BUS.addListener<FMLCommonSetupEvent> { onCommonSetup(it) }

        // Forge 1.20.1: capabilities are attached via block entity getCapability() overrides,
        // not via RegisterCapabilitiesEvent

        MinecraftForge.EVENT_BUS.addListener<RegisterCommandsEvent> {
            de.devin.cbbees.content.bee.debug.BeeDebugCommand.register(it.dispatcher)
        }

        MinecraftForge.EVENT_BUS.register(CCRServerEvents::class.java)
        MinecraftForge.EVENT_BUS.register(PlayerTickEvent())

        // Manual player tick listener (same shim pattern as client ticks)
        val playerTickHandler = PlayerTickEvent()
        MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.event.TickEvent.PlayerTickEvent> { event ->
            if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                playerTickHandler.tick(event.player)
            }
        }
    }

    fun asResource(path: String): ResourceLocation {
        return ResourceLocation(ID, path)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
        PonderIndex.addPlugin(CBBPonderPlugin())
        ExternalSchematicSource.active = CreateModSchematicSource(
            hmacSecret = "5a0841453e5c2588583da1fb215f4af88a5a7d4ee86a720aea4ae27c4065dace"
        )
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Common setup...")
    }
}
