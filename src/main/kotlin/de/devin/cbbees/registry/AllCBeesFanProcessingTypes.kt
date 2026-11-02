package de.devin.cbbees.registry

import com.simibubi.create.api.registry.CreateRegistries
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType
import com.simibubi.create.foundation.recipe.RecipeApplier
import de.devin.cbbees.CreateBuzzyBeez
import net.createmod.catnip.theme.Color
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.tags.TagKey
import net.minecraft.core.registries.Registries
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object AllCBeesFanProcessingTypes {

    val GLUEING_CATALYST_BLOCK: TagKey<net.minecraft.world.level.block.Block> =
        TagKey.create(Registries.BLOCK, CreateBuzzyBeez.asResource("fan_processing_catalysts/glueing"))

    private val REGISTER: DeferredRegister<FanProcessingType> =
        DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, CreateBuzzyBeez.ID)

    val GLUEING = REGISTER.register("glueing", Supplier { GlueingType() })

    fun register(modEventBus: IEventBus) {
        REGISTER.register(modEventBus)
    }

    class GlueingType : FanProcessingType {
        override fun isValidAt(level: Level, pos: BlockPos): Boolean {
            val blockState = level.getBlockState(pos)
            return blockState.`is`(GLUEING_CATALYST_BLOCK)
        }

        override fun getPriority(): Int = 500

        override fun canProcess(stack: ItemStack, level: Level): Boolean {
            return AllCBeesRecipeTypes.GLUEING.find(
                SingleRecipeInput(stack), level
            ).isPresent
        }

        override fun process(stack: ItemStack, level: Level): List<ItemStack>? {
            return AllCBeesRecipeTypes.GLUEING.find(
                SingleRecipeInput(stack), level
            )
                .map { it.value }
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
            // Honey slide behavior — slow fall, clamp speed, slide sounds
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
