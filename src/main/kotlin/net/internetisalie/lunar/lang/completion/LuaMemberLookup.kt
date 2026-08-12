package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import net.internetisalie.lunar.lang.indexing.LuaReceiverMember
import net.internetisalie.lunar.lang.psi.types.LuaGraphType

/**
 * Builds a [LookupElement] for a single type-inferred member (COMP-04-01/02).
 *
 * Presentation: a method icon for function-typed members, a field icon otherwise, with the
 * member's display type as tail/type text. The caller is responsible for any priority wrapping.
 */
object LuaMemberLookup {
    fun create(
        name: String,
        memberType: LuaGraphType,
    ): LookupElement {
        val icon =
            if (memberType is LuaGraphType.Function) {
                AllIcons.Nodes.Method
            } else {
                AllIcons.Nodes.Field
            }
        return LookupElementBuilder
            .create(name)
            .withIcon(icon)
            .withTypeText(memberType.displayName())
    }

    /**
     * COMP-09 §4.13 — the index arm's element, for a member enumerated by
     * [LuaReceiverMemberIndex][net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex].
     *
     * **No type text, deliberately.** The index carries member *names and kinds*, not types — reading a
     * type would mean the graph build this arm exists to skip. §1.7 measured type rendering at 4 ms, so
     * this is an absence rather than a saving, and E5 asserts it as `typeText == null` off a rendered
     * `LookupElementPresentation` rather than off the lookup string.
     */
    fun create(member: LuaReceiverMember): LookupElement {
        val icon =
            if (member.kind == LuaReceiverMember.Kind.FUNCTION) {
                AllIcons.Nodes.Method
            } else {
                AllIcons.Nodes.Field
            }
        return LookupElementBuilder
            .create(member.name)
            .withIcon(icon)
    }
}
