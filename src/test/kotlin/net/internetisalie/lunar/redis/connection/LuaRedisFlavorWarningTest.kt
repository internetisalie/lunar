package net.internetisalie.lunar.redis.connection

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

/**
 * REDIS-03 TC-FLV-3: [LuaRedisFlavorWarning] fires exactly once per connection per session on a
 * target/flavor mismatch, and is silent when the flavors match (design §2.6, §3.4). No live socket —
 * the balloon is observed via the project `Notifications.TOPIC`.
 *
 * The `BasePlatformTestCase` light project (and its `LuaRedisFlavorWarning` service, whose
 * once-per-session set persists) is reused across methods, so every test uses a fresh connection id.
 */
@RunWith(JUnit4::class)
class LuaRedisFlavorWarningTest : BasePlatformTestCase() {

    @Test
    fun warnsExactlyOncePerConnectionOnMismatchAndSuppressesRepeat() {
        val balloons = captureBalloons()
        val warning = LuaRedisFlavorWarning.getInstance(project)
        val connectionId = "conn-${UUID.randomUUID()}"

        warning.warnOnceIfMismatch(connectionId, ServerFlavor.VALKEY, LuaPlatform.REDIS)
        warning.warnOnceIfMismatch(connectionId, ServerFlavor.VALKEY, LuaPlatform.REDIS)

        assertEquals(1, balloons.size)
    }

    @Test
    fun warnsPerDistinctConnection() {
        val balloons = captureBalloons()
        val warning = LuaRedisFlavorWarning.getInstance(project)

        warning.warnOnceIfMismatch("conn-${UUID.randomUUID()}", ServerFlavor.VALKEY, LuaPlatform.REDIS)
        warning.warnOnceIfMismatch("conn-${UUID.randomUUID()}", ServerFlavor.VALKEY, LuaPlatform.REDIS)

        assertEquals(2, balloons.size)
    }

    @Test
    fun silentWhenFlavorMatchesTarget() {
        val balloons = captureBalloons()
        val warning = LuaRedisFlavorWarning.getInstance(project)

        warning.warnOnceIfMismatch("conn-${UUID.randomUUID()}", ServerFlavor.REDIS, LuaPlatform.REDIS)
        warning.warnOnceIfMismatch("conn-${UUID.randomUUID()}", ServerFlavor.VALKEY, LuaPlatform.STANDARD)

        assertTrue(balloons.isEmpty())
    }

    private fun captureBalloons(): MutableList<Notification> {
        val balloons = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == "notification.group.lunar.tools") balloons.add(notification)
                }
            },
        )
        return balloons
    }
}
