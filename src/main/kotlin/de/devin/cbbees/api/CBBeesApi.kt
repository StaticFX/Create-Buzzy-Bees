package de.devin.cbbees.api

import de.devin.cbbees.api.action.BeeActionCodec
import de.devin.cbbees.api.schematic.BlockTransform
import de.devin.cbbees.api.schematic.SchematicSubstitute
import de.devin.cbbees.api.storage.PortStorageProvider
import de.devin.cbbees.content.domain.action.BeeActionSerializer
import de.devin.cbbees.content.logistics.storage.PortStorages
import de.devin.cbbees.content.schematics.BlockTransforms
import de.devin.cbbees.content.schematics.SchematicSubstitutes

/** Entry point for other mods. Call these during mod setup; providers are asked in registration order. */
object CBBeesApi {

    /** Lets ports read from and write into your storage blocks or networks. */
    @JvmStatic
    fun registerPortStorage(provider: PortStorageProvider) = PortStorages.register(provider)

    /** Lets bees build blocks that can't be placed as they are in the schematic. */
    @JvmStatic
    fun registerSchematicSubstitute(substitute: SchematicSubstitute) = SchematicSubstitutes.register(substitute)

    /** Lets schematics rotate and mirror your blocks correctly. */
    @JvmStatic
    fun registerBlockTransform(transform: BlockTransform) = BlockTransforms.register(transform)

    /** Lets your custom bee actions be saved with their job. */
    @JvmStatic
    fun registerBeeAction(codec: BeeActionCodec<*>) = BeeActionSerializer.register(codec)
}
