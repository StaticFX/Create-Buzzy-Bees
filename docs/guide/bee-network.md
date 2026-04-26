# Bee Network

The bee network is the backbone of Create Buzzy Beez. It connects hives, logistics ports, and transport ports into a unified system that bees use to find materials, deliver items, and coordinate work.

## How Networks Form

A network forms automatically when components are placed within range of each other:

- **Mechanical Beehives** are the anchors — they define the network's range
- **Logistics Ports** connect to the nearest hive within range
- **Transport Ports** enable item transport between linked locations

The network range is determined by the hive's RPM:

```
range = baseRange + RPM * rangePerRpm
```

## Network Components

### Mechanical Beehive (Anchor)

The hive is the central hub. It:
- Houses bee items
- Provides rotational-force-based range and bee scaling
- Dispatches bees to work on jobs

Multiple hives can be part of the same network if they're within range of each other.

### Logistics Ports

Ports are the interface between bees and your storage:

- **Provider mode** — bees pick up items from the connected inventory
- **Drop-off mode** — bees deposit items into the connected inventory
- **Filters** control which items the port accepts
- **Priority** determines which port bees prefer when multiple match

### Transport Ports

Transport ports enable automatic item transport between locations:

- **Provider** transport ports offer items for pickup
- **Requester** transport ports request items to be delivered
- Linked by **frequency** (same as Create's logistics)
- Bumble bees handle transport jobs automatically

## Goggle Information

Wear **Engineer's Goggles** and look at a beehive to see:
- Network stats (active bees, stored bees, max capacity)
- Active job list with progress
- Stall reasons if bees are stuck
