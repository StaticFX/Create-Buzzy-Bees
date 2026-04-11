package de.devin.cbbees.content.logistics.transport.client

import com.simibubi.create.AllItems
import com.simibubi.create.content.equipment.goggles.GogglesItem
import de.devin.cbbees.content.logistics.transport.TransportPortBlockEntity
import de.devin.cbbees.util.ClientSide
import net.createmod.catnip.outliner.Outliner
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

/**
 * When a player wearing goggles looks at a Cargo Port,
 * renders lines to all other Cargo Ports with the same frequency in the network.
 */
@ClientSide
object CargoPortLinkRenderer {

    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (mc.screen != null) return

        if (!GogglesItem.isWearingGoggles(player)) return

        // Only show frequency links while holding a wrench
        val holdingWrench = AllItems.WRENCH.isIn(player.mainHandItem) || AllItems.WRENCH.isIn(player.offhandItem)
        if (!holdingWrench) return

        val target = mc.hitResult
        if (target !is BlockHitResult) return

        val be = level.getBlockEntity(target.blockPos) as? TransportPortBlockEntity ?: return
        val network = be.network()
        val myKey = be.linkBehaviour.networkKey

        val otherPorts = network.transportPorts
            .filterIsInstance<TransportPortBlockEntity>()
            .filter { it !== be && it.linkBehaviour.networkKey == myKey }

        if (otherPorts.isEmpty()) return

        val fromCenter = Vec3.atCenterOf(be.blockPos)
        val isProvider = be.isProvider()
        val color = if (isProvider) 0xFF9E44 else 0x44B0FF // orange for provider, blue for requester

        for (other in otherPorts) {
            val toCenter = Vec3.atCenterOf(other.blockPos)

            Outliner.getInstance()
                .showLine("cargo_link_${be.blockPos.asLong()}_${other.blockPos.asLong()}", fromCenter, toCenter)
                .colored(color)
                .lineWidth(1 / 16f)

            Outliner.getInstance()
                .chaseAABB("cargo_link_target_${other.blockPos.asLong()}", AABB(other.blockPos))
                .colored(color)
                .lineWidth(1 / 16f)
        }
    }
}
