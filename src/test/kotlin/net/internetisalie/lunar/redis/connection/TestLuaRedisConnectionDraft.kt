package net.internetisalie.lunar.redis.connection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * BUG-381 — **the settings page must not silently rewrite a connection's provisioning to `Remote`.**
 *
 * `LuaRedisConnectionDraft` is the settings page's whole in-memory model of a connection, and it
 * carried no `provisioning` field. So the value was dropped at both ends of its round trip: `from`
 * did not read it, and `toConnection` could not pass on what it did not hold, hardcoding `Remote`
 * instead. `LuaRedisConnectionsConfigurable.apply` then upserts **every** draft — not only edited
 * ones — through `LuaRedisConnectionSettings.upsert`, which replaces persisted state wholesale
 * rather than merging.
 *
 * The result was that editing any field of any connection rewrote **all** of them to `Remote`,
 * with no error and nothing to diff — destroying the hand-edited-XML configuration that was this
 * bug's own documented workaround.
 *
 * **Every assertion here goes through the existing public API rather than reading a
 * `draft.provisioning` field**, so all three were red against the unfixed code rather than failing
 * to compile against it. A compile error is not a reproduction.
 *
 * Ordering: [testApplyDoesNotFlattenAConnectionTheUserNeverTouched] is the one that matters and was
 * written first. The two round-trip tests below it can both pass while the settings page still
 * flattens a sibling, because `apply`'s defect is in which drafts it writes, not in one conversion.
 *
 * The light fixture shares its project across test classes, so [tearDown] removes what each test
 * seeded — a leaked connection would surface as an unrelated failure elsewhere in the suite.
 */
class TestLuaRedisConnectionDraft : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            val settings = LuaRedisConnectionSettings.getInstance(project)
            settings.connections().map { it.id }.forEach { settings.remove(it) }
        } finally {
            super.tearDown()
        }
    }

    /**
     * The defect as a user meets it: two connections, one of them provisioned by Docker, and an
     * ordinary trip through the settings page.
     *
     * `reset()` then `apply()` is exactly what the Settings dialog does when the user changes
     * anything at all — `apply` iterates the whole model, so editing the *other* connection's port
     * is enough to take this one's provisioning with it. No edit is simulated here because none is
     * needed to reach the defect; the loop does not consult whether a draft changed.
     */
    fun testApplyDoesNotFlattenAConnectionTheUserNeverTouched() {
        val settings = LuaRedisConnectionSettings.getInstance(project)
        settings.upsert(connection(DOCKER_ID, LuaRedisProvisioning.Docker("redis:8")))
        settings.upsert(connection(REMOTE_ID, LuaRedisProvisioning.Remote))

        val configurable = LuaRedisConnectionsConfigurable(project)
        configurable.reset()
        configurable.apply()

        assertEquals(
            "the settings page rewrote a Docker connection's provisioning to Remote — the user's " +
                "ephemeral-server configuration is gone, with no error and nothing to diff",
            LuaRedisProvisioning.Docker("redis:8"),
            settings.connections().single { it.id == DOCKER_ID }.provisioning,
        )
        assertEquals(
            "and the Remote connection must be left alone too",
            LuaRedisProvisioning.Remote,
            settings.connections().single { it.id == REMOTE_ID }.provisioning,
        )
    }

    /** Every kind survives connection → draft → connection, which is what the page does per entry. */
    fun testDraftRoundTripPreservesEveryProvisioningKind() {
        listOf(
            LuaRedisProvisioning.Remote,
            LuaRedisProvisioning.LocalBinary("valkey-server"),
            LuaRedisProvisioning.Docker("valkey/valkey:8"),
        ).forEach { provisioning ->
            val original = connection(DOCKER_ID, provisioning)
            val restored = LuaRedisConnectionDraft.from(original, password = null).toConnection()
            assertEquals("$provisioning must survive the draft round trip", original, restored)
        }
    }

    /**
     * `toConnection` alone, so a failure here points at the conversion rather than at the page.
     *
     * The draft is built by `from` rather than by constructor, deliberately: that is the only way
     * the page ever obtains one for an existing connection, and it was the half that dropped the
     * value first.
     */
    fun testToConnectionKeepsLocalBinaryProvisioning() {
        val draft =
            LuaRedisConnectionDraft.from(
                connection(DOCKER_ID, LuaRedisProvisioning.LocalBinary("redis-server")),
                password = null,
            )

        assertEquals(
            "toConnection must carry the draft's provisioning, not a hardcoded Remote",
            LuaRedisProvisioning.LocalBinary("redis-server"),
            draft.toConnection().provisioning,
        )
    }

    private fun connection(
        id: String,
        provisioning: LuaRedisProvisioning,
    ): LuaRedisServerConnection =
        LuaRedisServerConnection(
            id = id,
            name = "conn-$id",
            host = "127.0.0.1",
            port = 6379,
            tls = false,
            database = 0,
            username = null,
            provisioning = provisioning,
        )

    private companion object {
        const val DOCKER_ID = "bug381-docker"
        const val REMOTE_ID = "bug381-remote"
    }
}
