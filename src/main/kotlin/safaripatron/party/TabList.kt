package safaripatron.party

import java.util.UUID

/**
 * Oyuncular, bu sunucuya gelince tab list'e eklenenler. Hypixel sunucu değiştirirken önceki sunucunun
 * oyuncularını listede bırakabiliyor; onları "burada" saymamak için warp kontrolü buna bakar.
 * [TabListMixin][safaripatron.mixin.TabListMixin] doldurur.
 */
object TabList {
    private val fresh = HashSet<UUID>()

    /** false until the hook has seen a server switch (then the warp check falls back to the whole tab list). */
    var active = false
        private set

    fun onServerSwitch() {
        fresh.clear()
        active = true
    }

    fun onAdded(ids: Collection<UUID>) { fresh.addAll(ids) }

    fun onRemoved(ids: Collection<UUID>) { fresh.removeAll(ids.toSet()) }

    /** Players added since the last server switch. */
    fun players(): Set<UUID> = fresh
}
