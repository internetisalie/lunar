package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.corpus.CorpusManifest
import java.io.File

internal data class GoldenSource(
    val goldenPath: String,
    val fileName: String,
    val text: String,
)

/**
 * TYPE-12 golden sources (design Appendix C). `fileUnion` (`---@class File`) is deliberately absent:
 * it collides with the bundled `File` stub class, whose merge order varies between JVMs (BUG-498).
 */
internal object TypeGoldenFixtures {
    fun annotated(n: Int): String {
        val calls = (0 until n).joinToString("\n") { "b:setName(\"a$it\")" }
        return "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(n) end\n\nlocal b = Builder\n$calls"
    }

    fun paramN(n: Int): String {
        val calls = (0 until n).joinToString("\n") { if (it % 2 == 0) "b:setName(\"a$it\")" else "b:setName($it)" }
        return "---@class Builder\nlocal Builder = {}\n---@param n string\n---@return Builder\n" +
            "function Builder:setName(n) return self end\nlocal b = Builder\n$calls"
    }

    fun selfN(n: Int): String {
        val methods = (0 until n).joinToString("\n") { "function C:f$it() self:inc($it) end" }
        return "---@class C\nlocal C = {}\n---@param x number\nfunction C:inc(x) end\n$methods"
    }

    fun dotN(n: Int): String {
        val reads = (0 until n).joinToString("\n") { "print(b.name)" }
        return "---@class D\n---@field name string\nlocal D = {}\nlocal b = D\n$reads"
    }

    val PARAM =
        "---@class Builder\nlocal Builder = {}\n---@param n string\n---@return Builder\n" +
            "function Builder:setName(n) return self end\nlocal b = Builder\nb:setName(42)"

