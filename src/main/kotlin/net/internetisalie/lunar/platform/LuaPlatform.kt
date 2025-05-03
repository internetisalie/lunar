package net.internetisalie.lunar.platform

enum class LuaPlatform(val label: String) {
    STANDARD("Standard"),
    LUAU("Luau"),
    PANDOC("Pandoc"),
    REDIS("Redis"),
    TARANTOOL("Tarantool");

    override fun toString(): String {
        return label
    }
}
