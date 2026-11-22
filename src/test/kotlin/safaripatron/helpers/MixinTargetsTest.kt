package safaripatron.helpers

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.*

class MixinTargetsTest {
    private fun methods(name: String): Set<Pair<String, String>> {
        val stream = javaClass.getResourceAsStream("/$name.class")
        assertNotNull(stream, name)
        val found = HashSet<Pair<String, String>>()
        stream.use {
            ClassReader(it).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    found.add(name to descriptor); return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        return found
    }
    @Test fun attackInvokerAndInputHookExistInTargetMinecraft() {
        val found = methods("net/minecraft/client/Minecraft")
        assertTrue("startAttack" to "()Z" in found)
        assertTrue("handleKeybinds" to "()V" in found)
    }
    @Test fun entryPacketBoundariesMatchTheActualGameDescriptors() {
        val found = methods("net/minecraft/client/multiplayer/MultiPlayerGameMode")
        val player = "Lnet/minecraft/world/entity/player/Player;"
        val entity = "Lnet/minecraft/world/entity/Entity;"
        val hand = "Lnet/minecraft/world/InteractionHand;"
        val result = "Lnet/minecraft/world/InteractionResult;"
        assertTrue("attack" to "($player$entity)V" in found)
        assertTrue("interact" to "($player${entity}Lnet/minecraft/world/phys/EntityHitResult;$hand)$result" in found)
        assertTrue("useItem" to "($player$hand)$result" in found)
        assertTrue("useItemOn" to "(Lnet/minecraft/client/player/LocalPlayer;${hand}Lnet/minecraft/world/phys/BlockHitResult;)$result" in found)
        assertTrue("handleContainerInput" to "(IIILnet/minecraft/world/inventory/ContainerInput;$player)V" in found)
    }
    @Test fun candleHookTargetsSelectionShapeRatherThanCollisionShape() {
        assertTrue("getShape" to "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;" in
            methods("net/minecraft/world/level/block/state/BlockBehaviour\$BlockStateBase"))
    }
}
