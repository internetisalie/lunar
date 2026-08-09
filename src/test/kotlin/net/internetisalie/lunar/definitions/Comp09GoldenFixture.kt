package net.internetisalie.lunar.definitions

/**
 * The fixture COMP-09's golden is recorded against: **one receiver per BINDING SHAPE**, each in its
 * own file.
 *
 * The standing lesson from planning (requirements.md, "The lesson worth carrying into Phase 0") is
 * that a golden is only as good as the binding shapes in it. Three de-risking rounds each missed a
 * real defect because their fixtures varied *member style* — dot, colon, `@field` — while holding
 * *binding shape* fixed at `R = {}` with members assigned separately, the one shape in which the
 * index and the type graph agree. BL-2 (`Config = { host, port }` → index `[]`, graph `[host,
 * port]`) and round five's BLOCKER 2 (`M = { VERSION } + function M.f()`, where an
 * emptiness-triggered fallback never fires and `VERSION` is silently lost) were both found by adding
 * a shape, not a member style.
 *
 * So adding a shape here is the cheap move; adding another member style is not. The eight shapes
 * requirements.md names are all present, plus the two receivers the `sourceElement` case needs:
 *
 * | receiver | shape |
 * | :-- | :-- |
 * | `wx` | `R = {}` + syntactic members |
 * | `Config` | `R = { a = 1 }` — pure table literal |
 * | `M` | `R = { a = 1 }` + syntactic — mixed |
 * | `Busted` | `R = require(x)` — opaque |
 * | `OM` | `R = require(x)` + syntactic — opaque and extended |
 * | `wxFrame` | `@class` on a **local**, dot *and* colon members, `@field` |
 * | `AllColon` | `@class` on a local, **every** member colon-declared |
 * | `Shapes` | `a.b.c = v` — the nested qualifier BUG-430 flattens |
 * | `Base` / `Derived` | `@class D : Base`, `@field`, and the override/implement pair |
 * | `luassert` | the `require` target the two opaque shapes bind to |
 *
 * `Busted` is deliberately **not** named `assert`: the real busted idiom binds the stdlib name, and
 * a golden that shares a receiver with a bundled stub would record whichever declaring file the
 * platform's unordered `getContainingFiles` happened to return. The shape is what is under test.
 */
object Comp09GoldenFixture {
    /** A golden receiver and the binding shape it exists to pin. */
    data class Receiver(
        val name: String,
        val shape: String,
    )

    val receivers: List<Receiver> =
        listOf(
            Receiver("wx", "bare-table binding plus syntactic members: R = {} + R.f = v + function R.f()"),
            Receiver("Config", "pure table literal: R = { a = 1 }"),
            Receiver("M", "table literal plus syntactic: R = { a = 1 } + function R.f()"),
            Receiver("Busted", "opaque binding: R = require(x)"),
            Receiver("OM", "opaque binding plus syntactic: R = require(x) + function R.f()"),
            Receiver("luassert", "@class on a local, dot member — the require target"),
            Receiver("wxFrame", "@class on a local, dot AND colon members, @field"),
            Receiver("AllColon", "@class on a local, every member colon-declared"),
            Receiver("Shapes", "nested qualifier: a.b.c = v"),
            Receiver("Base", "@class on a bare global, @field signature plus a colon method"),
            Receiver("Derived", "@class D : Base on a bare global, @field plus an overriding method"),
        )

    fun files(): Map<String, String> =
        mapOf(
            "wx.lua" to WX,
            "config.lua" to CONFIG,
            "mixed.lua" to MIXED,
            "luassert.lua" to LUASSERT,
            "busted.lua" to BUSTED,
            "opaquemix.lua" to OPAQUE_MIX,
            "wxframe.lua" to WX_FRAME,
            "allcolon.lua" to ALL_COLON,
            "shapes.lua" to SHAPES,
            "base.lua" to BASE,
            "derived.lua" to DERIVED,
        )

    private val WX =
        """
        ---@meta

        ---@class wx
        wx = {}

        ---@type number
        wx.wxID_ANY = nil

        ---@param filename string
        ---@return boolean
        function wx.wxFileExists(filename) end

        return wx
        """.trimIndent()

    private val CONFIG =
        """
        ---@meta

        Config = {
            host = "localhost",
            port = 6379,
        }

        return Config
        """.trimIndent()

    private val MIXED =
        """
        ---@meta

        M = { VERSION = "1.0", DEBUG = false }

        ---@return boolean
        function M.f() end

        return M
        """.trimIndent()

    private val LUASSERT =
        """
        ---@meta

        ---@class luassert
        local luassert = {}

        ---@param n string
        function luassert.unregister(n) end

        return luassert
        """.trimIndent()

    private val BUSTED =
        """
        ---@meta

        Busted = require("luassert")
        """.trimIndent()

    private val OPAQUE_MIX =
        """
        ---@meta

        OM = require("luassert")

        ---@return boolean
        function OM.extra() end

        return OM
        """.trimIndent()

    private val WX_FRAME =
        """
        ---@meta

        ---@class wxFrame
        ---@field title string
        local wxFrame = {}

        ---@param show boolean
        ---@return boolean
        function wxFrame:Show(show) end

        ---@return string
        function wxFrame:GetTitle() end

        ---@return number
        function wxFrame.staticCount() end
        """.trimIndent()

    private val ALL_COLON =
        """
        ---@meta

        ---@class AllColon
        local AllColon = {}

        ---@return string
        function AllColon:alpha() end

        ---@return string
        function AllColon:beta() end
        """.trimIndent()

    private val SHAPES =
        """
        ---@meta

        ---@class Shapes
        Shapes = {}

        Shapes.nested = {}
        Shapes.nested.deep = 1
        Shapes.direct = 2

        function Shapes.plain() end

        return Shapes
        """.trimIndent()

    private val BASE =
        """
        ---@meta

        ---@class Base
        ---@field inheritedField string
        ---@field onClose fun(): nil
        Base = {}

        ---@return boolean
        function Base:Show() end

        ---@return boolean
        function Base.inheritedFn() end

        return Base
        """.trimIndent()

    private val DERIVED =
        """
        ---@meta

        ---@class Derived : Base
        ---@field ownField number
        Derived = {}

        ---@return boolean
        function Derived:Show() end

        ---@return boolean
        function Derived.ownFn() end

        return Derived
        """.trimIndent()
}
