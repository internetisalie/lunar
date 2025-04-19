/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.internetisalie.lunar.lang

enum class LuaLanguageLevel(val major: Int, val minor: Int) {
    LUA50(5, 0),
    LUA51(5, 1),
    LUA52(5, 2),
    LUA53(5, 3),
    LUA54(5, 4);

    override fun toString(): String {
        return "Lua ${major}.${minor}"
    }

    val version : String
        get() = "${major}.${minor}"
}
