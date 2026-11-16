package safaripatron

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component
import safaripatron.config.Config
import safaripatron.hud.HudEditScreen
import safaripatron.ui.MenuNav
import safaripatron.safari.Run
import safaripatron.safari.Safari
import safaripatron.sparkling.SparklingFinder

/** The /sp command and its subcommands. */
object Commands {
    private fun msg(text: String): net.minecraft.network.chat.MutableComponent = Brand.msg(text)
    private fun onOff(b: Boolean) = if (b) "§aenabled" else "§cdisabled"

    private fun toggle(
        name: String, label: String, get: () -> Boolean, set: (Boolean) -> Unit,
    ): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(name).executes {
            set(!get()); Config.save()
            it.source.sendFeedback(msg("$label ${onOff(get())}"))
            1
        }

    private val help = listOf(
        "§6§lSafari Patron §7— commands",
        "§e/sp §7— open the menu",
        "§e/sp settings §7— open the settings",
        "§e/sp hud §7— move and resize the panels",
        "§e/sp history §7— your saved runs",
        "§e/sp topshards §7— the most profitable shards of each biome",
        "§e/sp boxes §7— box size of each critter (ESP and Capsule Helper)",
        "§e/sp bestparty §7— the parties of your Everything PBs",
        "§e/sp capsule §7— toggle Capsule Helper",
        "§e/sp contest §7— toggle the Miria's Contest tracker",
        "§e/sp resetsession §7— reset your session profit",
        "§e/sp sessionprofit §7— show your session profit in chat",
        "§e/sp totalprofit §7— show your total Safari profit in chat",
        "§e/sp sparkling §7— toggle sparkling finder",
        "§e/sp sparkling sound §7— toggle sparkling sound",
        "§e/sp sparkling clear §7— clear the sparkling panel",
        "§e/sp esp §7— toggle critter ESP",
        "§e/sp floor §7— toggle floor drop ESP",
        "§e/sp announce §7— toggle biome announcements in party chat (Forest Done! ...)",
        "§e/sp warp §7— toggle the warp check in party chat",
        "§e/sp hotspot §7— toggle sharing your Hunting Hotspot in party chat",
        "§e/sp prefix <safari|gold|emerald|sunset|aurora> §7— chat prefix colors",
        "§e/sp status §7— show mod status",
        "§e/sp newrun §7— manually reset the run",
    )

