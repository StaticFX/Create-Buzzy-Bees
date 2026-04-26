# Mechanical Bees

There are two types of mechanical bees, each designed for different tasks.

## Mechanical Bee

The standard worker bee. Handles **construction** and **deconstruction** jobs:

- Flies to logistics ports to gather required materials
- Places blocks at schematic positions
- Breaks blocks during deconstruction
- Picks up item drops after breaking blocks
- Returns to the hive to recharge its clockwork spring

## Mechanical Bumble Bee

The transport specialist. Handles **pickup** and **transport** jobs:

- Collects loose items from the ground (pickup jobs)
- Transports items between transport ports (transport jobs)
- Delivers collected items to drop-off logistics ports
- Larger inventory capacity than regular bees

## Clockwork Spring

Every bee has an internal clockwork spring that depletes as it works:

| Action | Spring drain |
|--------|-------------|
| Block placement | 2% per block |
| Block breaking | 1.5% per block |
| Flight | 0.01% per tick |
| Item pickup | 1% per pickup |
| Item deposit | 1% per deposit |

When the spring is depleted, the bee returns to the hive to recharge. Recharge time scales with RPM — faster shafts mean faster recharging.

For portable beehives, recharging also consumes honey fuel.
