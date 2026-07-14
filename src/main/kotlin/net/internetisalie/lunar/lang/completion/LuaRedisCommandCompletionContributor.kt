package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import net.internetisalie.lunar.analysis.redis.RedisCallSiteMatcher
import net.internetisalie.lunar.analysis.redis.RedisCommandInfo
import net.internetisalie.lunar.analysis.redis.RedisCommandSpec
import net.internetisalie.lunar.analysis.redis.RedisCommandSpecService
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Offers Redis command names as completions inside the first string argument of
 * `redis.call(...)` / `redis.pcall(...)` (and `server.call` / `server.pcall` on
 * the Valkey platform) (AC-3, design §2.3, §3.3, §3.11).
 *
 * Registered as a sibling to [net.internetisalie.lunar.lang.LuaCompletionContributor]
 * and completely inert on non-Redis/Valkey targets — every contributor path begins
 * with a target check and returns early on mismatch.
 *
 * Threading: runs on the EDT completion callback; only PSI reads + service lookup
 * (no I/O). All PSI elements are used transiently inside the callback.
 */
class LuaRedisCommandCompletionContributor : CompletionContributor() {

    companion object {
        private const val COMMAND_PRIORITY = 90.0

        /**
         * Version filter: returns `true` when [since] is ≤ the server version of [target]
         * (design §3.11). Parses dotted version components; missing components default to 0.
         * Valkey labels map to their Redis-compat baseline [7, 2].
         */
        fun sinceLe(since: String, target: Target): Boolean {
            val sinceVec = parseDotted(since)
            val targetVec = targetVersionVec(target)
            return compareVectors(targetVec, sinceVec) >= 0
        }

        /** Splits a dotted-integer string into an `IntArray`; non-numeric parts default to 0. */
        private fun parseDotted(version: String): IntArray =
            version.split(".").map { it.toIntOrNull() ?: 0 }.toIntArray()

        /**
         * Derives the server version vector for [target], used by [sinceLe].
         * Redis labels: `"5"` → `[5]`, `"6"` → `[6]`, `"7+"` → `[7]`.
         * Valkey labels map to the Redis-compat baseline `[7, 2]`.
         */
        private fun targetVersionVec(target: Target): IntArray {
            val platform = target.platform
            val label = target.version.label
            if (platform == LuaPlatform.VALKEY || platform.name == "VALKEY") return intArrayOf(7, 2)
            val stripped = label.trimEnd('+')
            return parseDotted(stripped)
        }

        /**
         * Compares two version vectors component-wise (missing components = 0).
         * Returns positive when [a] > [b], zero when equal, negative when [a] < [b].
         */
        private fun compareVectors(a: IntArray, b: IntArray): Int {
            val len = maxOf(a.size, b.size)
            for (i in 0 until len) {
                val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }

        /** The `redis` namespace is valid for REDIS (and Valkey as compat alias). */
        private fun namespaceMatchesPlatform(namespace: String, target: Target): Boolean {
            val platform = target.platform
            return when (namespace) {
                "redis" -> platform == LuaPlatform.REDIS || platform == LuaPlatform.VALKEY
                "server" -> platform == LuaPlatform.VALKEY || platform.name == "VALKEY"
                else -> false
            }
        }

        /** Adds one prioritized lookup element per command that passes the version filter. */
        private fun addCommandElements(
            spec: RedisCommandSpec,
            target: Target,
            result: CompletionResultSet,
        ) {
            for (info: RedisCommandInfo in spec.commands.values) {
                if (!sinceLe(info.since, target)) continue
                val element = LookupElementBuilder
                    .create(info.name)
                    .withTailText(" ${info.summary}", true)
                result.addElement(PrioritizedLookupElement.withPriority(element, COMMAND_PRIORITY))
            }
        }
    }

    init {
        extend(
            CompletionType.BASIC,
            psiElement().withElementType(LuaElementTypes.STRING),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    // Step 1 (design §3.3): project is required; bail if unavailable.
                    val project = parameters.editor.project ?: return

                    // Step 2: confirm the position is inside a redis/server call-site's
                    // first string-literal argument. A non-literal first arg (TC-COMP-3)
                    // never reaches here because LuaElementTypes.STRING won't match an
                    // identifier; dynamic first args with a literal at another position
                    // are still excluded because nameLiteral must be non-null.
                    val site = RedisCallSiteMatcher.match(parameters.position) ?: return
                    if (site.member !in setOf("call", "pcall")) return
                    if (site.nameLiteral == null) return

                    // Step 3: target must be Redis/Valkey, and namespace must match.
                    val target = LuaProjectSettings.getInstance(project).state.getTarget()
                    if (!namespaceMatchesPlatform(site.namespace, target)) return

                    // Step 4: load the spec; no-op when no bundled data (TC-SPEC-2).
                    val spec = RedisCommandSpecService.getInstance().specFor(target)
                    if (spec === RedisCommandSpec.EMPTY) return

                    // Step 5: emit one lookup per version-valid command (§3.11).
                    addCommandElements(spec, target, result)
                }
            },
        )
    }
}
