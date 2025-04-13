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
package net.internetisalie.lunar.lang.lexer

/**
 * Created by IntelliJ IDEA.
 * User: jon
 * Date: Apr 3, 2010
 * Time: 2:15:34 AM
 */
class ExtendedSyntaxStrCommentHandler {
    /* Code to handle extended quote/comment syntax
    *
    * There is a basic assumption that inside a longstring or longcomment
    * you cannot begin another longstring or comment of the same level,
    * thus there is only ever 1 closing bracket to track, and once found
    * no more closing brackets are valid until another opening bracket.
    * */
    var longQLevel: Int = 0

    fun isCurrentExtQuoteStart(endQuote: CharSequence): Boolean {
        val level = getLevel(endQuote)
        return longQLevel == level
    }

    fun resetCurrentExtQuoteStart() {
        longQLevel = 0
    }

    fun setCurrentExtQuoteStart(cs: CharSequence) {
        val level = getLevel(cs)

        longQLevel = level
    }

    companion object {
        private fun getLevel(cs: CharSequence): Int {
            var level = 0
            var comment = 0
            while (cs[comment] == '-') comment++
            while (cs.length > comment + level && cs[comment + 1 + level] == '=') level++
            return level
        }
    }
}
