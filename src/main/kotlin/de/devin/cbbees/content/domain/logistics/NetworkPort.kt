package de.devin.cbbees.content.domain.logistics

import de.devin.cbbees.content.domain.network.INetworkComponent
import net.minecraft.core.BlockPos

interface NetworkPort : INetworkComponent {

    fun gatherPos(): BlockPos = pos.above()

    fun priority(): Int

    override fun isAnchor(): Boolean = false

    override fun getNetworkingRange(): Double = 0.0

    override fun isInWorkRange(pos: BlockPos): Boolean = false
}
