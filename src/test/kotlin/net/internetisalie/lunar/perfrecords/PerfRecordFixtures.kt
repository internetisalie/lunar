package net.internetisalie.lunar.perfrecords

internal const val USE_LUA = "local x = global_fu<caret>"

internal fun flat(n: Int): String =
    (0 until n).joinToString("\n") { i ->
        when (i % 5) {
            0 -> "local t$i = { a = $i, b = \"s$i\" }"
            1 -> "function f$i(x) if x > $i then return x else return $i end end"
            2 -> "for k = 1, $i do local c = function() return k end; c() end"
            3 -> "obj:method$i(1); obj.field$i = $i"
            else -> "local w = 0; while w < 3 do w = w + 1 end; repeat w = w - 1 until w == 0"
        }
    }

internal fun callbacks(d: Int): String = "f(function()\n".repeat(d) + "x()\n" + "end)\n".repeat(d)

internal fun parens(d: Int): String = "local x = " + "(".repeat(d) + "a" + ")".repeat(d)

internal fun annotatedCalls(n: Int): String =
    "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(n) end\n\nlocal b = Builder\n" +
        (0 until n).joinToString("\n") { "b:setName(\"a$it\")" }

internal fun colonCalls(n: Int): String =
    "local t = {}\nfunction t:m(n) end\n\n" + (0 until n).joinToString("\n") { "t:m(\"a$it\")" }

internal fun members(n: Int): String =
    "---@class Builder\nlocal Builder = {}\n" +
        (0 until n).joinToString("\n") { "function Builder.m$it() end" } + "\nlocal b = Builder\nb.<caret>"

internal fun globals(n: Int): String = (0 until n).joinToString("\n") { "function global_func_$it() end" }
