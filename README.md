<p align="center">
  <img src="docs/images/banner.png" alt="Safari Patron">
</p>

<p align="center">
  <b>Everything you need for Critter Safari, in one mod.</b><br>
  Find sparklings, track your runs and profit, coordinate your party and chase achievements.
</p>

<p align="center">
  <a href="https://github.com/PatronOG/safari-patron/releases/latest"><img src="https://img.shields.io/github/v/release/PatronOG/safari-patron?label=download&color=c9a43a" alt="Download"></a>
  <a href="https://www.minecraft.net"><img src="https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-4f7a28" alt="Minecraft 26.2 | 26.3"></a>
  <a href="https://fabricmc.net/use/installer/"><img src="https://img.shields.io/badge/Fabric-0.19.5%2B-dbd0b4" alt="Fabric 0.19.5+"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-3a4a30" alt="License: Apache-2.0"></a>
</p>

<p align="center">
  <b><a href="#features">Features</a></b> ·
  <b><a href="#installing">Installing</a></b> ·
  <b><a href="#commands">Commands</a></b>
</p>

<p align="center">
  <img src="docs/images/showcase.gif" alt="Safari Patron showcase" width="800">
</p>

## Features

<table>
<tr>
<td width="33%" valign="top">

### ✦ Sparkling Finder
Spots Sparkling critters and marks them in the world until caught.<br>
Share their location in one click.<br>
Your Critterdex sparklings, synced from the game.

</td>
<td width="33%" valign="top">

### 🎯 Hotspot Assignment
Reads everyone's hotspot from party chat.<br>
Places each player in one message.<br>
Fair on clashes, works with other mods.

</td>
<td width="33%" valign="top">

### ⚑ Party Messages
Warp check, hotspot sharing and biome announcements.<br>
Completion times with personal bests.<br>
Each message can be turned off.

</td>
</tr>
<tr>
<td width="33%" valign="top">

### 📋 Safari HUDs
Your and your party's uniques per biome.<br>
Run timer, splits and missing critters.<br>
Every panel can be moved and resized.

</td>
<td width="33%" valign="top">

### ☰ Run History & Stats
Up to 20,000 runs with time, loot and party.<br>
Run time and profit graphs, clear stats and records.<br>
Filters for sparkling, PB and Dye runs.

</td>
<td width="33%" valign="top">

### ★ Achievements
38 achievements across 9 rarities.<br>
From Common to Impossible, 2,400 points.<br>
Your past runs count too.

</td>
</tr>
<tr>
<td width="33%" valign="top">

### ♦ Profit Tracker
Session and all-time profit, item by item.<br>
Bazaar and auction house prices.<br>
Top shards of each biome.

</td>
<td width="33%" valign="top">

### ✪ Miria's Contest
Time left, bracket, score and ticket.<br>
Warnings before the contest ends.<br>
Honeyhive waypoints and refill timers.

</td>
<td width="33%" valign="top">

### ◈ ESP
Critters and floor drops through walls, with what's inside each drop.<br>
Bee nests and Snoozle walls.<br>
Clear water in the Safari.

</td>
</tr>
<tr>
<td width="33%" valign="top">

### ➶ Helpers
Capsule path and hit check.<br>
Doomspiral timer and Wumpa skip spots.<br>
Gem, Scrappy food and Hideyho helpers.<br>
Rockmite, incense and Safari entry helpers.

</td>
<td width="33%" valign="top">

### ⚙ Main Menu
Every feature and setting in one place.<br>
Search through every option.<br>
Open with `/sp`.

</td>
<td width="33%" valign="top">

### ✨ Quality of Life
Pick Hideyho's `[Sure]` with one click in chat.<br>
Update checker.<br>
Reset buttons ask before deleting.

</td>
</tr>
</table>

<details>
<summary><b>All features in detail</b></summary>

