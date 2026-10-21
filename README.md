# Litematica Storages+

**Save container contents in schematics and include stored resources in Litematica’s material counts.**

Litematica Storages+ is a client-side Fabric addon for Litematica. It helps you preserve the contents of containers in your schematics and track building materials stored around your world.

## Features

### Container contents in schematics
Record the contents of containers you open and save those observations into your schematics. Preview expected contents, compare them with the actual container, and view a separate list of items required inside schematic containers.

### Resources from tracked storages
Mark a container using the icon in its title bar. Switch between **Inventory only** and **Inventory + storages** to include tracked resources in both the Material List and Info HUD.

Saved contents remain available after leaving the area, unloading chunks, or restarting the game. Double chests count as one storage, and tracked ender chest contents are associated with your player.

### Clear slot comparisons
Compare schematic and actual contents using simple colors:

- **Blue:** a required item is missing, shown as a translucent preview.
- **Red:** the slot contains the wrong item.
- **Yellow:** the item matches, but its quantity differs.
- **Pink:** the slot contains an extra item.
- Matching and empty slots keep their normal appearance.

### Movable tracking icon
Click the icon to toggle tracking. Hold and drag it to change its position without opening settings. Positions are saved for each supported menu type.

## Storage organization and search

Browse tracked storages, search their saved contents (including items inside shulker boxes), edit names, and view coordinates and dimensions. Use **Locate** to highlight a selected container through walls for 15 seconds with smooth fade-in and fade-out. Opening the located container dismisses its outline; either half works for a double chest. Locating works in the current dimension and does not require Chest Tracker.

Organize storages into persistent groups and choose which group supplies resources to both the Material List and Info HUD. Bind a group to an individual schematic file for separate resource sources per schematic. These bindings do not reserve items.

Collapse group headings or hold the left mouse button on a storage and drag it to another group. Display filtering and the group used for resource counts are independent. In an open tracked container, click its title to edit its local name: Enter saves, Escape cancels the edit, and clicking elsewhere or closing the menu saves it.

An optional resource-source breakdown shows inventory, ender chest and selected storage totals. It is disabled by default. Shulker-box comparisons show a compact summary; hold Shift for internal slot differences and use Shift + mouse wheel to browse longer comparisons.

Container comparisons use individual slots. The old whole-container comparison mode and F7 shortcut have been removed.

## Supported containers

Chests, trapped chests, barrels, shulker boxes, hoppers, dispensers, droppers, furnaces, blast furnaces, smokers, and brewing stands.

Ender chests support resource tracking; their contents are not written into schematics.

## How it works

The addon remembers contents observed when you open a container. It does not scan unopened containers or read changes inside closed storages remotely. Open a tracked container again to refresh its saved contents.

Schematic recording and resource tracking are independent. You can use tracked resources while schematic recording is disabled.

## Requirements

- Minecraft Java Edition **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, or 26.3**
- **Java 21 or later** for 1.21.11; **Java 25 or later** for 26.x
- **Fabric Loader**
- **Fabric API**
- **Litematica**
- **MaLiLib**

**Mod Menu** is optional. Tested alongside **Chest Tracker 2.8.4+26.3**.

Choose the artifact matching your Minecraft version and install only one copy of the addon:

| Minecraft | Addon artifact |
| --- | --- |
| 1.21.11 | `litematica-storages-plus-1.3.1+mc1.21.11.jar` |
| 26.1 / 26.1.1 / 26.1.2 / 26.2 | `litematica-storages-plus-1.3.1+mc26.1-26.2.jar` (one shared file) |
| 26.3 | `litematica-storages-plus-1.3.1+mc26.3.jar` |

Fabric API, Litematica and MaLiLib must match the game version. See [BUILDING.md](BUILDING.md) for the tested dependency versions. The Chest Tracker compatibility run above applies to 26.3; it is not a compatibility claim for every older Chest Tracker release.

Install the addon and its required dependencies in your client’s `mods` folder. No server-side installation is required.

## Settings and compatibility

Press **F10** to access addon settings, resource accounting, tracking icon options, and saved-data controls.

The window has **Storages**, **Preferences**, and **Data management** tabs. The sidebar provides contextual details, search, recording and overlay switches, Reset, Cancel, and Done. Search finds storage names, groups, coordinates and saved items, or settings by name and description. Destructive data operations require confirmation.

The addon changes resource counts displayed in the Material List and Info HUD. It does not change which items are physically available to Easy Place.

English, modern Russian and pre-reform Russian (`rpr`) translations are included.

## Building and release notes

The repository maintains one shared implementation in `src/`. Version profiles and API adaptations live in `compat/`; generated sources stay under ignored build directories. Separate copies of the entire mod are not required for each Minecraft version.

See [BUILDING.md](BUILDING.md) for dependencies and build instructions, and [CHANGELOG-1.1.0.md](CHANGELOG-1.1.0.md) for the 1.1.0 release notes.
