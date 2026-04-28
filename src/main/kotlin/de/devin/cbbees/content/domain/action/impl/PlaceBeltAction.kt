package de.devin.cbbees.content.domain.action.impl

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem
import de.devin.cbbees.content.bee.server.BeeWorker
import de.devin.cbbees.content.domain.action.BeeAction
import de.devin.cbbees.content.domain.action.ItemConsumingAction
import de.devin.cbbees.content.domain.beehive.BeeHive
import de.devin.cbbees.content.upgrades.BeeContext
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Places an entire Create belt using the same flow as Schematicannon/BeltConnectorItem.
 *
 * This action consumes the required items (shaft + belt connector) from the bee,
 * calls [BeltConnectorItem.createBelts] to construct the full chain, and then reapplies
 * casing/cover data captured from the schematic.
 */
class PlaceBeltAction(
    override val pos: BlockPos,
    private val end: BlockPos,
    val chain: List<BlockPos>,
    /** Actual belt block states from the schematic, used for ghost rendering. */
    val chainStates: List<BlockState>,
    private val casings: List<BeltBlockEntity.CasingType>,
    private val covers: List<Boolean>,
    override val requiredItems: List<ItemStack> = emptyList()
) : BeeAction, ItemConsumingAction {

    override fun execute(level: Level, worker: BeeWorker, context: BeeContext): Boolean {
        // Never replace a Mechanical Beehive — abort belt if any position overlaps
        if (chain.any { level.getBlockEntity(it) is BeeHive }) return true

        consumeItems(worker)

        BeltConnectorItem.createBelts(level, pos, end)

        val placedChain = BeltBlock.getBeltChain(level, pos)
        if (placedChain.size != chain.size) {
            placedChain.forEach { beltPos ->
                if (AllBlocks.BELT.has(level.getBlockState(beltPos))) {
                    level.destroyBlock(beltPos, false)
                }
            }
            return false
        }

        placedChain.forEachIndexed { index, beltPos ->
            val beltBE = level.getBlockEntity(beltPos) as? BeltBlockEntity ?: return@forEachIndexed
            val casing = casings.getOrNull(index) ?: BeltBlockEntity.CasingType.NONE
            if (casing != BeltBlockEntity.CasingType.NONE) {
                beltBE.setCasingType(casing)
            }
            beltBE.covered = covers.getOrNull(index) ?: false
            beltBE.setChanged()
            beltBE.sendData()
        }

        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                1, 0.2, 0.2, 0.2, 0.0
            )
        }

        return true
    }

    override fun getDescription(): String {
        return "Placing belt from (${pos.x}, ${pos.y}, ${pos.z}) to (${end.x}, ${end.y}, ${end.z})"
    }

    fun save(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("X", pos.x)
        tag.putInt("Y", pos.y)
        tag.putInt("Z", pos.z)
        tag.putInt("EndX", end.x)
        tag.putInt("EndY", end.y)
        tag.putInt("EndZ", end.z)

        val chainList = ListTag()
        chain.forEach { bp ->
            val bpTag = CompoundTag()
            bpTag.putInt("X", bp.x)
            bpTag.putInt("Y", bp.y)
            bpTag.putInt("Z", bp.z)
            chainList.add(bpTag)
        }
        tag.put("Chain", chainList)

        val stateList = ListTag()
        chainStates.forEach { state ->
            stateList.add(NbtUtils.writeBlockState(state))
        }
        tag.put("ChainStates", stateList)

        val casingList = ListTag()
        casings.forEach { casing ->
            val ct = CompoundTag()
            ct.putString("Type", casing.name)
            casingList.add(ct)
        }
        tag.put("Casings", casingList)

        val coverArray = ByteArray(covers.size) { i -> if (covers[i]) 1.toByte() else 0.toByte() }
        tag.putByteArray("Covers", coverArray)

        val itemList = ListTag()
        requiredItems.forEach { stack ->
            itemList.add(stack.save(registries))
        }
        tag.put("RequiredItems", itemList)
        return tag
    }

    companion object {
        fun load(tag: CompoundTag, registries: HolderLookup.Provider): PlaceBeltAction {
            val pos = BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))
            val end = BlockPos(tag.getInt("EndX"), tag.getInt("EndY"), tag.getInt("EndZ"))
            val blockLookup = registries.lookupOrThrow(Registries.BLOCK)

            val chainList = tag.getList("Chain", Tag.TAG_COMPOUND.toInt())
            val chain = (0 until chainList.size).map { i ->
                val bp = chainList.getCompound(i)
                BlockPos(bp.getInt("X"), bp.getInt("Y"), bp.getInt("Z"))
            }

            val stateList = tag.getList("ChainStates", Tag.TAG_COMPOUND.toInt())
            val chainStates = (0 until stateList.size).map { i ->
                NbtUtils.readBlockState(blockLookup, stateList.getCompound(i))
            }

            val casingList = tag.getList("Casings", Tag.TAG_COMPOUND.toInt())
            val casings = (0 until casingList.size).map { i ->
                BeltBlockEntity.CasingType.valueOf(casingList.getCompound(i).getString("Type"))
            }

            val coverArray = tag.getByteArray("Covers")
            val covers = coverArray.map { it != 0.toByte() }

            val itemList = tag.getList("RequiredItems", Tag.TAG_COMPOUND.toInt())
            val items = (0 until itemList.size).mapNotNull { i ->
                ItemStack.parse(registries, itemList.getCompound(i)).orElse(null)
            }

            return PlaceBeltAction(pos, end, chain, chainStates, casings, covers, items)
        }
    }
}