### 📋 Safari HUDs
- **Run Stats HUD:** Tracks your and your party's unique catch per biome and global. Includes Run Timer and Run splits.
- **Run Timer:** Starts the moment the Safari loads. The panel, party chat, personal bests and Run History all use this one clock.
- **Run Splits:** Includes party member's unique catches per biome.
- **Missing Critters Hud:** Shows the remaining uncaptured unique critters in the biome.
- **Critter List Icons:** Critter pictures next to the names in the missing critters and sparkling lists.
- **Sparkling HUD:** When a Sparkling Critter is spotted, it appears on the screen. It remains there until it is caught.
- **Alert HUD:** It alerts you as to whether all players have warped into the area or someone missing.
- **Done HUD:** Biome completion alerts. Includes per biome or every area. (with and without Macaw)
- **Sparklings HUD:** Shows which sparklings you have caught in your current biome. Can list only the missing ones.
- **Total Sparklings HUD:** All 37 sparklings in four columns, shown in the middle area between the biomes.
- **Contest HUD:** Miria's Contest time left, bracket, score and ticket status.
- **Achievement Panel:** A box on screen when you unlock an achievement. Choose the lowest rarity it shows, or turn it off.
- **Completion Times:** Biome announcements show your time and personal best, to a tenth of a second, e.g. `Forest Done! Completed in 2:31.4 (PB 2:20.8 by IGN)`.

### ✦ Sparkling Finder
- **Sparkling ESP:** Spots Sparkling critters and boxes.
- **Sparkling Waypoint:** Marks the critter with a box in the world when seen. Waypoint disappears after catching.
- **Sparkling Share:** One-click buttons to share its location in party or all chat.
- **Auto Share:** Optionally sends every Sparkling straight to party chat.
- **Critterdex Sync:** Opening the Sparkling Critterdex in game updates your sparklings list automatically.
- **Last Sparkling:** When a Sparkling is caught, tells you how many runs and how long ago the previous one was.

### ➶ Helpers
- **Capsule Helper:** Shows where your capsule will fly and whether it will hit a critter.
- **Masterful Target Filter:** Off by default. With a Masterful Critter Capsule in hand, a right click only throws when the Capsule Helper's flight path hits Doomspiral, Wumpa, Macaw, Gemzie or Scrappy. Other targets show in red.
- **Hitbox Editor:** Every critter has its own box size, measured in game, used for the Capsule Helper and ESP. Change any of them with `/sp boxes`. Use the default settings unless you know what you're doing.
- **Doomspiral Timer:** Marks where to stand and where to aim, then counts down to the perfect Masterful Capsule throw while Doomspiral rises.
- **Wumpa Skip Helper:** Marks where to stand and the two points to aim at: a Critter Capsule first, then the Masterful Capsule with a countdown. Set the delay between them with **Masterful delay** (1.2 s by default).
- **Cavern Gem Helper:** Picks the matching Lime, Purple or Orange Gem in your hotbar while you look at its podium. You place it yourself, or let it place the gem for you after a short delay.
- **Scrappy Food:** Shows what each Scrappy eats before you feed it, worked out from the Hunter nearby (Luigi: Flavor-packed Fish, Jasper: Lush Lily Pad, Reeves: Wholesale Wheat). Once two foods are known, the third is marked with `*`.
- **Scrappy Food Priority:** Scrappies whose food you carry are highlighted in green, the others stay in gray. They disappear once your party has caught one.
- **Hideyho Auto-Sure:** Off by default. Accepts Hideyho's challenge in Haunted by clicking `[Sure]` for you.
- **Rockmite Hold Click:** Off by default. Hold left click on a Rockmite mound in Cavern and it keeps hitting until the mound breaks. Let go, look away or open a menu to stop.
- **Incense Candle Targeting:** With Soothing Incense in hand in Haunted, the whole red candle block counts as the candle, so it's easier to aim at.
- **Safari Entry Guard:** Stops you from entering the Safari at the Manager, with a ticket or from the entry menu until your whole party is there and the critters have loaded. Hold Shift to enter anyway.
- **Helper Sounds:** A bell when it is time to throw. Each helper can be turned off on its own.