    val NAMED_SHAPES: List<Pair<String, String>> =
        listOf(
            "selfCalls" to
                "---@class C\nlocal C = {}\n---@param n number\nfunction C:inc(n) end\n" +
                "function C:a() self:inc(1) self:inc('x') end\nfunction C:b() self:inc(2) self:inc() end\n" +
                "function C:c() self:inc(3, 4) end\n",
            "nilReceiver" to "local x = nil\nx:foo()\nx:foo(1)\nx:bar()\n",
            "functionReceiver" to "local f = function() end\nf:foo(1)\nf:foo(2)\n",
            "unannotated" to
                "local o = {}\nfunction o:f(a) return a end\nlocal r1 = o:f(1)\nlocal r2 = o:f('s')\nlocal r3 = o:f(true)\nprint(r1 .. r2)\n",
            "callBeforeDecl" to
                "local o = {}\nlocal function use() return o:g(1), o:g('a') end\nfunction o:g(v) return v end\nlocal z = use()\n",
            "inheritance" to
                "---@class Base\nlocal Base = {}\n---@param s string\nfunction Base:say(s) end\n" +
                "---@class D : Base\nlocal D = {}\nlocal d = D\nd:say('x')\nd:say(1)\nd:say()\n",
            "fieldFunction" to
                "---@class K\n---@field run fun(self: K, n: number): string\nlocal K = {}\nlocal k = K\n" +
                "local s = k:run(1)\nlocal t = k:run('x')\nprint(s .. t)\n",
            "chain" to
                "---@class Bd\nlocal Bd = {}\n---@param n string\n---@return Bd\nfunction Bd:set(n) return self end\n" +
                "local b = Bd\nlocal r = b:set('x')\nr:set(1)\nb:set('y'):set(2)\nlocal q = b:set('z')\n",
            "twoReceivers" to
                "---@class P\nlocal P = {}\n---@param n number\nfunction P:go(n) end\nlocal a = P\nlocal c = P\n" +
                "a:go(1)\nc:go('x')\na:go('y')\nc:go(2)\n",
            "generic" to
                "---@class G\nlocal G = {}\n---@generic T\n---@param x T\n---@return T\n" +
                "function G:id(x) return x end\n" +
                "local g = G\nlocal n = g:id(1)\nlocal s = g:id('s')\n",
            "arity" to
                "---@class Ar\nlocal Ar = {}\n---@param a number\n---@param b number\nfunction Ar:two(a, b) end\n" +
                "local x = Ar\nx:two(1)\nx:two(1, 2)\nx:two(1, 2, 3)\nx:two(1, 'b')\n",
            "unionReceiver" to
                "---@class U1\nlocal U1 = {}\nfunction U1:m(a) end\n" +
                "---@class U2\nlocal U2 = {}\nfunction U2:m(a, b) end\n" +
                "local u = U1\nif cond then u = U2 end\nu:m(1)\nu:m(1, 2)\nu:m()\n",
            "pipeUnion" to
                "---@class Pipe\nlocal Pipe = {}\n---@param fmt? string\n---@return string\n" +
                "function Pipe:read(fmt) end\n" +
                "function Pipe:close() end\n---@return Pipe|nil\nlocal function open(p) end\nlocal fh = open('x')\n" +
                "if not fh then return end\nif fh:read() ~= 'v' then fh:close() return end\nlocal s = fh:read('*a')\nfh:close()\n",
            "fileUnionStdlib" to
                "local fh = io.open('x', 'rb')\nif not fh then return end\n" +
                "if fh:read() ~= 'v' then fh:close() return end\n" +
                "if fh:read() ~= 'w' then fh:close() return end\nlocal s = fh:read('*a')\nfh:close()\n",
            "builderFixture" to
                "---@class LB\n---@field name string\nlocal LB = {}\n---@param name string\n---@return LB\n" +
                "function LB:setName(name)\n  self.name = name\n  return self\nend\nlocal builder = LB\n" +
                (0 until 12).joinToString("\n") { "builder:setName(\"f$it\")" } + "\n",
            "nilMember" to "local o = {}\no.m = nil\no:m(1)\no:m(2)\no:m(3)\n",
            "unknownMember" to "local o = {}\no.m = wx.thing\no:m(1)\no:m('a')\no:m(true)\n",
            "declaredFieldNil" to
                "---@class K\n---@field m fun(self: K, n: string)\nlocal K = {}\nK.m = nil\nlocal k = K\n" +
                "k:m(1)\nk:m('a')\nk:m(2)\n",
        )

    fun shapes(): List<GoldenSource> {
        val generated =
            listOf("param" to PARAM) + NAMED_SHAPES +
                listOf(
                    "annotated1" to annotated(1),
                    "annotated3" to annotated(3),
                    "annotated20" to annotated(20),
                    "paramN20" to paramN(20),
                    "selfN20" to selfN(20),
                    "dotN20" to dotN(20),
                )
        val inline = generated.map { (name, text) -> GoldenSource("shapes/$name.golden", "test.lua", text) }
        val resources =
            listOf("builder", "shapes").map { base ->
                val file = File(System.getProperty("user.dir"), "src/test/resources/corpus/annotated/$base.lua")
                GoldenSource("shapes/annotated-$base.golden", file.name, file.readText())
            }
        return inline + resources
    }

    private val MEMBER_FILES =
        listOf(
            "luacheck-cache" to "luacheck/src/luacheck/cache.lua",
            "zerobrane-commands" to "zerobrane/src/editor/commands.lua",
            "luarocks-zip" to "luarocks/src/luarocks/tools/zip.lua",
            "penlight-Date" to "penlight/lua/pl/Date.lua",
        )

    fun memberFiles(): List<GoldenSource> =
        MEMBER_FILES.map { (name, path) ->
            val file = File(System.getProperty("user.dir"), "${CorpusManifest.CORPUS_DIR}/$path")
            check(file.isFile) { "corpus not fetched: $path (tooling/corpus/fetch-corpus.py)" }
            GoldenSource("member-files/$name.golden", file.name, file.readText())
        }
}
