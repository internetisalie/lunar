package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiElement

/**
 * A type named before it is known to exist — `---@field part Gadget` builds one of these for
 * `Gadget` while `Gadget`'s own declaration may be in another file, or in no file at all.
 *
 * ## TYPE-11 / BUG-434 — the sixth under-recording channel, and why the frame lives here
 *
 * [resolved] is memoized, and a memoized answer is a *second* door onto a cross-file question. The
 * three doors in `LuaTypeManagerImpl` each store their answer beside the
 * [LuaTypeSourceRecorder.SourceFrame] that produced it and replay it on a cache hit
 * (`LuaTypeManagerImpl.CachedAnswer`, design §3.6); this one used to store the answer alone. So a
 * reference forced once at `depth() == 0` — by a hover, a completion, `LuaOverrideLineMarkerProvider`,
 * a hierarchy walk, an assignability inspection — short-circuited before
 * [LuaTypeManager.resolveType] was ever reached, and the *next* read, inside a snapshot build, learnt
 * nothing about the file the reference had resolved into. `LuaGraphType.fromLuaType` flattens every
 * such reference during a build (`LuaGraphType.kt:251`), so the escape was on the main path:
 * measured as `arm2 pre-forced urls=[lib.lua] pinnable=true` against `arm1 cold
 * urls=[lib.lua, gadget.lua] pinnable=false`, with the wrongly-pinned snapshot then surviving an
 * edit to the project file it depended on (`TypeElevenDr07LazyReferenceProbeTest`).
 *
 * The repair is the same idiom, applied to the same shape: memoize the frame **with** the answer and
 * replay it on every read, so a read inside a frame contributes exactly what a cold read would. The
 * cold path replays too, and that costs nothing: [LuaTypeSourceRecorder.report] and its siblings
 * already wrote to every open frame on the way in, so the replay is set-wise idempotent — the same
 * reasoning `LuaTypeSourceRecorder.reportWarmSnapshot` states for `forFile`'s cold path.
 *
 * Not fixed by dropping the `by lazy` for a plain `get()` (the candidate the bug report named): that
 * is correct but pays a map lookup and a resolution guard on every member access, and it makes the
 * frame nobody's property again — the next consumer of a memoized type would re-open the hole. A
 * frame belongs with the answer it explains.
 *
 * Lifetime: a frame holds `String` URLs only, so this adds no hard reference to `VirtualFile` or
 * `PsiFile` (engineering contract §4). The instance itself is bounded by the `typeCache` entry that
 * holds the enclosing [LuaClassType], which every `PsiModificationTracker` tick discards.
 */
class LuaTypeReference(
    override val name: String,
    private val context: PsiElement,
) : LuaType {
    private val memoizedAnswer: Pair<LuaType, LuaTypeSourceRecorder.SourceFrame> by lazy {
        LuaTypeSourceRecorder.recording {
            LuaTypeManager.getInstance(context.project).resolveType(name, context) ?: LuaPrimitiveType.UNKNOWN
        }
    }

    /**
     * The resolved type, **and** a replay of the sources that resolving it consumed. Reading this is
     * consuming those sources, whether or not this read is the one that paid for them.
     */
    val resolved: LuaType
        get() {
            val (answer, sourceFrame) = memoizedAnswer
            LuaTypeSourceRecorder.replay(sourceFrame)
            return answer
        }

    fun resolveType(): LuaType = resolved

    override fun resolveMember(name: String): LuaTypeMember? = resolved.resolveMember(name)

    override fun getMembers(): Map<String, LuaTypeMember> = resolved.getMembers()

    override fun isAssignableTo(other: LuaType): Boolean = resolved.isAssignableTo(other)

    override fun toString(): String = name
}
