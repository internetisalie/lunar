package net.internetisalie.lunar.redis.connection

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry

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

    /**
     * BUG-381 step 2's **source of truth for the Server-binary combo**, pinned where it can break.
     *
     * This test earned its place by failing. The plan said to fill the combo from
     * `LuaToolKindClassifier.Tier.PLATFORM_SERVER`, reasoning that it holds exactly the two server
     * kinds. It does not — that tier is assigned by *absence of capabilities*, and
     * `lua-language-server` has none either, so the first implementation would have offered a
     * language server as a Redis server. The list is Redis-owned now
     * ([LuaRedisProvisioning.SERVER_TOOL_KIND_IDS]), and this asserts both halves of that: the
     * kinds that must be offered, and the one that must not.
     *
     * Every id must also resolve in [net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry],
     * because the form maps ids to registry entries for their display names and an unmatched id is
     * dropped silently — a kind that vanishes from the combo with nothing else going red.
     *
     * This is the only part of step 2 a unit test can reach. Whether the control renders, whether
     * its conditional rows toggle, and whether Host/Port actually grey out are questions about a
     * Swing form on screen, and the `verify-in-ide` VNC pass answers those instead.
     */
    fun testTheServerKindListIsRedisOwnedAndFullyRegistered() {
        val declared = LuaRedisProvisioning.SERVER_TOOL_KIND_IDS
        val registeredIds = LuaToolKindRegistry.all().map { it.id }

        assertEquals("the RESP-speaking kinds the combo offers", listOf("redis-server", "valkey-server"), declared)
        assertTrue(
            "every declared server kind must exist in the registry, or it is dropped from the " +
                "combo silently — declared=$declared registry=$registeredIds",
            registeredIds.containsAll(declared),
        )
        assertFalse(
            "lua-language-server is not a Redis server; sourcing this list from the classifier's " +
                "capability-based PLATFORM_SERVER tier put it in the combo",
            "lua-language-server" in declared,
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
