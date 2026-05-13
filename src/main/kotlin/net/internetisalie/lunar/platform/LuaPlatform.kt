package net.internetisalie.lunar.platform

enum class LuaPlatform(val label: String, val pathSegment: String) {
    STANDARD("Standard", "standard"),
    LUAJIT("LuaJIT", "luajit"),
    NGX("OpenResty", "ngx"),
    PANDOC("Pandoc", "pandoc"),
    REDIS("Redis", "redis"),
    TARANTOOL("Tarantool", "tarantool");

    override fun toString(): String {
        return label
    }
}
