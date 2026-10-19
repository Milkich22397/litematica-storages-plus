# Litematica Storages+ 1.1.0 — Minecraft 26.3

- Added a tracked-storage browser with item search, custom names, coordinates and dimension labels. Searches also include items inside saved shulker boxes.
- Added a Locate action: highlight the selected container through walls for 15 seconds, with smooth fade-in and fade-out. Opening the located container dismisses the highlight, including either half of a double chest. Requires being in the same dimension; Chest Tracker is optional.
- Added persistent storage groups. Count all storages, ungrouped storages, or one selected group in both the Material List and Info HUD.
- Added group bindings to individual schematic files. Different schematics can use different resource sources without changing the global selection. Bindings do not reserve items.
- Added an optional source breakdown tooltip: player inventory, ender chest and selected storages. Disabled by default; enable it in the resource settings.
- Added compact comparison of shulker-box contents against the schematic. Hold Shift for internal slot differences and use Shift + mouse wheel to browse longer comparisons.
- Added pre-reform Russian localization (`rpr`), alongside English and modern Russian.
- Simplified container comparisons to slot-by-slot only. Removed the whole-container mode and its F7 shortcut; existing settings remain compatible.
- Improved resource-counting performance with cached HUD totals and progress, less repeated decoding of nested items, and fewer redundant disk writes.
- Kept existing saved observations and tracking-icon positions. New organization data is stored in the same atomic ledger with backup recovery.

Client-side Fabric addon. Requires Java 25, Minecraft 26.3, Litematica, MaLiLib and Fabric API. Install the regular JAR in your client's mods folder, replacing the previous version.
