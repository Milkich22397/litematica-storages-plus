# Building Litematica Storages+

Use JDK 25 and the included Gradle wrapper. The project targets Minecraft 26.3 and Fabric.

The current build uses local dependency files. Download these dependencies from their official project releases and place them in `libs/` (dependency binaries are not part of this source repository):

- `fabric-api-0.163.0+26.3.jar`
- `litematica-fabric-26.3-0.29.1.jar`
- `malilib-fabric-26.3-0.30.2.jar`
- `modmenu-21.0.0.jar` (needed to compile the optional integration)

Extract the nested module JARs from `META-INF/jars/` inside the Fabric API JAR into `libs/fabric-api-modules-0.163.0+26.3/`. Keep their filenames.

Build on Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS, use `bash ./gradlew build`.

The installable JAR and sources JAR are written to `build/libs/`. The regular JAR is the file to install in Minecraft; the sources JAR is for developers. Dependencies are required separately in the client.

`build` also runs the pure logic checks. Minecraft integration checks use generated test worlds under `run-smoke/`:

```powershell
.\gradlew.bat build -PintegrationTests -PwithModMenu runSmoke
```

For the optional Chest Tracker compatibility run, put Chest Tracker 2.8.4+26.3 and YACL 3.9.7 for Fabric in `libs/integration/`, and add `-PwithChestTracker`. Run game checks sequentially. Never copy personal saves into the test directory. Recovery tests deliberately exercise corrupt-file and blocked-write cases; inspect the final result in `run-smoke/smoke-result.txt`.

Generated worlds, caches, logs, third-party dependencies and compiled artifacts should not be committed.
