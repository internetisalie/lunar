/*
 * Copyright 2016 Jon S Akhtar (Sylvanaar)
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
package net.internetisalie.lunar.settings

import net.internetisalie.lunar.settings.LuaInterpreterFamily.Companion.FAMILIES
import net.internetisalie.lunar.settings.LuaInterpreterFamily.Companion.UNKNOWN_INTERPRETER

class LuaInterpreter(
    var name: String? = null,
    var path: String? = null,
    var familyKey: String? = null,
    var version: String? = null,
) {
    constructor(other: LuaInterpreter) : this(other.name, other.path, other.familyKey, other.version)

    val family: LuaInterpreterFamily?
        get() {
            if (familyKey == null) return UNKNOWN_INTERPRETER
            return FAMILIES[familyKey]
        }

    val familyOrUnknown: LuaInterpreterFamily
        get() {
            return if (familyKey == null || !FAMILIES.containsKey(familyKey)) UNKNOWN_INTERPRETER
            else FAMILIES[familyKey]!!
        }
}
