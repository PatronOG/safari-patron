package safaripatron.data

enum class Biome(val label: String, val color: Int) {
    FOREST("Forest", 0xFF55FF55.toInt()),
    CAVERN("Cavern", 0xFFFFAA00.toInt()),
    ICY("Icy", 0xFF55FFFF.toInt()),
    HAUNTED("Haunted", 0xFFAA00AA.toInt());

    val critters get() = Critters.inBiome(this)
}

enum class Rarity(val color: Int) {
    COMMON(0xFFFFFFFF.toInt()),
    UNCOMMON(0xFF55FF55.toInt()),
    RARE(0xFF5555FF.toInt()),
    EPIC(0xFFAA00AA.toInt()),
    LEGENDARY(0xFFFFAA00.toInt()),
}

/** espColor: ESP kutusunun rengi (Nebulune'ün varsayılan renkleri). */
data class Critter(val name: String, val biome: Biome, val rarity: Rarity, val espColor: Int)

/**
 * Critter Safari'deki 37 critter.
 * Bölge listesi ve nadirlik grupları CritterMod (MIT) ile aynı,
 * ESP renkleri Nebulune (BSD-3-Clause) varsayılanları.
 */
object Critters {
    private fun c(name: String, biome: Biome, rarity: Rarity, r: Int, g: Int, b: Int) =
        Critter(name, biome, rarity, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)

    private val F = Biome.FOREST; private val C = Biome.CAVERN; private val I = Biome.ICY; private val H = Biome.HAUNTED
    private val CO = Rarity.COMMON; private val UN = Rarity.UNCOMMON; private val RA = Rarity.RARE
    private val EP = Rarity.EPIC; private val LE = Rarity.LEGENDARY

    val all: List<Critter> = listOf(
        // FOREST (9)
        c("Foxtrot", F, CO, 240, 110, 20),
        c("Bluebird", F, UN, 20, 50, 200),
        c("Honeybug", F, UN, 240, 190, 30),
        c("Treefrog", F, UN, 110, 130, 90),
        c("Woodchucker", F, UN, 80, 70, 70),
        c("Fluffling", F, RA, 230, 230, 230),
        c("Hideonfloor", F, RA, 100, 130, 40),
        c("Parakeet", F, RA, 90, 200, 50),
        c("Macaw", F, LE, 210, 30, 30),
        // CAVERN (9)
        c("Cavernfish", C, CO, 180, 100, 30),
        c("Flitter", C, CO, 40, 90, 110),
        c("Shyworm", C, CO, 80, 160, 50),
        c("Driftling", C, UN, 150, 110, 60),
        c("Chuckwalla", C, RA, 60, 50, 40),
        c("Rockmite", C, RA, 160, 160, 160),
        c("Scrappy", C, RA, 190, 120, 130),
        c("Snoozle", C, RA, 160, 40, 40),
        c("Gemzie", C, EP, 150, 180, 240),
        // ICY (9)
        c("Strongarm", I, CO, 220, 120, 20),
        c("Tepid", I, CO, 200, 210, 200),
        c("Polaris", I, UN, 255, 255, 255),
        c("Shuddersquid", I, UN, 50, 190, 180),
        c("Billygoat", I, RA, 230, 220, 200),
        c("Mantis Shrimp", I, RA, 40, 90, 100),
        c("Nozzlenose", I, RA, 140, 150, 170),
        c("Troodon", I, RA, 120, 80, 190),
        c("Wumpa", I, LE, 100, 90, 90),
        // HAUNTED (10)
        c("Areita", H, UN, 20, 90, 90),
        c("Bloodbat", H, UN, 90, 40, 30),
        c("Duplico", H, UN, 120, 120, 120),
        c("Gazer", H, UN, 30, 50, 70),
        c("Litterbug", H, UN, 130, 50, 170),
        c("Solsnatcher", H, UN, 50, 60, 130),
        c("Gimmiegold", H, RA, 255, 210, 0),
        c("Hideonwall", H, RA, 140, 70, 140),
        c("Hideyho", H, RA, 190, 190, 190),
        c("Doomspiral", H, LE, 20, 140, 150),
    )

    const val TOTAL = 37
    const val MACAW = "Macaw"

    private val byName = all.associateBy { it.name.lowercase() }
    private val byBiome = all.groupBy { it.biome }

    fun named(name: String): Critter? = byName[name.trim().lowercase()]
    fun inBiome(biome: Biome): List<Critter> = byBiome.getValue(biome)

    /** Safari'nin merkezi x=-50, z=0; dört çeyrek dört bölge (Nebulune'deki harita). */
    fun biomeAt(x: Double, z: Double): Biome? {
        if (x < -200 || x > 100 || z < -150 || z > 150) return null
        return when {
            x >= -50 && z >= 0 -> Biome.FOREST
            x >= -50 -> Biome.HAUNTED
            z >= 0 -> Biome.CAVERN
            else -> Biome.ICY
        }
    }
}
