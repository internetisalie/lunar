/*
 * Copyright 2010 Jon S Akhtar (Sylvanaar)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.util.IncorrectOperationException

object LuaPsiUtils {

    @JvmStatic
    fun nestingLevel(element: PsiElement): Int {
        var depth = 0
        var current = element.context

        while (current != null) {
            depth++
            current = current.context
        }

        return depth
    }

    @JvmStatic
    fun blockNestingLevel(element: PsiElement): Int {
        var depth = 0
        var current = findEnclosingBlock(element)

        while (current != null) {
            depth++
            current = findEnclosingBlock(current)
        }

        return depth
    }

    @JvmStatic
    fun elementAfter(element: PsiElement): PsiElement? {
        val node = element.node ?: return null
        val next = node.treeNext ?: return null
        return next.psi
    }

    @JvmStatic
    fun findEnclosingBlock(element: PsiElement): PsiElement? {
        var current: PsiElement = element

        while (current.context != null) {
            current = current.context ?: return null

            if (isValidContainer(current)) {
                return current
            }
        }

        return null
    }

    private fun isValidContainer(element: PsiElement): Boolean = element is LuaBlock

    @JvmStatic
    fun processChildDeclarations(
        element: PsiElement,
        processor: PsiScopeProcessor,
        substitutor: ResolveState,
        lastParent: PsiElement?,
        place: PsiElement,
    ): Boolean {
        var run = if (lastParent == null) element.lastChild else lastParent.prevSibling

        while (run != null) {
            if (!run.processDeclarations(processor, substitutor, null, place)) {
                return false
            }

            run = run.prevSibling
        }

        return true
    }

    @JvmStatic
    fun getElementLineNumber(element: PsiElement): Int {
        val fileViewProvider: FileViewProvider = element.containingFile.viewProvider
        val document = fileViewProvider.document ?: return 0
        return document.getLineNumber(element.textOffset) + 1
    }

    @JvmStatic
    fun getElementEndLineNumber(element: PsiElement): Int {
        val fileViewProvider: FileViewProvider = element.containingFile.viewProvider
        val document = fileViewProvider.document ?: return 0
        return document.getLineNumber(element.textOffset + element.textLength) + 1
    }

    @JvmStatic
    fun createRange(node: PsiElement): TextRange =
        TextRange.from(node.textOffset, node.textLength)

    @JvmStatic
    fun nodeType(element: PsiElement): IElementType? = element.node?.elementType

    @JvmStatic
    fun findNextSibling(start: PsiElement, ignoreType: IElementType): PsiElement? {
        var current = start.nextSibling

        while (current != null) {
            if (ignoreType != nodeType(current)) {
                return current
            }

            current = current.nextSibling
        }

        return null
    }

    @JvmStatic
    fun findPreviousSibling(start: PsiElement, ignoreType: IElementType): PsiElement? {
        var current = start.prevSibling

        while (current != null) {
            if (ignoreType != nodeType(current)) {
                return current
            }

            current = current.prevSibling
        }

        return null
    }

    @JvmStatic
    @Throws(IncorrectOperationException::class)
    fun replaceElement(original: PsiElement, replacement: PsiElement): PsiElement {
        try {
            return original.replace(replacement)
        } catch (e: IncorrectOperationException) {
            // failed, try another way
        } catch (e: UnsupportedOperationException) {
            // failed, try another way
        }

        val parent = original.parent
        return if (parent != null) {
            val inserted = parent.addBefore(replacement, original)
            original.delete()
            inserted
        } else {
            original.node.replaceAllChildrenToChildrenOf(replacement.node)
            original
        }
    }

    @JvmStatic
    fun toPsiElementArray(collection: Collection<PsiElement>): Array<PsiElement> {
        if (collection.isEmpty()) {
            return PsiElement.EMPTY_ARRAY
        }

        return collection.toTypedArray()
    }

    @JvmStatic
    fun hasDirectChildErrorElements(element: PsiElement): Boolean {
        if (element is PsiErrorElement) {
            return true
        }

        return element.children.any { it is PsiErrorElement }
    }
}
