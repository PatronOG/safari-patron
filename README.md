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
- **Done HUD:** Biome completion alerts. Includes per biome or every area. (with and without Macaw) On-screen “Done” alerts are off by default.

### ✦ Sparkling Finder
- **Sparkling ESP:** Spots Sparkling critters and boxes.
- **Sparkling Waypoint:** Marks the critter with a box in the world when seen. Waypoint disappears after catching.
- **Sparkling Share:** One-click buttons to share its location in party or all chat.

### ➶ Capsule Helper
- Shows where your capsule will fly and whether it will hit a critter.

### ⚑ Party Messages
- **Warp Check:** Sends if someone missing or not in party chat.
- **Hotspot Sharing:** It shares the hotspot area assigned to you in the party chat.
- **Biome Announcements:** {Area} Done!, Everything except Macaw done!, Everything Done!

### ◈ ESP System
- **Sparkling ESP:** Spots Sparkling critters and alerts you in chat with their distance and coordinates. Marks the critter with a box in the world.
- **Critter ESP:** Boxes around critters.
- **Floor Drop ESP:** Marks floor drop spots.
- **Bee Nest ESP:** Honeybug nests in Forest.
- **Snoozle Walls:** Breakable walls in Cavern.

### ✨ Quality of Life
- **HUD Editor:** `/sp hud` lets you drag and resize every panel
- **Menu:** `/sp` opens every setting in one place, with search
- **Hideyho:** Click anywhere in screen to pick `[Sure]`

## Installing

1. Install **Fabric Loader 0.19.5 or newer** for your Minecraft version (26.3 or 26.2) using the [Fabric installer](https://fabricmc.net/use/installer/).
2. Download these mods and put them in your `mods` folder:
   - **Safari Patron**: pick the jar that matches your Minecraft version from the [Releases](../../releases) page
     - Minecraft 26.3 → `safari-patron-1.0.0.jar`
     - Minecraft 26.2 → `safari-patron-1.0.0+26.2.jar`
   - [**Fabric API**](https://modrinth.com/mod/fabric-api), for the same Minecraft version
   - [**Fabric Language Kotlin**](https://modrinth.com/mod/fabric-language-kotlin)
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
| `/sp warp` | Toggle the party warp check |
| `/sp hotspot` | Toggle Hunting Hotspot sharing |
| `/sp announce` | Toggle biome announcements |
| `/sp announce screen` | Also show announcements on screen |
| `/sp prefix <style>` | Chat prefix style: `safari`, `gold`, `emerald`, `sunset`, `aurora` |
| `/sp status` | Show your current run status |
| `/sp newrun` | Manually reset the run |

Every toggle is also available in the `/sp` menu.

## License

Safari Patron is released under the [MIT License](LICENSE).

Some parts are based on code and data from other open-source projects, which keep their own licenses. See the [`licenses`](licenses) folder for details.
