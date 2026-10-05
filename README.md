# eppopkaaddons

Standalone Fabric client mod for Minecraft **26.1.2** (Java 25).

Open the configuration with **`/ea`**. It can be installed alongside NoammAddons: it has its own mod id, package namespace, configuration and command.

## Eppopka modules

- Always Water
- Ambient Particles
- Auto Rag
- Auto Reel
- Bestiary ESP
- China Hat
- Halo
- Invisibug ESP
- Item Aura
- LB Timer
- Move Hearts
- Move Hotbar
- Odin Dungeon Waypoints
- Rotating Item
- Shitter List
- Tiki ESP, including Black Hole / Huntaxe automation

The Click GUI contains a dedicated **Eppopka** category, animated command-deck background,
search, draggable category panels, resizable setting windows, themes, and an animated wordmark.

Only the modules listed above are registered. NoammAddons features, `/na`, private API authentication, WebSocket startup and legacy AutoSession code are not started by this mod.

## Build

Use JDK 25:

```bash
./gradlew build
```

Artifacts are written to `build/libs/`. The `cheat` variant contains automation modules; the
`legit` variant excludes code guarded by the original `CHEAT` preprocessing flag.
