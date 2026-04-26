# FAQ

## My bees aren't flying

- Check that the Mechanical Beehive has rotational power (RPM > 0)
- Verify there are bee items in the hive
- Make sure the job's target area is within the hive's work range
- For portable beehives, check honey fuel level

## The job is stuck (red outline)

Look at the bounding box to see the stall reason in the action bar. Common causes:

- **Missing materials** — add the required blocks to a provider logistics port
- **Out of range** — move the hive closer or increase RPM
- **No bees available** — add more bee items to the hive
- **No bumble bees** — pickup/transport jobs need Mechanical Bumble Bee items
- **No logistics port** — add a drop-off port for bees to deliver items

## The spout won't fill my portable beehive

Place the beehive on a belt or depot under a Create Spout filled with honey fluid. The conversion is 250mB honey = 100 fuel units.

## Bees are placing blocks without materials

This was fixed in 1.3.0. Update to the latest version.

## How do I make bumble bees?

Craft Mechanical Bumble Bee items (recipe uses the same pattern as regular bees but with different materials). Place them in the hive alongside regular bees.

## Can I use this with the Portable Beehive only (no block hive)?

Yes. The Portable Beehive forms its own network centered on the player. Place logistics ports within range and you're set.

## Does it work in multiplayer?

Yes. Each player's jobs are independent. Multiple players can have their own bee networks operating simultaneously.