    private fun prefixCommand(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val root = ClientCommands.literal("prefix").executes {
            it.source.sendFeedback(msg("Styles: " + Brand.Style.entries.joinToString(", ") { s -> s.id }))
            1
        }
        for (style in Brand.Style.entries) {
            root.then(ClientCommands.literal(style.id).executes {
                Config.s.prefixStyle = style.id; Config.save()
                it.source.sendFeedback(msg("Prefix style: ${style.id}"))
                1
            })
        }
        return root
    }

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val s = { Config.s }
            dispatcher.register(
                ClientCommands.literal("sp")
                    .executes { MenuNav.openMain(); 1 }
                    .then(ClientCommands.literal("settings").executes { MenuNav.openSettings(); 1 })
                    .then(ClientCommands.literal("help").executes { ctx -> help.forEach { ctx.source.sendFeedback(Component.literal(it)) }; 1 })
                    .then(ClientCommands.literal("hud").executes { MenuNav.open(HudEditScreen()); 1 })
                    .then(ClientCommands.literal("history").executes { MenuNav.open(safaripatron.ui.HistoryScreen()); 1 })
                    .then(ClientCommands.literal("boxes").executes { MenuNav.open(safaripatron.ui.BoxScreen()); 1 })
                    .then(ClientCommands.literal("topshards").executes { ctx -> topShards(ctx.source); 1 })
                    .then(ClientCommands.literal("bestparty").executes { ctx -> bestParty(ctx.source); 1 })
                    .then(toggle("capsule", "Capsule Helper", { s().capsuleTrajectory }, { s().capsuleTrajectory = it }))
                    .then(toggle("contest", "Miria's Contest", { s().contest }, { s().contest = it }))
                    .then(ClientCommands.literal("sessionprofit").executes {
                        it.source.sendFeedback(copyable(safaripatron.profit.Profit.sessionLine()))
                        safaripatron.profit.Bazaar.warning()?.let { w -> it.source.sendFeedback(msg("§c$w")) }
                        1
                    })
                    .then(ClientCommands.literal("totalprofit").executes {
                        it.source.sendFeedback(copyable(safaripatron.profit.Profit.totalLine()))
                        safaripatron.profit.Bazaar.warning()?.let { w -> it.source.sendFeedback(msg("§c$w")) }
                        1
                    })
                    .then(ClientCommands.literal("resetsession").executes {
                        safaripatron.profit.Profit.newSession()
                        it.source.sendFeedback(msg("Session profit reset."))
                        1
                    })
                    .then(toggle("sparkling", "Sparkling finder", { s().sparklingFinder }, { s().sparklingFinder = it })
                        .then(toggle("sound", "Sparkling sound", { s().sparklingSound }, { s().sparklingSound = it }))
                        .then(ClientCommands.literal("clear").executes {
                            SparklingFinder.clear()
                            it.source.sendFeedback(msg("Sparkling panel cleared"))
                            1
                        }))
                    .then(toggle("esp", "Critter ESP", { s().esp }, { s().esp = it }))
                    .then(toggle("floor", "Floor drop ESP", { s().floorDrops }, { s().floorDrops = it }))
                    .then(toggle("announce", "Biome announcements", { s().biomeAnnouncements }, { s().biomeAnnouncements = it }))
                    .then(toggle("warp", "Warp check", { s().warpCheck }, { s().warpCheck = it }))
                    .then(toggle("hotspot", "Hotspot sharing", { s().hotspotAnnounce }, { s().hotspotAnnounce = it }))
                    .then(prefixCommand())
                    // Only used by the clickable share buttons: hidden from suggestions until a share link exists,
                    // and does nothing (no error) if typed without an id.
                    .then(ClientCommands.literal("share")
                        .requires { SparklingFinder.hasShares() }
                        .executes { 1 }
                        .then(ClientCommands.literal("party").executes { 1 }
                            .then(ClientCommands.argument("id", IntegerArgumentType.integer(0)).executes {
                                SparklingFinder.share(IntegerArgumentType.getInteger(it, "id"), toParty = true); 1
                            }))
                        .then(ClientCommands.literal("all").executes { 1 }
                            .then(ClientCommands.argument("id", IntegerArgumentType.integer(0)).executes {
                                SparklingFinder.share(IntegerArgumentType.getInteger(it, "id"), toParty = false); 1
                            })))
                    .then(ClientCommands.literal("status").executes {
                        val src = it.source
                        src.sendFeedback(msg("In Safari: ${if (Safari.inSafari) "§aYes" else "§cNo"}§f · Biome: ${Safari.biome?.label ?: "-"}" +
                            " · Run: ${Run.state} · Uniques: ${Run.caught.size}/37"))
                        val caughtList = Run.sparklingsCaught.joinToString(", ") { (p, c) -> "$c ($p)" }.ifEmpty { "none" }
                        src.sendFeedback(msg("Captured Mobs (scoreboard): ${Safari.capturedMobs ?: "-"}"))
                        src.sendFeedback(msg("Sparklings caught this run: ${Run.sparklingsCaught.size} §7($caughtList)§f · Active: ${SparklingFinder.sparks.size}"))
                        1
                    })
                    .then(ClientCommands.literal("newrun").executes {
                        Run.start(); SparklingFinder.clear()
                        it.source.sendFeedback(msg("Run reset"))
                        1
                    })
            )
        }
    }

    /** Dark green strikethrough line, the mod's colour (used between blocks). */
    private const val SEPARATOR = "§2§m---------------------------------------------"

    /** /sp bestparty: the Everything except Macaw and Everything PBs with the players of those runs. */
    private fun bestParty(source: FabricClientCommandSource) {
        source.sendFeedback(bestPartyMessage(listOf(
            "Everything except Macaw Done PB" to Config.s.bestTimes["allButMacaw"],
            "Everything Done PB" to Config.s.bestTimes["all"],
        )))
    }

    /**
     * The whole block as ONE chat message (lines joined with \n), so chat mods that stack repeated
     * messages can't merge its identical lines.
     */
    fun bestPartyMessage(entries: List<Pair<String, safaripatron.config.BestTime?>>): Component {
        val out = msg("§6Best PB Run Parties")
        out.append(Component.literal("\n$SEPARATOR"))
        for ((title, best) in entries) {
            out.append(Component.literal("\n§f§l$title\n"))
            when {
                best == null || best.ms <= 0 -> out.append(Component.literal("§7No PB yet."))
                best.party.isNullOrEmpty() -> out.append(Component.literal("§fBest PB §a${Run.time(best.ms)}§f. §7(party not recorded yet)"))
                else -> {
                    out.append(Component.literal("§fBest PB §a${Run.time(best.ms)}§f. "))
                    best.party!!.forEachIndexed { i, m ->
                        if (i > 0) out.append(Component.literal("§f, "))
                        out.append(Component.literal(biomeCode(m.biome) + m.name))
                    }
                    out.append(Component.literal("§f."))
                }
            }
            out.append(Component.literal("\n$SEPARATOR"))
        }
        return out
    }

    /** Chat colour of a biome (by name); grey when unknown. */
    private fun biomeCode(name: String?) = when (name) {
        "FOREST" -> "§a"; "CAVERN" -> "§6"; "ICY" -> "§b"; "HAUNTED" -> "§5"; else -> "§7"
    }

    /** /sp topshards: a header, then one block per biome (its name, its 5 most profitable shards), as one message. */
    private fun topShards(source: FabricClientCommandSource) {
        val tops = safaripatron.data.Biome.entries.map { it to safaripatron.profit.TopShards.top(it) }
        if (tops.all { it.second.isEmpty() }) {
            source.sendFeedback(msg(safaripatron.profit.Bazaar.warning() ?: "Loading Bazaar prices… try again in a moment."))
            return
        }
        val out = msg("§6Most Profitable Shards (per Shard):")
        out.append(Component.literal("\n$SEPARATOR"))
        for ((biome, list) in tops) {
            out.append(Component.literal("\n${biomeCode(biome.name)}§l${biome.label}\n"))
            list.forEachIndexed { i, e ->
                if (i > 0) out.append(Component.literal("§7, "))
                out.append(Component.literal(safaripatron.profit.TopShards.format(e)).withStyle {
                    it.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(safaripatron.profit.TopShards.details(e))))
                })
            }
            if (list.isEmpty()) out.append(Component.literal("§8no prices yet"))
            out.append(Component.literal("\n$SEPARATOR"))
        }
        source.sendFeedback(out)
    }

    /** Prefixed line; clicking it copies the text (prefix included) to the clipboard. */
    private fun copyable(text: String): net.minecraft.network.chat.MutableComponent {
        val plain = "[✦Safari Patron✦] " + text.replace(Regex("§."), "")
        return Brand.msg(text).withStyle {
            it.withClickEvent(net.minecraft.network.chat.ClickEvent.CopyToClipboard(plain))
                .withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("Click to copy")))
        }
    }
}
