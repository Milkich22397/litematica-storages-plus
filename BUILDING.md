# Building Litematica Storages+

Use **JDK 25** and the included Gradle wrapper to build the project. The 1.21.11 artifact targets Java 21; the 26.x artifacts target Java 25. Run the commands below from the repository root.

## Source layout

```text
src/                       Shared implementation, resources and tests
compat/
  26.1.properties          Canonical profile for the shared 26.1–26.2 JAR
  26.2.properties          26.2 dependency/test profile
  sources.gradle           Adaptations for 26.1–26.2
  smoke/                   Compatibility test helpers
  1.21.11.properties       Dependency profile for 1.21.11
  legacy/                  Loom-remap build and adaptations for 1.21.11
build.gradle               Default 26.3 build and 26.x profile selection
```

Keep changes in the shared sources or the version adaptations. Generated compatibility sources live in `build/compat-*/generated` and must not be edited or committed. One change to the shared implementation can therefore serve all supported game versions.

## Dependencies

The current build uses local dependency JARs. Download the versions below from their authors' releases: [Fabric API](https://modrinth.com/mod/fabric-api/versions), [Litematica](https://modrinth.com/mod/litematica/versions), [MaLiLib](https://modrinth.com/mod/malilib/versions), and [Mod Menu](https://modrinth.com/mod/modmenu/versions). Third-party binaries are excluded from this repository. Fabric Loader 0.19.5 is resolved by Gradle.

| Profile | Local directory | Fabric API | Litematica | MaLiLib | Mod Menu |
| --- | --- | --- | --- | --- | --- |
| 26.3 (default) | `libs/` | `0.163.0+26.3` | `0.29.1` for 26.3 | `0.30.2` for 26.3 | `21.0.0` |
| 26.1 (shared artifact) | `libs/compat/` | `0.155.3+26.1.2` | `0.27.14` for 26.1.2 | `0.28.12` for 26.1.2 | `18.0.2` |
| 26.2 (test profile) | `libs/compat/` | `0.161.0+26.2` | `0.28.8` for 26.2 | `0.29.6` for 26.2 | `20.0.3` |
| 1.21.11 | `libs/compat/` | `0.141.6+1.21.11` | `0.26.16` for 1.21.11 | `0.27.20` for 1.21.11 | `17.0.1` |

Keep the original filenames. For example, the default profile needs `fabric-api-0.163.0+26.3.jar`, `litematica-fabric-26.3-0.29.1.jar`, `malilib-fabric-26.3-0.30.2.jar`, and `modmenu-21.0.0.jar`. Exact compatibility filenames are in the profile `.properties` files. Mod Menu is required to compile its optional integration but remains optional when playing.

Extract the nested module JARs from `META-INF/jars/` in each Fabric API JAR into `fabric-api-modules-{fabric_api_version}/` beside that JAR. For example:

- `libs/fabric-api-modules-0.163.0+26.3/`
- `libs/compat/fabric-api-modules-0.155.3+26.1.2/`
- `libs/compat/fabric-api-modules-0.141.6+1.21.11/`

For 1.21.11, also extract the nested `conditional-mixin-fabric-0.6.4.jar` from Litematica or MaLiLib into `libs/compat/legacy-support-1.21.11/`. Only one copy is needed.

## Build commands

| Minecraft supported by the artifact | Windows command | Installable output |
| --- | --- | --- |
| 26.3 | `.\gradlew.bat build` | `build/libs/litematica-storages-plus-1.3.1+mc26.3.jar` |
| 26.1 / 26.1.1 / 26.1.2 / 26.2 | `.\gradlew.bat '-Ptarget=26.1' build` | `build/compat-26.1/libs/litematica-storages-plus-1.3.1+mc26.1-26.2.jar` |
| 1.21.11 | `.\gradlew.bat -p compat/legacy '-Ptarget=1.21.11' build` | `build/compat-1.21.11/libs/litematica-storages-plus-1.3.1+mc1.21.11.jar` |

On Linux/macOS, replace `.\gradlew.bat` with `bash ./gradlew`. Use the normal JAR in Minecraft, not a sources, dev, or testmod JAR. Install only one addon artifact, plus the dependencies matching your game. The 26.2 profile supplies that client's integration driver; the canonical shared release is built using `target=26.1`.

## Checks

Every `build` runs the pure logic checks. Minecraft integration uses generated worlds under `run-smoke/`. Run game checks sequentially and never use personal saves.

```powershell
# Default 26.3 development integration
.\gradlew.bat -PintegrationTests -PwithModMenu runSmoke

# 1.21.11: actual remapped mod JAR and separate test mod, running on Java 21
.\gradlew.bat -p compat/legacy '-Ptarget=1.21.11' -PintegrationTests -PwithModMenu productionSmoke
```

Install a Java 21 toolchain for `productionSmoke`; Gradle itself still uses JDK 25. See [compat/README.md](compat/README.md) for tests of the same frozen JAR across all four 26.1–26.2 clients, and [compat/legacy/README.md](compat/legacy/README.md) for the 1.21.11 driver.

For optional Chest Tracker compatibility checks on 26.3, put Chest Tracker 2.8.4+26.3 and YACL 3.9.7 for Fabric in `libs/integration/` and add `-PwithChestTracker`. Recovery tests deliberately exercise corrupt-file and blocked-write cases; inspect the final `run-smoke/smoke-result.txt`, not only the error messages in the log.

Do not commit generated worlds, caches, logs, local dependencies or compiled artifacts. Release JARs belong in release assets.
