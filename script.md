# How to Create a Create Mod Addon - Pipe Inspector

## Video Intro

**Talking point:** "In this tutorial, we're going to build a simple Create mod addon from scratch. It adds pipe statistics to the Engineer's Goggle overlay — when you look at a fluid pipe while wearing goggles, you'll see the fluid type, flow direction, pressure values, and how far away the nearest pump is. No blocks, no items — just pure functionality in about 100 lines of code."

**Talking point:** "This is a great first addon because it teaches you the fundamentals: project setup, how Create's API works, and how to use mixins to extend existing block entities."

---

## Part 1: Project Setup

**Talking point:** "First, let's set up our project. We need a NeoForge 1.21.1 mod with Create as a dependency."

### 1.1 Create a new project directory

Create a new folder called `CreatePipeInspector`. Copy the Gradle wrapper files (`gradle/`, `gradlew`, `gradlew.bat`) from any existing NeoForge project, or generate them with `gradle wrapper --gradle-version 8.10`.

### 1.2 `settings.gradle`

**Talking point:** "The settings file tells Gradle where to find the NeoForge plugin and what our project is called."

```groovy
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'CreatePipeInspector'
```

### 1.3 `gradle.properties`

**Talking point:** "We keep all our version numbers in gradle.properties so they're easy to update later."

```properties
# NeoForge
minecraft_version=1.21.1
neo_version=21.1.206
parchment_mappings_version=2024.11.17

# Create
create_version=6.0.9-215
flywheel_version=1.0.6
registrate_version=MC1.21-1.3.0+67

# Mod
mod_id=pipeinspector
mod_name=Create Pipe Inspector
mod_version=1.0.0
mod_group_id=com.example.pipeinspector
mod_authors=YourName
mod_description=Shows fluid pipe statistics in the Engineer's Goggle overlay.
```

### 1.4 `build.gradle`

**Talking point:** "This is the most important file in the setup. We need the NeoForge mod dev plugin, and then we add Create as a compile-time dependency. Notice we use `slim` and `transitive = false` — this avoids pulling in Create's entire dependency tree, since those are already present at runtime."

```groovy
plugins {
    id 'java-library'
    id 'idea'
    id 'net.neoforged.moddev' version '2.0.78'
}

version = mod_version
group = mod_group_id

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = "https://maven.createmod.net" }       // Create, Flywheel
    maven { url = "https://mvn.devos.one/snapshots/" }   // Registrate
}

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_mappings_version
        minecraftVersion = minecraft_version
    }

    runs {
        client {
            client()
        }
        server {
            server()
        }
    }

    mods {
        "${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    // Create (slim = no bundled dependencies)
    implementation("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") {
        transitive = false
    }

    // Flywheel — Create's rendering engine
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-${minecraft_version}:${flywheel_version}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-${minecraft_version}:${flywheel_version}")

    // Registrate — Create's registration library
    implementation("com.tterrag.registrate:Registrate:${registrate_version}")
}

// Expand properties in neoforge.mods.toml
tasks.withType(ProcessResources).configureEach {
    var replaceProperties = [
        minecraft_version   : minecraft_version,
        neo_version         : neo_version,
        mod_id              : mod_id,
        mod_name            : mod_name,
        mod_version         : mod_version,
        mod_authors         : mod_authors,
        mod_description     : mod_description,
    ]
    inputs.properties replaceProperties
    filesMatching(['META-INF/neoforge.mods.toml']) {
        expand replaceProperties + [project: project]
    }
}
```

**Talking point:** "The key repositories are `maven.createmod.net` for Create and Flywheel, and `mvn.devos.one` for Registrate. These are the standard repos every Create addon uses."

---

## Part 2: Mod Metadata

### 2.1 `src/main/resources/META-INF/neoforge.mods.toml`

**Talking point:** "The neoforge.mods.toml file tells NeoForge about our mod — its ID, name, dependencies, and that we have a mixin config. Notice we declare Create as a required dependency so the game won't load without it."

```toml
modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"

[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "${mod_name}"
authors = "${mod_authors}"
description = '''${mod_description}'''

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "[21,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1]"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "create"
type = "required"
versionRange = "[6.0,)"
ordering = "AFTER"
side = "BOTH"

[[mixins]]
config = "${mod_id}.mixins.json"
```

