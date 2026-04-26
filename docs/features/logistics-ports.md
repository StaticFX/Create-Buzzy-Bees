# Logistics Ports

Logistics Ports are the interface between bees and your item storage. They connect to inventories (chests, vaults, barrels) and allow bees to pick up or deposit items.

## Modes

### Provider Mode
Bees pick up items from the connected inventory through this port.
- Used for **construction** jobs (gathering building materials)
- Filter determines which items are available

### Drop-off Mode
Bees deposit items into the connected inventory through this port.
- Used for **deconstruction** drops and **pickup** jobs
- Filter determines which items are accepted

## Filtering

Each port has a filter that controls which items it handles. Only items matching the filter will be picked up or accepted.

## Priority

When multiple ports can provide the same item, bees prefer the port with the **highest priority**. Set priority via the port's GUI.

## Status Indicator

The port's bulb shows its current state:
- **Green** — valid and operational
- **Amber** — busy (processing a bee interaction)
- **Red** — invalid configuration

## Connection

Ports automatically connect to the nearest bee network anchor (beehive) within range. The port must be within the hive's work range to be part of the network.
