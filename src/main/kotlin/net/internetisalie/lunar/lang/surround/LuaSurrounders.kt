package net.internetisalie.lunar.lang.surround

import net.internetisalie.lunar.lang.editor.LuaBlockStructure.CARET

/**
 * The seven concrete statement-list surrounders (design §2.4). Each supplies only its template shape; the
 * skeleton (validate/replace/reformat/caret) lives in [LuaStatementSurrounder]. `§CARET§` sits in the header
 * for `if`/`while`/`for`; body templates omit it so the caret lands at the wrapped body's first statement.
 */

/** EDITOR-05-01: wrap in `if <caret> then … end`. */
class LuaIfSurrounder : LuaStatementSurrounder("if") {
    override fun wrap(bodyText: String): String = "if $CARET then\n$bodyText\nend"
}

/** EDITOR-05-02: wrap in `while <caret> do … end`. */
class LuaWhileSurrounder : LuaStatementSurrounder("while") {
    override fun wrap(bodyText: String): String = "while $CARET do\n$bodyText\nend"
}

/** EDITOR-05-02: wrap in `for <caret> = 1, 10 do … end`. */
class LuaNumericForSurrounder : LuaStatementSurrounder("for (numeric)") {
    override fun wrap(bodyText: String): String = "for $CARET = 1, 10 do\n$bodyText\nend"
}

/** EDITOR-05-02: wrap in `for <caret> in pairs(t) do … end`. */
class LuaGenericForSurrounder : LuaStatementSurrounder("for (generic)") {
    override fun wrap(bodyText: String): String = "for $CARET in pairs(t) do\n$bodyText\nend"
}

/** EDITOR-05-03: wrap in an anonymous `function() … end`, caret at the body. */
class LuaFunctionSurrounder : LuaStatementSurrounder("function") {
    override fun wrap(bodyText: String): String = "function()\n$bodyText\nend"
}

/** EDITOR-05-04: wrap in a bare `do … end` scope, caret at the body. */
class LuaDoSurrounder : LuaStatementSurrounder("do") {
    override fun wrap(bodyText: String): String = "do\n$bodyText\nend"
}

/** EDITOR-05-05: wrap in `pcall(function() … end)`, caret at the protected body. */
class LuaPcallSurrounder : LuaStatementSurrounder("pcall") {
    override fun wrap(bodyText: String): String = "pcall(function()\n$bodyText\nend)"
}
