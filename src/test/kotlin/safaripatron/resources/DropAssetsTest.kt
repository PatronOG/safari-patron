package safaripatron.resources

import javax.imageio.ImageIO
import kotlin.test.*

class DropAssetsTest {
    @Test fun allResourceAndUnknownIconsDecodeFromBundledAssets() {
        val paths = DropKind.entries.map { it.texture } + "textures/drops/unknown.png"
        for (path in paths) {
            val stream = javaClass.getResourceAsStream("/assets/safari-patron/$path")
            assertNotNull(stream, path)
            val image = stream.use { ImageIO.read(it) }
            assertNotNull(image, path)
            assertTrue(image.width in 8..128 && image.height in 8..128, path)
        }
    }
    @Test fun moundCatalogContainsDistinctCavernSites() {
        val stream = javaClass.getResourceAsStream("/assets/safari-patron/mound_sites.txt")
        assertNotNull(stream)
        val sites = stream.bufferedReader().useLines { lines -> lines.filter(String::isNotBlank).map { it.split(' ').map(String::toInt) }.toList() }
        assertEquals(32, sites.size); assertEquals(sites.size, sites.distinct().size)
        assertTrue(sites.all { it.size == 3 && it[0] < -50 && it[1] <= 65 && it[2] >= 0 })
    }
}
