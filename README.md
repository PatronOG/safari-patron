# Safari Patron
QoL Critter Safari mod for Hypixel SkyBlock · Fabric · Minecraft 26.2 & 26.3

## Features

### 📋 Safari HUDs
- **Run Stats HUD:** Tracks your and your party's unique catch per biome and global. Includes Run Timer and Run splits.
- **Run Timer:** Starts when you entered in Critter Safari.
- **Run Splits:** Includes party member's unique catches per biome.
- **Missing Critters Hud:** Shows the remaining uncaptured unique critters in the biome.
- **Sparkling HUD:** When a Sparkling Critter is spotted, it appears on the screen. It remains there until it is caught.
- **Alert HUD:** It alerts you as to whether all players have warped into the area or someone missing.
- **Done HUD:** Biome completion alerts. Includes per biome or every area. (with and without Macaw)
- **Sparklings HUD:** Shows which sparklings you have caught in your current biome. Can list only the missing ones.
- **Total Sparklings HUD:** All 37 sparklings in four columns, shown in the middle area between the biomes.
- **Contest HUD:** Miria's Contest time left, bracket, score and ticket status. 

### ✦ Sparkling Finder
- **Sparkling ESP:** Spots Sparkling critters and boxes.
- **Sparkling Waypoint:** Marks the critter with a box in the world when seen. Waypoint disappears after catching.
- **Sparkling Share:** One-click buttons to share its location in party or all chat.
- **Auto Share:** Optionally sends every Sparkling straight to party chat.
- **Critterdex Sync:** Opening the Sparkling Critterdex in game updates your sparklings list automatically.

### ➶ Capsule Helper
- Shows where your capsule will fly and whether it will hit a critter.

### ✪ Miria's Contest
- **Contest Tracker:** Time left, bracket and score, read from the scoreboard.
- **Ticket Message:** Sends `r` (or your own text) to party chat when your Basic or First-Class ticket is earned.
- **Start Message:** Sends `!dt contest` (or your own text) to party chat when a new contest starts.
- **Incomplete Warnings:** Title and sound at 5, 3 and 1 minute left if your ticket isn't done.

### ⚑ Party Messages
- **Warp Check:** Sends if someone missing or not in party chat.
- **Hotspot Sharing:** It shares the hotspot area assigned to you in the party chat.
- **Biome Announcements:** {Area} Done!, Everything except Macaw done!, Everything Done!
- **Alert panels:** Each alert can go to party chat, your screen, or both. 

### ◈ ESP System
- **Sparkling ESP:** Spots Sparkling critters and alerts you in chat with their distance and coordinates. Marks the critter with a box in the world.
- **Critter ESP:** Boxes around critters.
- **Floor Drop ESP:** Marks floor drop spots.
- **Bee Nest ESP:** Honeybug nests in Forest.
- **Snoozle Walls:** Breakable walls in Cavern.

### ✨ Quality of Life
- **HUD Editor:** /sp hud lets you drag and resize every panel, with center guides that snap.
- **Menu:** `/sp` opens every setting in one place, with search
- **Hideyho:** With chat open, click anywhere in it to pick `[Sure]`
- **Update Checker:** Tells you in chat when a new version is out. Can be turned off on the main menu.

## Installing

1. Install **Fabric Loader 0.19.5 or newer** for your Minecraft version (26.3 or 26.2) using the [Fabric installer](https://fabricmc.net/use/installer/).
2. Download these mods and put them in your `mods` folder:
   - **Safari Patron**: pick the jar that matches your Minecraft version from the [Releases](../../releases) page
     - Minecraft 26.3 → the jar ending in `+26.3.jar`
     - Minecraft 26.2 → the jar ending in `+26.2.jar`
   - [**Fabric API**](https://modrinth.com/mod/fabric-api), for the same Minecraft version
   - [**Fabric Language Kotlin**](https://modrinth.com/mod/fabric-language-kotlin)
   - *Optional:* [**Mod Menu**](https://modrinth.com/mod/modmenu), for a settings button in the mod list
3. Launch the game, join Hypixel and type `/sp` to open the menu.

## Commands

| Command | Description |
|---|---|
| `/sp` | Open the main menu |
| `/sp settings` | Open the settings |
| `/sp hud` | Move and resize the panels |
| `/sp help` | List all commands in chat |
| `/sp sparkling` | Toggle the Sparkling Finder |
| `/sp sparkling sound` | Toggle the Sparkling sound |
| `/sp sparkling clear` | Clear the Sparkling panel |
| `/sp esp` | Toggle critter ESP |
| `/sp esp biome` | Only show ESP in your current biome |
| `/sp floor` | Toggle floor drop ESP |
| `/sp capsule` | Toggle the Capsule Helper |
| `/sp contest` | Toggle the Miria's Contest tracker |
| `/sp warp` | Toggle the warp check in party chat |
| `/sp hotspot` | Toggle Hotspot sharing in party chat |
| `/sp announce` | Toggle biome announcements in party chat |
| `/sp prefix <style>` | Chat prefix style: `safari`, `gold`, `emerald`, `sunset`, `aurora` |
| `/sp status` | Show your current run status |
| `/sp newrun` | Manually reset the run |

Every toggle is also available in the `/sp` menu.

## License

Safari Patron is licensed under the [Apache License 2.0](LICENSE). If you use or redistribute it, keep the [NOTICE](NOTICE) file.
Versions up to 1.0.1 were released under the MIT License.

Some parts are based on code and data from other open-source projects, which keep their own licenses. See the [`licenses`](licenses) folder for details.
