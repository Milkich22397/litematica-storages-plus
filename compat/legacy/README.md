# Minecraft 1.21.11 port

This isolated Loom-remap project builds the maintained implementation in `../../src` for Minecraft 1.21.11. Generated adaptations live in `../../build/compat-1.21.11/generated`; never edit them directly. The 26.x builds remain separate.

Run from the project root with JDK 25 for Gradle; the mod targets Java 21:

```powershell
.\gradlew.bat -p compat/legacy '-Ptarget=1.21.11' build --no-daemon
.\gradlew.bat -p compat/legacy '-Ptarget=1.21.11' -PintegrationTests -PwithModMenu productionSmoke --no-daemon
```

Install the normal remapped JAR from `build/compat-1.21.11/libs`, never a dev, sources, or testmod JAR. This obfuscated Minecraft version requires intermediary remapping.

Tested dependencies: Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Litematica 0.26.16, MaLiLib 0.27.20, optional Mod Menu 17.0.1. Dependencies under `../../libs/compat` were downloaded from their authors' Modrinth releases and checked against SHA-512 metadata. See `../1.21.11.properties`.

The adaptation covers GUI render names and descriptors, keyboard registration, item-container streams, current-screen access, and MaLiLib world-render arguments. Direct Minecraft references are remapped by Loom. Test-only helpers adapt cursor reflection, native HUD arguments and disconnect ordering; none are shipped.

`productionSmoke` uses a Java 21 toolchain, a separately remapped test mod and the ordinary intermediary client. The first assertion verifies that product classes come from the exact distributable. `-PbinaryUnderTest=build/ports/<file>.jar` tests a frozen artifact. Run game processes sequentially because they share generated worlds and ledgers under `../../run-smoke`. Never use user saves. Deliberate GZIP and blocked-temp-file errors test recovery; judge the final result. See `../../COMPATIBILITY-1.21.11.md`.