---

## Part 3: Understanding Create's Goggle System

**Talking point:** "Before we write any code, let's understand how Create's goggle overlay works. When you wear Engineer's Goggles and look at a block, Create's `GoggleOverlayRenderer` checks if the block entity implements the `IHaveGoggleInformation` interface. If it does, it calls `addToGoggleTooltip()` and renders whatever text you return."

**Talking point:** "Fluid pipes in Create do NOT implement this interface by default — that's why you don't see any info when looking at pipes. We're going to add it using Mixins, which let us inject code into existing classes."

**Talking point:** "The data we want lives in `FluidTransportBehaviour` — every pipe block entity has one. It holds a map of `PipeConnection` objects, one per connected direction. Each connection tracks the fluid flow and pressure values."

---

## Part 4: The Mod Entry Point

### 4.1 `src/main/java/com/example/pipeinspector/PipeInspector.java`

**Talking point:** "Our mod class is almost empty. Since we're using mixins to add functionality to existing blocks, we don't need to register anything. This is about as minimal as a mod can get."

```java
package com.example.pipeinspector;

import net.neoforged.fml.common.Mod;

@Mod(PipeInspector.ID)
public class PipeInspector {
    public static final String ID = "pipeinspector";
}
```

---

## Part 5: The Mixins

**Talking point:** "Now for the core of our addon. We need two mixins — one for standard copper fluid pipes (`FluidPipeBlockEntity`), and one for glass, encased, and smart pipes (`StraightPipeBlockEntity`). Both do the same thing: they implement `IHaveGoggleInformation` and delegate to our handler class."

### 5.1 `src/main/resources/pipeinspector.mixins.json`

**Talking point:** "First, the mixin config file. This tells the mixin system where to find our mixin classes."

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.pipeinspector.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "FluidPipeGoggleMixin",
    "StraightPipeGoggleMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

### 5.2 `src/main/java/com/example/pipeinspector/mixin/FluidPipeGoggleMixin.java`

**Talking point:** "This mixin targets `FluidPipeBlockEntity` — that's the standard copper fluid pipe. By implementing `IHaveGoggleInformation` on the mixin class, the Mixin framework will add that interface to the target class at runtime. So when Create's renderer checks `be instanceof IHaveGoggleInformation`, it will return true for pipes."

```java
package com.example.pipeinspector.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import com.example.pipeinspector.PipeGoggleHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.network.chat.Component;

@Mixin(FluidPipeBlockEntity.class)
public class FluidPipeGoggleMixin implements IHaveGoggleInformation {

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return PipeGoggleHandler.addPipeInfo(tooltip, (SmartBlockEntity) (Object) this);
    }
}
```

**Talking point:** "Notice the cast `(SmartBlockEntity) (Object) this`. In a mixin, `this` refers to the target class instance at runtime — which IS a `SmartBlockEntity`. But the compiler doesn't know that, so we cast through `Object` first. This is a standard mixin pattern."

### 5.3 `src/main/java/com/example/pipeinspector/mixin/StraightPipeGoggleMixin.java`

**Talking point:** "The second mixin is identical but targets `StraightPipeBlockEntity`. This covers glass pipes, encased pipes, and smart fluid pipes — since smart pipes extend straight pipes, they inherit our mixin too."

```java
package com.example.pipeinspector.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import com.example.pipeinspector.PipeGoggleHandler;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.network.chat.Component;

@Mixin(StraightPipeBlockEntity.class)
public class StraightPipeGoggleMixin implements IHaveGoggleInformation {

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return PipeGoggleHandler.addPipeInfo(tooltip, (SmartBlockEntity) (Object) this);
    }
}
```

---

## Part 6: The Pipe Goggle Handler

### 6.1 `src/main/java/com/example/pipeinspector/PipeGoggleHandler.java`

**Talking point:** "This is where all the actual logic lives. We read the pipe's transport behaviour to get flow and pressure data, then format it using Create's `CreateLang` builder — the same API Create uses internally for all its goggle tooltips. This ensures our text looks consistent with the rest of Create's UI."

**Talking point (on the BFS):** "For finding the nearest pump, we do a simple breadth-first search through connected pipes. We start at the current pipe and check each neighbor — if it's a pump, we're done. If it's another pipe, we add it to the search queue. We limit the search to Create's pump range so we don't search forever."

