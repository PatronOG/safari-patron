package safaripatron.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import safaripatron.SafariPatron
import safaripatron.capsule.Trajectory
import safaripatron.esp.Esp
import safaripatron.esp.Structures
import safaripatron.sparkling.SparklingFinder

/**
 * Dünyaya çizilen her şey (ESP kutuları, sparkling kutuları, kapsül yörüngesi) tek geçişte çizilir.
 * Minecraft 26.3 render API'si.
 * Çizgiler duvarların arkasından da görünür.
 */
object WorldRender {
    private const val LINE_WIDTH = 2f

    private lateinit var lines: RenderType

    fun init() {
        val builder = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(SafariPatron.MOD_ID, "pipeline/lines"))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
        val pipeline = RenderPipelines.register(builder.build())
        val setup = RenderSetup.builder(pipeline).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
        lines = RenderType.create("${SafariPatron.MOD_ID}:lines", setup.createRenderSetup())

        LevelRenderEvents.COLLECT_SUBMITS.register(::render)
    }

    /** Bir karede çizilecek çizgiler; koordinatlar kameraya göre. */
    class Batch(private val cam: Vec3) {
        val segments = ArrayList<Seg>()
        val labels = ArrayList<Label>()
        class Label(val text: String, val pos: Vec3, val color: Int, val bold: Boolean, val line: Int)

        /** Dünyada, her zaman kameraya dönük ve duvar arkasından görünen yazı. line = alt alta satır sırası. */
        fun label(text: String, pos: Vec3, color: Int, bold: Boolean = false, line: Int = 0) {
            labels.add(Label(text, pos, color, bold, line))
        }
        class Seg(val ax: Float, val ay: Float, val az: Float, val bx: Float, val by: Float, val bz: Float, val color: Int)

        fun line(a: Vec3, b: Vec3, color: Int) {
            val ax = (a.x - cam.x).toFloat(); val ay = (a.y - cam.y).toFloat(); val az = (a.z - cam.z).toFloat()
            val bx = (b.x - cam.x).toFloat(); val by = (b.y - cam.y).toFloat(); val bz = (b.z - cam.z).toFloat()
            if (!ax.isFinite() || !ay.isFinite() || !az.isFinite() || !bx.isFinite() || !by.isFinite() || !bz.isFinite()) return
            if (ax == bx && ay == by && az == bz) return
            segments.add(Seg(ax, ay, az, bx, by, bz, color))
        }

        fun box(b: AABB, color: Int) {
            val x0 = b.minX; val y0 = b.minY; val z0 = b.minZ; val x1 = b.maxX; val y1 = b.maxY; val z1 = b.maxZ
            val c = arrayOf(
                Vec3(x0, y0, z0), Vec3(x1, y0, z0), Vec3(x1, y0, z1), Vec3(x0, y0, z1),
                Vec3(x0, y1, z0), Vec3(x1, y1, z0), Vec3(x1, y1, z1), Vec3(x0, y1, z1),
            )
            for (i in EDGES.indices step 2) line(c[EDGES[i]], c[EDGES[i + 1]], color)
        }

        /** Sadece köşeleri çizer (her köşeden kenarlar boyunca kısa çizgiler) — waypoint görünümü. */
        fun corners(b: AABB, color: Int, frac: Double = 0.3) {
            val lx = (b.maxX - b.minX) * frac; val ly = (b.maxY - b.minY) * frac; val lz = (b.maxZ - b.minZ) * frac
            for (x in doubleArrayOf(b.minX, b.maxX)) for (y in doubleArrayOf(b.minY, b.maxY)) for (z in doubleArrayOf(b.minZ, b.maxZ)) {
                val sx = if (x == b.minX) 1 else -1; val sy = if (y == b.minY) 1 else -1; val sz = if (z == b.minZ) 1 else -1
                val p = Vec3(x, y, z)
                line(p, Vec3(x + sx * lx, y, z), color)
                line(p, Vec3(x, y + sy * ly, z), color)
                line(p, Vec3(x, y, z + sz * lz), color)
            }
        }
    }

    private val EDGES = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7)

    private fun render(context: LevelRenderContext) {
        val client = Minecraft.getInstance()
        if (client.level == null || client.player == null) return
        val cam = client.gameRenderer.mainCamera().position()
        val batch = Batch(cam)

        Esp.collect(client, batch)
        Structures.collect(client, batch)
        safaripatron.contest.Honeyhives.collect(client, batch)
        SparklingFinder.collect(batch)
        Trajectory.collect(client, batch)
        safaripatron.capsule.Prefire.collect(client, batch)

        if (batch.segments.isNotEmpty()) {
            val segs = batch.segments.toList()
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), lines) { pose, vertices ->
                for (s in segs) emit(pose, vertices, s)
            }
        }
        if (batch.labels.isNotEmpty()) drawLabels(context, client, cam, batch.labels)
    }

    private fun drawLabels(context: LevelRenderContext, client: Minecraft, cam: Vec3, labels: List<Batch.Label>) {
        val camera = client.gameRenderer.mainCamera()
        val font = client.font
        val poses = context.poseStack()
        for (l in labels) {
            val dx = l.pos.x - cam.x; val dy = l.pos.y - cam.y; val dz = l.pos.z - cam.z
            val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).toFloat()
            // Uzaktan da okunabilsin diye mesafeyle büyür
            val scale = 0.025f * maxOf(1f, dist / 10f)
            val comp = Component.literal(l.text).let { if (l.bold) it.withStyle(ChatFormatting.BOLD) else it }
            poses.pushPose()
            poses.translate(dx, dy, dz)
            poses.rotate(camera.rotation())
            poses.scale(scale, -scale, scale)
            context.submitNodeCollector().submitText(poses, -font.width(comp) / 2f, (l.line * (font.lineHeight + 1)).toFloat(),
                comp.visualOrderText, false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, l.color, 0x40000000, 0)
            poses.popPose()
        }
    }

    private fun emit(pose: PoseStack.Pose, v: VertexConsumer, s: Batch.Seg) {
        val dx = s.bx - s.ax; val dy = s.by - s.ay; val dz = s.bz - s.az
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (len == 0f || !len.isFinite()) return
        val nx = dx / len; val ny = dy / len; val nz = dz / len
        v.addVertex(pose, s.ax, s.ay, s.az).setColor(s.color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH)
        v.addVertex(pose, s.bx, s.by, s.bz).setColor(s.color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH)
    }
}
