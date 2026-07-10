package net.internetisalie.lunar.redis.connection

import com.intellij.util.xmlb.XmlSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TC-CONN-1 (design §2.4, §2.5): a connection round-trips through [LuaRedisConnectionSettings] and its
 * XML state, preserving every scalar field, and the secret never appears in the serialized XML.
 *
 * PasswordSafe round-trips are covered separately in [TestLuaRedisCredentialStore]; these tests pin the
 * metadata persistence contract and the no-secret-in-XML invariant with a headless serializer.
 */
class TestLuaRedisConnectionSettings {

    @Test
    fun upsertThenFindByIdRoundTripsAllScalarFields() {
        val settings = LuaRedisConnectionSettings()
        val connection = sampleConnection()

        settings.upsert(connection)
        val restored = settings.findById("u1")

        assertNotNull(restored)
        assertEquals(connection, restored)
        assertEquals(2, restored.database)
        assertEquals("app", restored.username)
        assertEquals(LuaRedisProvisioning.Remote, restored.provisioning)
    }

    @Test
    fun stateRoundTripsThroughXmlSerializerWithoutSecret() {
        val settings = LuaRedisConnectionSettings()
        settings.upsert(sampleConnection())

        val element = XmlSerializer.serialize(settings.state)
        val serializedXml = com.intellij.openapi.util.JDOMUtil.write(element)
        val restoredState = XmlSerializer.deserialize(element, LuaRedisConnectionSettings.State::class.java)

        val reloaded = LuaRedisConnectionSettings()
        reloaded.loadState(restoredState)
        val restored = reloaded.findById("u1")

        assertNotNull(restored)
        assertEquals(sampleConnection(), restored)
        assertFalse(serializedXml.contains("s3cr3t"), "password must not appear in serialized XML")
        assertFalse(serializedXml.contains("password", ignoreCase = true), "no password field in XML")
    }

    @Test
    fun upsertReplacesExistingEntryWithSameId() {
        val settings = LuaRedisConnectionSettings()
        settings.upsert(sampleConnection())
        settings.upsert(sampleConnection().copy(name = "renamed", port = 7000))

        assertEquals(1, settings.connections().size)
        assertEquals("renamed", settings.findById("u1")?.name)
        assertEquals(7000, settings.findById("u1")?.port)
    }

    @Test
    fun removeDropsTheConnection() {
        val settings = LuaRedisConnectionSettings()
        settings.upsert(sampleConnection())

        settings.remove("u1")

        assertNull(settings.findById("u1"))
        assertEquals(0, settings.connections().size)
    }

    @Test
    fun localBinaryAndDockerProvisioningRoundTrip() {
        val settings = LuaRedisConnectionSettings()
        settings.upsert(sampleConnection().copy(id = "b1", provisioning = LuaRedisProvisioning.LocalBinary("redis-server")))
        settings.upsert(sampleConnection().copy(id = "d1", provisioning = LuaRedisProvisioning.Docker("valkey/valkey:8")))

        val element = XmlSerializer.serialize(settings.state)
        val restoredState = XmlSerializer.deserialize(element, LuaRedisConnectionSettings.State::class.java)
        val reloaded = LuaRedisConnectionSettings().apply { loadState(restoredState) }

        assertEquals(LuaRedisProvisioning.LocalBinary("redis-server"), reloaded.findById("b1")?.provisioning)
        assertEquals(LuaRedisProvisioning.Docker("valkey/valkey:8"), reloaded.findById("d1")?.provisioning)
    }

    private fun sampleConnection(): LuaRedisServerConnection =
        LuaRedisServerConnection(
            id = "u1",
            name = "local",
            host = "127.0.0.1",
            port = 6379,
            tls = false,
            database = 2,
            username = "app",
            provisioning = LuaRedisProvisioning.Remote,
        )
}