```java
package com.example.pipeinspector;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class PipeGoggleHandler {

    public static boolean addPipeInfo(List<Component> tooltip, SmartBlockEntity be) {
        FluidTransportBehaviour transport = be.getBehaviour(FluidTransportBehaviour.TYPE);
        if (transport == null || transport.interfaces == null)
            return false;

        // Header
        CreateLang.translate("pipeinspector.goggle.header")
            .forGoggles(tooltip);

        boolean hasInfo = false;

        // Flow and pressure per connection
        for (Map.Entry<Direction, PipeConnection> entry : transport.interfaces.entrySet()) {
            Direction dir = entry.getKey();
            PipeConnection connection = entry.getValue();

            if (connection.hasFlow()) {
                PipeConnection.Flow flow = connection.flow.orElse(null);
                if (flow != null && !flow.fluid.isEmpty()) {
                    String dirName = dir.getName().substring(0, 1).toUpperCase()
                        + dir.getName().substring(1);
                    String flowDir = flow.inbound ? "In" : "Out";

                    CreateLang.builder()
                        .add(Component.translatable(
                            flow.fluid.getHoverName().getString()))
                        .style(ChatFormatting.GRAY)
                        .text(ChatFormatting.DARK_GRAY,
                            " " + dirName + " ")
                        .add(CreateLang.builder()
                            .text(flow.inbound
                                ? ChatFormatting.GREEN
                                : ChatFormatting.GOLD, flowDir))
                        .forGoggles(tooltip, 1);
                    hasInfo = true;
                }
            }

            if (connection.hasPressure()) {
                Couple<Float> pressure = connection.getPressure();
                float inbound = pressure.getFirst();
                float outward = pressure.getSecond();

                if (inbound != 0 || outward != 0) {
                    String dirName = dir.getName().substring(0, 1).toUpperCase()
                        + dir.getName().substring(1);

                    CreateLang.builder()
                        .text(ChatFormatting.DARK_GRAY, dirName + " ")
                        .add(CreateLang.builder()
                            .text(ChatFormatting.AQUA,
                                String.format("%.1f", inbound)))
                        .text(ChatFormatting.DARK_GRAY, " / ")
                        .add(CreateLang.builder()
                            .text(ChatFormatting.AQUA,
                                String.format("%.1f", outward)))
                        .forGoggles(tooltip, 1);
                    hasInfo = true;
                }
            }
        }

        // Pump distance
        Level level = be.getLevel();
        if (level != null) {
            int pumpDist = findNearestPump(level, be.getBlockPos());
            int pumpRange = FluidPropagator.getPumpRange();

            if (pumpDist >= 0) {
                int remaining = pumpRange - pumpDist;
                CreateLang.translate("pipeinspector.goggle.pump_distance",
                        pumpDist, remaining)
                    .style(remaining <= 2
                        ? ChatFormatting.RED
                        : ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            } else {
                CreateLang.translate("pipeinspector.goggle.no_pump")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
            }
            hasInfo = true;
        }

        return hasInfo;
    }

    /**
     * BFS through connected pipes to find the nearest pump.
     * Returns the distance in blocks, or -1 if no pump found
     * within range.
     */
    private static int findNearestPump(Level level, BlockPos start) {
        int maxRange = FluidPropagator.getPumpRange();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Queue<Integer> distances = new LinkedList<>();

        visited.add(start);
        queue.add(start);
        distances.add(0);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int dist = distances.poll();

            if (dist > maxRange)
                continue;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor))
                    continue;
                visited.add(neighbor);

                // Check if neighbor is a pump
                if (level.getBlockEntity(neighbor)
                        instanceof PumpBlockEntity) {
                    return dist + 1;
                }

                // Check if neighbor is a pipe (has fluid transport)
                FluidTransportBehaviour pipe = BlockEntityBehaviour
                    .get(level, neighbor, FluidTransportBehaviour.TYPE);
                if (pipe != null) {
                    queue.add(neighbor);
                    distances.add(dist + 1);
                }
            }
        }

        return -1;
    }
}
```

**Talking point (on CreateLang):** "The `CreateLang.translate()` and `CreateLang.builder()` methods give us Create's styled text builder. The `.forGoggles(tooltip)` call formats the line with the right indentation and spacing that matches all other goggle overlays. The second parameter `1` adds an extra indent level for sub-items."

