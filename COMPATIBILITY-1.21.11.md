# Minecraft 1.21.11 validation

Version 1.3.1+mc1.21.11 was tested on 20 October 2026 with Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Litematica 0.26.16, MaLiLib 0.27.20 and Mod Menu 17.0.1. Full production runs passed on Java 21 and Java 25.

Validation includes 9,014 pure logic assertions, 17,359 core game assertions, 44 resource assertions, 1,019 optimization checks, and 73 storage feature checks. Live checks cover container recording, schematic round trips, shift-click transfers, Material List and Info HUD, storage groups, drag/drop, inline renaming, deletion confirmations, persistence and recovery, reconnecting, real chunk unloads, and refreshing a tracked shulker after returning.

The production driver verifies the addon class origin against the actual remapped distributable. Test helpers are packaged separately and are not part of the mod. UI screenshots were also inspected. Tests use generated worlds under run-smoke and do not access user saves.

See [BUILDING.md](BUILDING.md) for reproduction commands. Passing with Mod Menu does not claim compatibility with every third-party mod or every dependency release. Deliberately corrupt files and blocked temporary writes are part of recovery checks; the final test result must pass.
