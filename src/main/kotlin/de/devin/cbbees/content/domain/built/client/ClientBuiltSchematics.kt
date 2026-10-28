package de.devin.cbbees.content.domain.built.client

import de.devin.cbbees.util.ClientSide
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.UUID

data class ClientBuiltSchematic(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val ownerName: String?,
    val bounds: BoundingBox,
) {
    fun isOwnedBy(playerId: UUID) = ownerId == playerId

    val volume: Long get() = bounds.xSpan.toLong() * bounds.ySpan * bounds.zSpan
}

@ClientSide
object ClientBuiltSchematics {

    var builds: List<ClientBuiltSchematic> = emptyList()
        private set

    fun update(builds: List<ClientBuiltSchematic>) {
        this.builds = builds
    }

    fun at(pos: BlockPos): ClientBuiltSchematic? = builds.filter { it.bounds.isInside(pos) }.minByOrNull { it.volume }

    fun clear() {
        builds = emptyList()
    }
}
