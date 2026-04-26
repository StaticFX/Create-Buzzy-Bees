# Mechanical Beehive

The Mechanical Beehive is a kinetic-powered block that houses bees and serves as the anchor of a bee network.

## Crafting

Crafted with brass, honeycomb, and Create mechanical components.

## Rotational Power

The beehive requires rotational force (shaft, cogwheel, etc.) to operate. RPM affects:

| Stat | Formula |
|------|---------|
| Work range | `baseRange + RPM * rangePerRpm` |
| Max bees | `defaultBees + RPM / beeDivisor` |
| Spring efficiency | `1 + RPM / speedDivisor` |

Higher RPM = more bees, larger range, faster recharging.

## Bee Storage

Place Mechanical Bee and Bumble Bee items directly into the beehive inventory. The hive automatically dispatches bees when jobs are available in range.

## Goggle Info

Look at the beehive while wearing Engineer's Goggles to see:

- Active / stored / max bees
- Active jobs with progress percentages
- Stall reasons (highlighted in red)

## Network Anchor

Each beehive is a network anchor. Logistics ports, transport ports, and other beehives within range automatically join the same network. Multiple beehives can be part of one network if they're within range of each other.
