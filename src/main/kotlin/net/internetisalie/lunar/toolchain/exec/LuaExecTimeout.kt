package net.internetisalie.lunar.toolchain.exec

enum class LuaExecTimeout(val millis: Int) {
    PROBE(10_000),
    COMMAND(15_000),
    FORMAT(30_000),
    NETWORK(120_000),
    INSTALL(600_000),
}