> Moving, aiming and throwing are always up to you. The Cavern Gem Helper, Hideyho Auto-Sure and Rockmite Hold Click can act for you; each has its own settings under Helpers.

### ✪ Miria's Contest
- **Contest Tracker:** Time left, bracket and score, read from the scoreboard.
- **Ticket Message:** Sends `Ticket earned!`, `r` or your own text to party chat when your Basic or First-Class ticket is earned.
- **Start Message:** Sends `New Miria's Contest started.`, `!dt contest` or your own text to party chat when a new contest starts.
- **Incomplete Warnings:** Title and sound if your ticket isn't done. 5 minutes left by default, 3 and 1 minute can be turned on too.
- **Honeyhives:** During a contest every full Honeyhive in Torrhus Canyon gets a waypoint. A looted hive shows its one-hour refill timer, and the contest panel shows how many are ready.

### ♦ Profit Tracker
- **Session Profit:** Shard, Safari Essence, Rainbow Feather and Emerald Dye profits, total, run count, average run time and profit per hour.
- **Session Clock:** Starts when you arrive in the Safari and pauses when you're away from it.
- **Total Profit Tracker:** All-time profit item by item, in rarity colors, with total runs and total profit. Shows at the Critter Safari Entrance by default, or everywhere outside the Safari. Never inside the Safari.
- **Emerald Dye:** Your own Emerald Dyes count too, at the Auction House's lowest BIN price.
- **Top Shards:** `/sp topshards` lists each biome's 5 most profitable shards in chat.
- **Scroll Support:** Choose how many items it shows (5, 10, 15 or 20); scroll with your mouse wheel over the panel while an inventory is open.
- **Bazaar Prices:** Choose Instant Sell or Sell Offer prices. Warns you when prices can't be loaded instead of showing 0. A run saved while a price was missing is worked out again once it's in.
- **Safe Data:** Settings, profit, run history, achievements and hitbox sizes are saved in their own folder, `config/safari-patron/`, and kept between updates.

