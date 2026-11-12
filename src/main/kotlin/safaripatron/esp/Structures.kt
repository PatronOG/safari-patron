package safaripatron.esp

import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import safaripatron.config.Config
import safaripatron.data.Biome
import safaripatron.render.WorldRender
import safaripatron.safari.Run
import safaripatron.safari.Safari

/**
 * Sabit yapılar için waypoint'ler:
 *  - Forest: Honeybug'ların çıktığı arı yuvaları (BEE_NEST). Vurduğun yuva o run için listeden çıkar.
 *  - Cavern: Snoozle'ların arkasında saklandığı kırılabilir duvarlar (blok hâlâ duruyorsa gösterilir).
 * İlgili critter parti tarafından yakalanınca işaretler kaybolur.
 * Konumlar CritterMod (MIT) verisinden.
 */
object Structures {
    private const val NEST_COLOR = 0xFFFFC800.toInt()
    private const val WALL_COLOR = 0xFFFF7F50.toInt()
    private const val RANGE_SQ = 200.0 * 200.0

    private val beeNests = listOf(
        BlockPos(-17, 68, 41), BlockPos(-22, 68, 39), BlockPos(25, 70, 41), BlockPos(23, 70, 46),
        BlockPos(-1, 81, 60), BlockPos(-9, 81, 58), BlockPos(-13, 103, 21), BlockPos(-11, 103, 13),
        BlockPos(-1, 84, 9), BlockPos(20, 88, 33), BlockPos(15, 88, 31), BlockPos(18, 114, 79),
        BlockPos(16, 114, 87),
    )
    private val snoozleWalls = listOf(
        BlockPos(-126, 39, 74), BlockPos(-114, 39, 87), BlockPos(-70, 39, 68),
        BlockPos(-96, 40, 17), BlockPos(-95, 40, 42),
    )

    /** Bu run'da vurduğun yuvalar. */
    private val punched = HashSet<BlockPos>()
    private var runMarker: Long = -1

    fun init() {
        AttackBlockCallback.EVENT.register { _, level, _, pos, _ ->
            if (level.isClientSide && Safari.inSafari && pos in beeNests) punched.add(pos.immutable())
            InteractionResult.PASS
        }
    }

    fun collect(client: Minecraft, out: WorldRender.Batch) {
        if (!Safari.inSafari || !Run.running) return
        val level = client.level ?: return
        val cam = client.player?.position() ?: return
        if (Run.startStamp != runMarker) { runMarker = Run.startStamp; punched.clear() }

        when (Safari.biome) {
            Biome.FOREST -> if (Config.s.beeNests && "Honeybug" !in Run.caught) {
                for (pos in beeNests) {
                    if (pos in punched || pos.distToCenterSqr(cam) > RANGE_SQ) continue
                    if (level.isLoaded(pos) && !level.getBlockState(pos).`is`(Blocks.BEE_NEST)) continue
                    out.box(AABB(pos).inflate(0.005), NEST_COLOR)
                }
            }
            Biome.CAVERN -> if (Config.s.snoozleWalls && "Snoozle" !in Run.caught) {
                for (pos in snoozleWalls) {
                    if (pos.distToCenterSqr(cam) > RANGE_SQ) continue
                    if (level.isLoaded(pos) && level.getBlockState(pos).isAir) continue
                    out.box(AABB(pos).inflate(0.005), WALL_COLOR)
                }
            }
            else -> {}
        }
    }
}
