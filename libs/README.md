# libs/ — local build prerequisites (not committed)

This folder holds **unmodified** third-party mod jars used as compile-only references
(`modCompileOnly` in `build.gradle`). They are **not bundled** into the built mod and are **not
modified** — they exist only so the code can compile against their APIs. The mod runs fine without
them at runtime (RCT support is guarded by `FabricLoader.isModLoaded("rctmod")`).

The folder is `.gitignore`d. To build, drop these jars in here:

| File | Source |
|------|--------|
| `rctmod-fabric-1.21.1-0.17.6-beta.jar` | Radical Cobblemon Trainers (rctmod) |
| `rctapi-fabric-1.21.1-0.14.8-beta.jar` | Radical Cobblemon Trainers API (rctapi) |

Both ship inside the Cobbleverse modpack (`.../instances/Cobbleverse/minecraft/mods/`) or from the
RCT distribution. Versions only need to match the APIs used (`TrainerMob`, `RCTMod`,
`TrainerManager`, `TrainerModel`/`PokemonModel`); bump freely and rebuild.
