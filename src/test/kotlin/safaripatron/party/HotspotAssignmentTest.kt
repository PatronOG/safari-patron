package safaripatron.party

import safaripatron.data.Biome
import kotlin.test.*

class HotspotAssignmentTest {
    private val post = "[✦SP✦] Alex: Cavern ✦ | Steve: Icy | Zoe: Forest ✦ | Max: Haunted"

    @Test fun findsWhereEachPlayerGoes() {
        assertEquals(Biome.CAVERN, HotspotAssign.assignedTo(post, "Alex"))
        assertEquals(Biome.ICY, HotspotAssign.assignedTo(post, "steve"))
        assertEquals(Biome.FOREST, HotspotAssign.assignedTo(post, "Zoe"))
    }

    @Test fun playersNotInTheMessageGetNothing() {
        assertNull(HotspotAssign.assignedTo(post, "Sam"))
    }

    @Test fun hotspotShareAndOtherMessagesAreNotAssignments() {
        assertNull(HotspotAssign.assignedTo("[✦SP✦] Hotspot: Icy", "Hotspot"))
        assertNull(HotspotAssign.assignedTo("Alex: Cavern | Steve: Icy", "Alex"))
        assertNull(HotspotAssign.assignedTo("[✦SP✦] Alex: Cavern | Steve: somewhere", "Alex"))
    }
}
