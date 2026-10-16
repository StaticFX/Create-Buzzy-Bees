package de.devin.cbbees.content.domain.logistics

import com.simibubi.create.content.redstone.link.LinkBehaviour

/**
 * Port used by bumble bees for item transport.
 *
 * Provider ports supply items from attached inventories.
 * Requester ports accept items into attached inventories.
 *
 * Ports are paired via a two-item frequency system using Create's [LinkBehaviour].
 * Ports with no frequency set only connect to other ports with no frequency set.
 * Ports with matching frequency pairs connect to each other.
 *
 * Extends [ReservablePort] for shared item handling and reservation support.
 */
interface TransportPort : ReservablePort {

    fun isProvider(): Boolean

    val linkBehaviour: LinkBehaviour

    fun frequenciesMatch(other: TransportPort): Boolean {
        return linkBehaviour.networkKey == other.linkBehaviour.networkKey
    }

    fun isValidProvider(): Boolean

    fun isValidRequester(): Boolean
}
