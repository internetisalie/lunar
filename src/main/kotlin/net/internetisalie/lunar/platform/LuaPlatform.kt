package net.internetisalie.lunar.platform

enum class LuaPlatform(val label: String) {
    PUC("Lua PUC-Rio"),
    LUAU("Luau"),
    LOVE("Love"),
    PANDOC("Pandoc"),
    REDIS("Redis");

    override fun toString(): String {
        return label
    }
}
