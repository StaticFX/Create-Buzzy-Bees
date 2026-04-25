package de.devin.cbbees.registry

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingTypeRegistry
import com.simibubi.create.foundation.recipe.RecipeApplier
import de.devin.cbbees.CreateBuzzyBeez
import net.createmod.catnip.theme.Color
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object AllCBeesFanProcessingTypes {

    val GLUEING_CATALYST_BLOCK: TagKey<net.minecraft.world.level.block.Block> =
        TagKey.create(Registries.BLOCK, CreateBuzzyBeez.asResource("fan_processing_catalysts/glueing"))

    lateinit var GLUEING: GlueingType
        private set

    private var injected = false

    fun register(modEventBus: net.minecraftforge.eventbus.api.IEventBus) {
        GLUEING = GlueingType()
        // Inject on every server start — SORTED_TYPES may be cleared between world loads
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.event.server.ServerAboutToStartEvent> {
            injectIntoSortedTypes()
        }
        // Also inject after all mods finish loading (for client-side checks like JEI)
        modEventBus.addListener<net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent> {
            injectIntoSortedTypes()
        }
    }

    private fun injectIntoSortedTypes() {
        try {
            val sortedField = FanProcessingTypeRegistry::class.java.getDeclaredField("SORTED_TYPES")
            sortedField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val sortedList = sortedField.get(null) as MutableList<FanProcessingType>
            if (GLUEING !in sortedList) {
                sortedList.clear()
                com.simibubi.create.api.registry.CreateBuiltInRegistries.FAN_PROCESSING_TYPE.forEach { sortedList.add(it) }
                if (GLUEING !in sortedList) sortedList.add(GLUEING)
                sortedList.sortBy { -it.priority }
                CreateBuzzyBeez.LOGGER.info("[GLUEING] Injected ${sortedList.size} fan processing types")
            }
        } catch (e: Exception) {
            CreateBuzzyBeez.LOGGER.error("[GLUEING] Failed", e)
        }
    }

    class GlueingType : FanProcessingType {
        override fun isValidAt(level: Level, pos: BlockPos): Boolean {
            val state = level.getBlockState(pos)
            val isHoney = state.block == net.minecraft.world.level.block.Blocks.HONEY_BLOCK
            val isTag = state.`is`(GLUEING_CATALYST_BLOCK)
            if (isHoney) CreateBuzzyBeez.LOGGER.info("[GLUEING] isValidAt: isHoney=$isHoney, isTag=$isTag, block=${state.block}")
            return isTag
        }

        override fun getPriority(): Int = 500

        override fun canProcess(stack: ItemStack, level: Level): Boolean {
            val result = AllCBeesRecipeTypes.GLUEING.find(
                SimpleContainer(stack), level
            ).isPresent
            CreateBuzzyBeez.LOGGER.info("[GLUEING] canProcess ${stack.item} = $result")
            return result
        }

        override fun process(stack: ItemStack, level: Level): List<ItemStack>? {
            return AllCBeesRecipeTypes.GLUEING.find(
                SimpleContainer(stack), level
            )
                .map { RecipeApplier.applyRecipeOn(level, stack, it, true) }
                .orElse(null)
        }

        override fun spawnProcessingParticles(level: Level, pos: Vec3) {
            if (level.random.nextInt(8) != 0) return
            level.addParticle(
                ParticleTypes.DRIPPING_HONEY,
                pos.x + (level.random.nextFloat() - 0.5f) * 0.5f,
                pos.y + 0.5f,
                pos.z + (level.random.nextFloat() - 0.5f) * 0.5f,
                0.0, 1.0 / 16.0, 0.0
            )
            if (level.random.nextInt(3) == 0) {
                level.addParticle(
                    ParticleTypes.FALLING_HONEY,
                    pos.x + (level.random.nextFloat() - 0.5f) * 0.5f,
                    pos.y + 0.5f,
                    pos.z + (level.random.nextFloat() - 0.5f) * 0.5f,
                    0.0, 1.0 / 16.0, 0.0
                )
            }
        }

        override fun morphAirFlow(particleAccess: FanProcessingType.AirFlowParticleAccess, random: RandomSource) {
            particleAccess.setColor(Color.mixColors(0xEB8844, 0xFFC233, random.nextFloat()))
            particleAccess.setAlpha(0.7f)
            if (random.nextFloat() < 1f / 32f)
                particleAccess.spawnExtraParticle(ParticleTypes.DRIPPING_HONEY, 0.125f)
            if (random.nextFloat() < 1f / 16f)
                particleAccess.spawnExtraParticle(ParticleTypes.FALLING_HONEY, 0.125f)
        }

        override fun affectEntity(entity: Entity, level: Level) {
            val vec3 = entity.deltaMovement
            if (vec3.y < -0.08) {
                val d0 = -0.05 / vec3.y
                entity.deltaMovement = Vec3(vec3.x * d0, -0.05, vec3.z * d0)
            } else {
                entity.deltaMovement = Vec3(vec3.x, -0.05, vec3.z)
            }
            entity.resetFallDistance()

            if (entity is LivingEntity || entity is net.minecraft.world.entity.vehicle.AbstractMinecart
                || entity is net.minecraft.world.entity.item.PrimedTnt || entity is net.minecraft.world.entity.vehicle.Boat
            ) {
                if (level.random.nextInt(5) == 0) {
                    entity.playSound(net.minecraft.sounds.SoundEvents.HONEY_BLOCK_SLIDE, 1.0f, 1.0f)
                }
                if (!level.isClientSide && level.random.nextInt(5) == 0) {
                    level.broadcastEntityEvent(entity, 53.toByte())
                }
            }
        }
    }
}
