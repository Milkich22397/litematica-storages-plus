# Compatibility builds

This directory holds version profiles and adaptations around one shared `src/` tree. The default root build targets 26.3; the profile below produces the common 26.1–26.2 artifact. For Minecraft 1.21.11, use the isolated [legacy build](legacy/README.md). Full dependency setup and all three release commands are in [BUILDING.md](../BUILDING.md).

The maintained implementation is `src/client/java`. Compatibility builds generate their Java sources under `build/compat-*/generated`; do not edit those generated files.

The canonical 26.1–26.2 binary is compiled against 26.1:

```powershell
.\gradlew.bat build '-Ptarget=26.1' --no-daemon
```

Output: `build/compat-26.1/libs/litematica-storages-plus-1.3.1+mc26.1-26.2.jar`.

The normal build without `target` continues to produce the separate Minecraft 26.3 variant. Both variants use the same mod ID, settings, storage schema, logo and translations. Install only the variant matching the Minecraft version. Exact data-version checks still protect saved observations from being interpreted with a different game's registries.

## Adaptation

`sources.gradle` adapts keyboard names, GLFW key polling, container-content stream names and the GUI blit constructor descriptors. Both 26.1 and 26.2 constructor descriptors are present in the common binary, with the normal required-injection check retained. `ClientScreenAccess` resolves the current-screen accessor once with a method handle, covering the move from `Minecraft.screen` to `Minecraft.gui.screen()`.

This avoids separate maintained copies of the mod. The test-only adapters under `compat/smoke` are not included in the distributable JAR.

## Binary integration tests

After copying the canonical JAR to `build/ports`, run these sequentially. They use generated test worlds in `run-smoke`, never user saves.

```powershell
.\gradlew.bat '-Ptarget=26.1' '-PtestMinecraft=26.1' '-PbinaryUnderTest=build/ports/litematica-storages-plus-1.3.1+mc26.1-26.2.jar' -PintegrationTests -PwithModMenu runSmoke --no-daemon
.\gradlew.bat '-Ptarget=26.1' '-PtestMinecraft=26.1.1' '-PbinaryUnderTest=build/ports/litematica-storages-plus-1.3.1+mc26.1-26.2.jar' -PintegrationTests -PwithModMenu runSmoke --no-daemon
.\gradlew.bat '-Ptarget=26.1' '-PtestMinecraft=26.1.2' '-PbinaryUnderTest=build/ports/litematica-storages-plus-1.3.1+mc26.1-26.2.jar' -PintegrationTests -PwithModMenu runSmoke --no-daemon
.\gradlew.bat '-Ptarget=26.2' '-PbinaryUnderTest=build/ports/litematica-storages-plus-1.3.1+mc26.1-26.2.jar' -PintegrationTests -PwithModMenu runSmoke --no-daemon
```

The `26.2` profile supplies that client's dependencies and test driver. It is not a second published binary. `binaryUnderTest` removes development client output from the runtime classpath; the test asserts the actual production class origin matches the specified JAR.

| Minecraft | Fabric API | Litematica | MaLiLib | Mod Menu (optional) |
| --- | --- | --- | --- | --- |
| 26.1 / 26.1.1 / 26.1.2 | 0.155.3+26.1.2 | 0.27.14 | 0.28.12 | 18.0.2 |
| 26.2 | 0.161.0+26.2 | 0.28.8 | 0.29.6 | 20.0.3 |

Java 25 and Fabric Loader 0.19.5 are used by the test profiles. Dependencies were obtained from their authors' [Modrinth projects](https://modrinth.com/mod/litematica/versions). Minecraft 26.3's input backend differs; see the [Fabric 26.3 migration notes](https://fabricmc.net/2026/09/15/263.html).