### ☰ Run History
- **Every Run Saved:** Time, uniques (yours and the party's), catches, profit and loot of up to 20,000 runs, saved in small files so the game never stutters.
- **Run Details:** Biome completion times, profit at both Instant Sell and Sell Offer prices, every party member's uniques in the color of the biome they did, sparklings and who caught them, and the loot.
- **Filters and Sorting:** Show only sparkling runs, record runs, Emerald Dye runs, today or this week; sort by newest, fastest or most profitable. Switch between Instant Sell and Sell Offer values at the top.
- **Overview:** Total runs, time played, total profit, fastest run and your sparkling rate at a glance.
- **Stats:** Run time and profit graphs with your all-time average (hover a profit bar to see that run), your last 10 runs against your average, clear stats per biome, sparkling stats, records and the players you play with most. Choose all time, this week, today or the last 50 runs.
- **Achievements:** 38 achievements across 9 rarities, from Common to Impossible, with points, progress bars and rarity filters. Your past runs count too. Coin achievements count at Sell Offer prices, whatever your price setting.
- Open it with `/sp history` or from the main menu.

### ⚑ Party Messages
- **Warp Check:** Sends `[✦SP✦] 4/4 Players in Safari.` or `[✦SP✦] Someone missing!` in party chat within a couple of seconds of arriving. Only players who really reached the new Safari count, so someone kicked on the way shows as missing.
- **Hotspot Sharing:** It shares the hotspot area assigned to you in the party chat.
- **Hotspot Assignment:** One player in the party turns it on. It reads everyone's hotspot from party chat, whether it comes from Safari Patron, another mod or someone typing `c`, `cave` or `my hotspot is icy`, and then sends where each player goes, e.g. `[✦SP✦] Alex: Cavern ✦ | PatronOG: Icy ✦ | Steve: Forest`. On a clash, whoever lost the last one goes first. Anyone who stays quiet gets a free biome after a wait you can set.
- **Biome Announcements:** {Area} Done!, Everything except Macaw done!, Everything Done!
- **Best Parties:** Your Everything PBs remember who was in your party. `/sp bestparty` shows them in chat, each player in the color of the biome they started in, with times to a tenth of a second.
- **Alert panels:** Each alert can go to party chat, your screen, or both. 

### ◈ ESP System
- **Sparkling ESP:** Spots Sparkling critters and alerts you in chat with their distance and coordinates. Marks the critter with a box in the world.
- **Critter ESP:** Boxes around critters.
- **Floor Drop ESP:** Marks floor drop spots.
- **Floor Drop Holograms:** The item each drop most likely holds floats above it: gems, bird food, incense, coins or Icebreakers. A red drop means its contents aren't worked out yet.
- **Needed Resources:** Drops you still need for the run are purple, the rest green. Choose when each one turns green, e.g. gems once you have that color, incense at 4. Optionally, in Forest only bird food is shown.
- **Bee Nest ESP:** Honeybug nests in Forest. A nest goes away once you hit it.
- **Snoozle Walls:** Breakable walls in Cavern.
- **Clear Water:** See through the water in the Safari, with adjustable opacity.

### ✨ Quality of Life
- **Main Menu:** `/sp` opens a menu with every feature, its settings, and your Run History, Stats and Achievements.
- **Settings:** Every setting in one place, with search.
- **HUD Editor:** /sp hud lets you drag and resize every panel, with center guides that snap.
- **Hideyho:** With chat open, click anywhere in it to pick `[Sure]`
- **Update Checker:** Tells you in chat when a new version is out. Can be turned off on the main menu.
- **Safe Resets:** Reset buttons ask for a second click before anything is deleted.

</details>

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
| `/sp history` | Open your run history, stats and achievements |
| `/sp topshards` | Show each biome's most profitable shards in chat (click a biome to copy it) |
| `/sp bestparty` | Show the parties of your Everything PBs in chat (click one to copy it) |
| `/sp help` | List all commands in chat |
| `/sp boxes` | Open the hitbox editor |
| `/sp sparkling` | Toggle the Sparkling Finder |
| `/sp sparkling sound` | Toggle the Sparkling sound |
| `/sp sparkling clear` | Clear the Sparkling Finder panel |
| `/sp esp` | Toggle critter ESP |
| `/sp floor` | Toggle floor drop ESP |
| `/sp capsule` | Toggle the Capsule Helper |
| `/sp contest` | Toggle the Miria's Contest tracker |
| `/sp sessionprofit` | Show your session profit in chat (click to copy) |
| `/sp totalprofit` | Show your all-time Safari profit in chat (click to copy) |
| `/sp resetsession` | Start a new profit session |
| `/sp warp` | Toggle the warp check in party chat |
| `/sp hotspot` | Toggle Hotspot sharing in party chat |
| `/sp announce` | Toggle biome announcements in party chat |
| `/sp prefix <style>` | Chat prefix style: `safari`, `gold`, `emerald`, `sunset`, `aurora` |
| `/sp status` | Show your current run status |
| `/sp newrun` | Manually reset the run |

Every toggle is also available in the `/sp` menu.

## Use at Your Own Risk

Safari Patron is a client-side mod. Most features only show information on your screen, and it only sends the party chat messages you turn on. A few optional helpers act in the game for you: the Cavern Gem Helper picks or places gems, the Masterful Target Filter can hold back a throw, Hideyho Auto-Sure clicks for you and Rockmite Hold Click keeps attacking while you hold the button. Some features, like ESP, Floor Drop Holograms, the Sparkling Finder and the Capsule Helper, show information the game doesn't, and Hypixel's rules on mods can change at any time.

If something needs to be changed or fixed, [open an issue](https://github.com/PatronOG/safari-patron/issues) and we'll fix it as soon as we can.

## License

Safari Patron is released under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for attribution.

Some parts are based on code and data from other open-source projects, which keep their own licenses. See the [`licenses`](licenses) folder for details.
