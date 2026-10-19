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

An optional resource-source breakdown shows inventory, ender chest and selected storage totals. It is disabled by default. Shulker-box comparisons show a compact summary; hold Shift for internal slot differences and use Shift + mouse wheel to browse longer comparisons.

Container comparisons use individual slots. The old whole-container comparison mode and F7 shortcut have been removed.

## Supported containers

Chests, trapped chests, barrels, shulker boxes, hoppers, dispensers, droppers, furnaces, blast furnaces, smokers, and brewing stands.

Ender chests support resource tracking; their contents are not written into schematics.

## How it works

The addon remembers contents observed when you open a container. It does not scan unopened containers or read changes inside closed storages remotely. Open a tracked container again to refresh its saved contents.

Schematic recording and resource tracking are independent. You can use tracked resources while schematic recording is disabled.

## Requirements

- **Java 25**
- Minecraft Java Edition **26.3**
- **Fabric Loader**
- **Fabric API**
- **Litematica**
- **MaLiLib**

**Mod Menu** is optional. Tested alongside **Chest Tracker 2.8.4+26.3**.

Install the addon and its required dependencies in your client’s `mods` folder. No server-side installation is required.

## Settings and compatibility

Press **F10** to access addon settings, resource accounting, tracking icon options, and saved-data controls.

The addon changes resource counts displayed in the Material List and Info HUD. It does not change which items are physically available to Easy Place.

English, modern Russian and pre-reform Russian (`rpr`) translations are included.

## Building and release notes

See [BUILDING.md](BUILDING.md) for dependencies and build instructions, and [CHANGELOG-1.1.0.md](CHANGELOG-1.1.0.md) for the 1.1.0 release notes.
