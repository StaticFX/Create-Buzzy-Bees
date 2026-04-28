package de.devin.cbbees.content.domain.action

import de.devin.cbbees.content.domain.action.impl.DropOffItemsAction
import de.devin.cbbees.content.domain.action.impl.PickupItemsAction
import de.devin.cbbees.content.domain.action.impl.PlaceBeltAction
import de.devin.cbbees.content.domain.action.impl.PlaceBlockAction
import de.devin.cbbees.content.domain.action.impl.RemoveBlockAction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag

/**
 * Polymorphic serializer for [BeeAction] implementations.
 * Uses a type discriminator string to dispatch save/load to the correct implementation.
 */
object BeeActionSerializer {

    private const val TYPE_KEY = "ActionType"
    private const val DATA_KEY = "ActionData"

    private const val PLACE_BLOCK = "place_block"
    private const val REMOVE_BLOCK = "remove_block"
    private const val PLACE_BELT = "place_belt"
    private const val DROP_OFF = "drop_off"
    private const val PICKUP = "pickup"

    fun save(action: BeeAction, registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        when (action) {
            is PlaceBlockAction -> {
                tag.putString(TYPE_KEY, PLACE_BLOCK)
                tag.put(DATA_KEY, action.save(registries))
            }
            is RemoveBlockAction -> {
                tag.putString(TYPE_KEY, REMOVE_BLOCK)
                tag.put(DATA_KEY, action.save())
            }
            is PlaceBeltAction -> {
                tag.putString(TYPE_KEY, PLACE_BELT)
                tag.put(DATA_KEY, action.save(registries))
            }
            is DropOffItemsAction -> {
                tag.putString(TYPE_KEY, DROP_OFF)
                tag.put(DATA_KEY, action.save())
            }
            is PickupItemsAction -> {
                tag.putString(TYPE_KEY, PICKUP)
                tag.put(DATA_KEY, action.save())
            }
            else -> throw IllegalArgumentException("Unknown BeeAction type: ${action::class}")
        }
        return tag
    }

    fun load(tag: CompoundTag, registries: HolderLookup.Provider): BeeAction? {
        val type = tag.getString(TYPE_KEY)
        val data = tag.getCompound(DATA_KEY)
        return when (type) {
            PLACE_BLOCK -> PlaceBlockAction.load(data, registries)
            REMOVE_BLOCK -> RemoveBlockAction.load(data)
            PLACE_BELT -> PlaceBeltAction.load(data, registries)
            DROP_OFF -> DropOffItemsAction.load(data)
            PICKUP -> PickupItemsAction.load(data)
            else -> null
        }
    }
}