**Talking point (on pressure display):** "The pressure is stored as a `Couple<Float>` — that's Create's pair type. First value is inbound pressure, second is outward. When a pump pushes fluid through a pipe, you'll see non-zero pressure values that indicate which direction the fluid is being pushed."

**Talking point (on pump coloring):** "We color the pump distance red if there are only 2 or fewer blocks of range remaining. This gives players a quick visual warning that they're approaching the pump's maximum range."

---

## Part 7: Localization

### 7.1 `src/main/resources/assets/pipeinspector/lang/en_us.json`

**Talking point:** "Finally, our language file. Create's `CreateLang.translate()` automatically prepends our mod ID, so `pipeinspector.goggle.header` is the full key."

```json
{
  "pipeinspector.goggle.header": "Pipe Status",
  "pipeinspector.goggle.pump_distance": "Pump: %s blocks (%s remaining)",
  "pipeinspector.goggle.no_pump": "No pump connected"
}
```

---

## Part 8: Testing

**Talking point:** "Let's test it. Run `./gradlew runClient`, load into a creative world, and set up a simple pipe network."

### Test Setup
1. Place a fluid tank with water
2. Connect fluid pipes from the tank
3. Place a mechanical pump powered by a shaft
4. Extend pipes past the pump
5. Put on Engineer's Goggles (Create's helmet item)
6. Look at different pipes in the network

### What You Should See
- **Header**: "Pipe Status"
- **Flow lines**: The fluid name (e.g. "Water"), the direction (North, South, etc.), and whether flow is inbound or outbound
- **Pressure lines**: Per-direction pressure values showing how the pump distributes force
- **Pump distance**: How many blocks away the nearest pump is, and how many blocks of range remain
- **At pipes far from pump**: Red coloring when only 2 blocks of range remain
- **At disconnected pipes**: "No pump connected" in gray

---

## Part 9: Recap & Project Structure

**Talking point:** "Let's recap what we built. Our entire addon is just 9 files."

```
CreatePipeInspector/
├── build.gradle                              # Build config with Create dependency
├── gradle.properties                         # Version numbers
├── settings.gradle                           # Project name + NeoForge repo
├── src/main/
│   ├── java/com/example/pipeinspector/
│   │   ├── PipeInspector.java                # Mod entry point (3 lines of real code)
│   │   ├── PipeGoggleHandler.java            # Tooltip logic + pump finder (~100 lines)
│   │   └── mixin/
│   │       ├── FluidPipeGoggleMixin.java     # Adds goggles to copper pipes
│   │       └── StraightPipeGoggleMixin.java  # Adds goggles to glass/encased/smart pipes
│   └── resources/
│       ├── pipeinspector.mixins.json         # Mixin config
│       ├── META-INF/neoforge.mods.toml       # Mod metadata
│       └── assets/pipeinspector/lang/en_us.json  # Translations
```

**Talking point:** "The key takeaway is how simple a Create addon can be. We didn't need to register any blocks, items, or renderers. Create's goggle system is designed to be extended — you just implement the interface, and the overlay appears. Mixins let us add that interface to classes we don't own."

**Talking point:** "From here, you could extend this addon with more features — like showing fluid throughput rates, color-coded pressure visualization in the world, or even a config to customize what info is displayed. But that's for another video."

---

## Key APIs Reference

| API | What It Does |
|-----|-------------|
| `IHaveGoggleInformation` | Interface that makes a block entity show info in the goggle overlay |
| `FluidTransportBehaviour.TYPE` | Behaviour type to look up pipe transport data on a SmartBlockEntity |
| `PipeConnection` | Holds flow and pressure data for one direction of a pipe |
| `PipeConnection.Flow` | The actual flow — fluid type, direction (inbound/outbound), progress |
| `PipeConnection.getPressure()` | Returns `Couple<Float>` — [inbound, outward] pressure values |
| `FluidPropagator.getPumpRange()` | Max distance a pump can push fluid (from Create's config) |
| `BlockEntityBehaviour.get(level, pos, type)` | Look up a behaviour on any SmartBlockEntity at a position |
| `CreateLang.translate().forGoggles(tooltip)` | Create's styled tooltip builder with proper goggle formatting |
