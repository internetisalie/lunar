package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import net.internetisalie.lunar.lang.psi.types.LuaGraphType

/**
 * Builds a [LookupElement] for a single type-inferred member (COMP-04-01/02).
 *
 * Presentation: a method icon for function-typed members, a field icon otherwise, with the
 * member's display type as tail/type text. The caller is responsible for any priority wrapping.
 */
object LuaMemberLookup {

    fun create(name: String, memberType: LuaGraphType): LookupElement {
        val icon = if (memberType is LuaGraphType.Function) {
            AllIcons.Nodes.Method
        } else {
            AllIcons.Nodes.Field
        }
        return LookupElementBuilder.create(name)
            .withIcon(icon)
            .withTypeText(memberType.displayName())
    }
}
