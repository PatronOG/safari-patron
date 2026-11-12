package safaripatron.data

/**
 * Oyuncunun gerçekten hangi bölgede olduğunu bulur: en yakın harita noktasına bakar.
 * 0 = orta alan / başlangıç gemisi (hiçbir bölge sayılmaz), 1..4 = Forest, Cavern, Icy, Haunted.
 * 40 bloktan uzakta nokta yoksa bilinmiyor (null).
 *
 * Nokta tablosu SkyHanni'nin Safari grafiğinden gelir; CritterMod (MIT) üzerinden alındı.
 */
object AreaMap {
    private class Node(val x: Double, val y: Double, val z: Double, val area: Int)

    private val nodes: List<Node> by lazy {
        AreaMap::class.java.getResourceAsStream("/assets/safari-patron/safari_areas.txt")
            ?.bufferedReader()?.useLines { lines ->
                lines.filter { it.isNotBlank() }.mapNotNull { line ->
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size < 4) null else Node(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3].toInt())
                }.toList()
            }.orEmpty()
    }

    fun areaAt(x: Double, y: Double, z: Double): Int? {
        var best = 40.0 * 40.0
        var area: Int? = null
        for (n in nodes) {
            val d = (x - n.x) * (x - n.x) + (y - n.y) * (y - n.y) + (z - n.z) * (z - n.z)
            if (d <= best) { best = d; area = n.area }
        }
        return area
    }

    /** Orta alanda / gemide ya da harita dışında null döner. */
    fun biomeAt(x: Double, y: Double, z: Double): Biome? =
        areaAt(x, y, z)?.takeIf { it in 1..4 }?.let { Biome.entries[it - 1] }
}
