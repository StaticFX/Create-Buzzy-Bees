package de.devin.cbbees.content.apiary

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour
import com.simibubi.create.foundation.item.ItemHelper
import com.simibubi.create.foundation.utility.CreateLang
import de.devin.cbbees.content.processing.PollinatingRecipe
import de.devin.cbbees.registry.AllCBeesRecipeTypes
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.items.IItemHandler
import net.neoforged.neoforge.items.ItemStackHandler
import net.neoforged.neoforge.items.wrapper.RecipeWrapper
import kotlin.math.abs

class MechanicalApiaryBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    KineticBlockEntity(type, pos, state), IHaveGoggleInformation {

    companion object {
        const val TANK_CAPACITY = 2000
        private const val RETRY_DELAY = 20
        private const val DEFAULT_DURATION = 100
    }

    val inputInv = object : ItemStackHandler(2) {
        override fun onContentsChanged(slot: Int) {
            retryCooldown = 0
            setChanged()
        }
    }

    val itemCapability: IItemHandler = ApiaryInventoryHandler()

    lateinit var tank: SmartFluidTankBehaviour
        private set

    var timer = 0
        private set

    private var lastRecipe: PollinatingRecipe? = null
    private var retryCooldown = 0

    override fun addBehaviours(behaviours: MutableList<BlockEntityBehaviour>) {
        behaviours.add(DirectBeltInputBehaviour(this))
        tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY).forbidInsertion()
        behaviours.add(tank)
        super.addBehaviours(behaviours)
    }

    override fun tick() {
        super.tick()
        if (getSpeed() == 0f) return

        if (timer > 0) {
            timer -= processingSpeed
            if (level!!.isClientSide) {
                spawnParticles()
                return
            }
            if (timer <= 0) process()
            return
        }

        if (level!!.isClientSide || isInputEmpty() || retryCooldown-- > 0) return

        val recipe = findRecipe()
        if (recipe == null || !canOutput(recipe)) {
            retryCooldown = RETRY_DELAY
            return
        }
        timer = recipe.processingDuration.takeIf { it > 0 } ?: DEFAULT_DURATION
        sendData()
    }

    private val processingSpeed: Int get() = Mth.clamp((abs(getSpeed()) / 16f).toInt(), 1, 512)

    private fun isInputEmpty() = (0 until inputInv.slots).all { inputInv.getStackInSlot(it).isEmpty }

    private fun findRecipe(): PollinatingRecipe? {
        val input = RecipeWrapper(inputInv)
        lastRecipe?.takeIf { it.matches(input, level!!) }?.let { return it }
        return AllCBeesRecipeTypes.POLLINATING.find<RecipeWrapper, PollinatingRecipe>(input, level!!)
            .map(RecipeHolder<PollinatingRecipe>::value)
            .orElse(null)
            ?.also { lastRecipe = it }
    }

    private fun canOutput(recipe: PollinatingRecipe): Boolean = recipe.fluidResults.all {
        tank.primaryHandler.fill(it.copy(), FluidAction.SIMULATE) == it.amount
    }

    private fun process() {
        val recipe = findRecipe() ?: return
        if (!canOutput(recipe)) return
        val slots = recipe.matchSlots(RecipeWrapper(inputInv)) ?: return

        slots.forEach { slot ->
            val stack = inputInv.getStackInSlot(slot)
            val remainder = stack.craftingRemainingItem
            inputInv.extractItem(slot, 1, false)
            if (!remainder.isEmpty) Block.popResource(level!!, worldPosition.above(), remainder)
        }
        recipe.fluidResults.forEach { tank.primaryHandler.fill(it.copy(), FluidAction.EXECUTE) }
        if (level!!.random.nextInt(3) == 0) level!!.playSound(null, worldPosition, SoundEvents.BEEHIVE_DRIP, SoundSource.BLOCKS, 0.8f, 1f)

        sendData()
        setChanged()
    }

    private fun spawnParticles() {
        val level = level ?: return
        if (level.random.nextInt(4) != 0) return
        val facing = blockState.getValue(MechanicalApiaryBlock.FACING)
        val spread = (level.random.nextDouble() - 0.5) * 8 / 16.0
        val slot = Vec3.atCenterOf(worldPosition)
            .add(Vec3.atLowerCornerOf(facing.normal).scale(7 / 16.0))
            .add(Vec3.atLowerCornerOf(facing.clockWise.normal).scale(spread))
            .with(Direction.Axis.Y, worldPosition.y + (10 + level.random.nextDouble() * 2) / 16.0)
        level.addParticle(ParticleTypes.FALLING_NECTAR, slot.x, slot.y, slot.z, 0.0, 0.0, 0.0)
    }

    override fun tickAudio() {
        super.tickAudio()
        val level = level ?: return
        if (getSpeed() == 0f || timer <= 0 || level.random.nextInt(80) != 0) return
        val pitch = Mth.clamp(abs(getSpeed()) / 256f + 0.8f, 0.8f, 1.2f)
        level.playLocalSound(worldPosition, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.6f, pitch, false)
    }

    fun getFluidCapability(side: Direction?): IFluidHandler? =
        tank.capability.takeIf { side == null || side == blockState.getValue(MechanicalApiaryBlock.FACING).opposite }

    override fun destroy() {
        super.destroy()
        ItemHelper.dropContents(level, worldPosition, inputInv)
    }

    override fun invalidate() {
        super.invalidate()
        invalidateCapabilities()
    }

    override fun write(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        compound.putInt("Timer", timer)
        compound.put("InputInventory", inputInv.serializeNBT(registries))
        super.write(compound, registries, clientPacket)
    }

    override fun read(compound: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        timer = compound.getInt("Timer")
        inputInv.deserializeNBT(registries, compound.getCompound("InputInventory"))
        super.read(compound, registries, clientPacket)
    }

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        Lang.builder("cbbees").translate("gui.goggles.apiary")
            .style(ChatFormatting.GOLD)
            .forGoggles(tooltip)
        honeyTooltip(tooltip)
        super<KineticBlockEntity>.addToGoggleTooltip(tooltip, isPlayerSneaking)
        return true
    }

    private fun honeyTooltip(tooltip: MutableList<Component>) {
        val honey = tank.primaryHandler.fluid
        val capacity = CreateLang.number(tank.primaryHandler.capacity.toDouble()).add(CreateLang.translate("generic.unit.millibuckets"))
        if (honey.isEmpty) {
            Lang.builder("cbbees").translate("gui.goggles.apiary.empty")
                .add(capacity.style(ChatFormatting.GOLD))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1)
            return
        }
        CreateLang.fluidName(honey).style(ChatFormatting.GRAY).forGoggles(tooltip, 1)
        CreateLang.builder()
            .add(CreateLang.number(honey.amount.toDouble()).add(CreateLang.translate("generic.unit.millibuckets")).style(ChatFormatting.GOLD))
            .text(ChatFormatting.GRAY, " / ")
            .add(capacity.style(ChatFormatting.DARK_GRAY))
            .forGoggles(tooltip, 1)
    }

    private inner class ApiaryInventoryHandler : IItemHandler by inputInv {

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            val current = inputInv.getStackInSlot(slot)
            if (!current.isEmpty) return ItemStack.isSameItemSameComponents(current, stack)
            if ((0 until inputInv.slots).any { ItemStack.isSameItemSameComponents(inputInv.getStackInSlot(it), stack) })
                return false
            val others = (0 until inputInv.slots).filter { it != slot }.map(inputInv::getStackInSlot).filterNot(ItemStack::isEmpty)
            return recipes().any { canCombine(it, others + stack) }
        }

        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack =
            if (isItemValid(slot, stack)) inputInv.insertItem(slot, stack, simulate) else stack

        override fun extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

        private fun recipes() = level?.recipeManager
            ?.getAllRecipesFor(AllCBeesRecipeTypes.POLLINATING.getType<RecipeWrapper, PollinatingRecipe>())
            ?.map(RecipeHolder<PollinatingRecipe>::value)
            .orEmpty()

        private fun canCombine(recipe: PollinatingRecipe, stacks: List<ItemStack>): Boolean {
            val ingredients = recipe.ingredients
            if (stacks.size > ingredients.size) return false
            fun assign(index: Int, used: Set<Int>): Boolean =
                index == stacks.size || ingredients.indices.any { i ->
                    i !in used && ingredients[i].test(stacks[index]) && assign(index + 1, used + i)
                }
            return assign(0, emptySet())
        }
    }
}
