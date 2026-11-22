// CritterIconAssetsTest.kt
// Module: Complete and decodable bundled sprite coverage for all Safari species
// Build: Kotlin 2.4.20 / JVM 25 / JUnit 4

package safaripatron.hud

import safaripatron.data.Critters
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CritterIconAssetsTest {
    @Test fun everyListedCritterHasADecodablePngSprite() {
        assertEquals(37, Critters.all.size)
        for (critter in Critters.all) {
            val name = critter.name.lowercase().replace(' ', '_')
            val stream = assertNotNull(javaClass.getResourceAsStream("/assets/safari-patron/textures/critters/$name.png"), critter.name)
            val image = stream.use { ImageIO.read(it) }
            assertNotNull(image, critter.name)
            assertTrue(image.width in 1..128 && image.height in 1..128, critter.name)
        }
    }
}
