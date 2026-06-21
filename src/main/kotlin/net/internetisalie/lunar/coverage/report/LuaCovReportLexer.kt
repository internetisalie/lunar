package net.internetisalie.lunar.coverage.report

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class LuaCovReportLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var bufferEnd: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    // High level state:
    // 0: STATE_SEEN_NO_BOUNDARY
    // 1: STATE_SEEN_FIRST_BOUNDARY
    // 2: STATE_SEEN_PATH
    // 3: STATE_IN_CODE
    private var highLevelState: Int = 0
    private var inLineCode: Boolean = false

    companion object {
        val HEADER_BOUNDARY = IElementType("HEADER_BOUNDARY", LuaCovReportLanguage)
        val FILE_PATH = IElementType("FILE_PATH", LuaCovReportLanguage)
        val HIT_COVERED = IElementType("HIT_COVERED", LuaCovReportLanguage)
        val HIT_UNCOVERED = IElementType("HIT_UNCOVERED", LuaCovReportLanguage)
        val HIT_NONE = IElementType("HIT_NONE", LuaCovReportLanguage)
        val LUA_CODE = IElementType("LUA_CODE", LuaCovReportLanguage)
        val NEWLINE = IElementType("NEWLINE", LuaCovReportLanguage)
        val WHITESPACE = IElementType("WHITESPACE", LuaCovReportLanguage)
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        
        // unpack state
        this.highLevelState = (initialState ushr 1) and 0x3
        this.inLineCode = (initialState and 1) != 0
        
        this.tokenType = null
        advance()
    }

    override fun getState(): Int {
        return (highLevelState shl 1) or (if (inLineCode) 1 else 0)
    }

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            tokenType = null
            tokenEnd = bufferEnd
            return
        }

        // Check if we are at the start of a line
        val isStartOfLine = tokenStart == 0 || buffer[tokenStart - 1] == '\n' || (buffer[tokenStart - 1] == '\r' && buffer[tokenStart] != '\n')

        if (isStartOfLine) {
            advanceStartOfLine()
        } else {
            advanceMiddleOfLine()
        }
    }

    private fun advanceStartOfLine() {
        inLineCode = false // Reset line-level state
        if (isBoundaryLine(tokenStart)) {
            parseBoundaryLine()
        } else if (highLevelState == 1) {
            parseFilePathLine()
        } else {
            parseCodeLineStart()
        }
    }

    private fun parseBoundaryLine() {
        val count = getBoundaryLength(tokenStart)
        tokenType = HEADER_BOUNDARY
        tokenEnd = tokenStart + count
        // Update state machine
        highLevelState = when (highLevelState) {
            2 -> 3 // STATE_SEEN_PATH -> STATE_IN_CODE
            else -> 1 // others -> STATE_SEEN_FIRST_BOUNDARY
        }
    }

    private fun parseFilePathLine() {
        val lineEnd = findLineEnd(tokenStart)
        tokenType = FILE_PATH
        tokenEnd = lineEnd
        highLevelState = 2 // STATE_SEEN_FIRST_BOUNDARY -> STATE_SEEN_PATH
    }

    private fun parseCodeLineStart() {
        val lineEnd = findLineEnd(tokenStart)
        val prefixLen = minOf(5, lineEnd - tokenStart)
        if (prefixLen == 0) {
            // Empty line, expect newline
            parseNewlineOrWhitespace()
        } else {
            val prefixStr = buffer.subSequence(tokenStart, tokenStart + prefixLen).toString()
            tokenType = when {
                prefixStr.matches(Regex("""^\*+0?\s*$""")) -> HIT_UNCOVERED
                prefixStr.isBlank() -> HIT_NONE
                prefixStr.matches(Regex("""^\s*\d+\s?$""")) -> HIT_COVERED
                else -> HIT_NONE
            }
            tokenEnd = tokenStart + prefixLen
            inLineCode = true
        }
    }

    private fun advanceMiddleOfLine() {
        if (inLineCode) {
            val lineEnd = findLineEnd(tokenStart)
            if (tokenStart < lineEnd) {
                tokenType = LUA_CODE
                tokenEnd = lineEnd
                inLineCode = false
                return
            }
        }
        // If not inLineCode, or we reached lineEnd, expect newline or whitespace
        parseNewlineOrWhitespace()
    }

    private fun parseNewlineOrWhitespace() {
        val c = buffer[tokenStart]
        if (c == '\r') {
            if (tokenStart + 1 < bufferEnd && buffer[tokenStart + 1] == '\n') {
                tokenType = NEWLINE
                tokenEnd = tokenStart + 2
            } else {
                tokenType = NEWLINE
                tokenEnd = tokenStart + 1
            }
        } else if (c == '\n') {
            tokenType = NEWLINE
            tokenEnd = tokenStart + 1
        } else if (c.isWhitespace()) {
            // Read all non-newline whitespace
            var idx = tokenStart
            while (idx < bufferEnd && buffer[idx].isWhitespace() && buffer[idx] != '\r' && buffer[idx] != '\n') {
                idx++
            }
            tokenType = WHITESPACE
            tokenEnd = idx
        } else {
            // Fallback for safety (e.g. if we get characters outside expected zones)
            val lineEnd = findLineEnd(tokenStart)
            tokenType = LUA_CODE
            tokenEnd = if (lineEnd > tokenStart) lineEnd else tokenStart + 1
        }
    }

    private fun isBoundaryLine(start: Int): Boolean {
        var idx = start
        while (idx < bufferEnd && buffer[idx] == '=') {
            idx++
        }
        val count = idx - start
        if (count < 10) return false
        return idx == bufferEnd || buffer[idx] == '\n' || buffer[idx] == '\r'
    }

    private fun getBoundaryLength(start: Int): Int {
        var idx = start
        while (idx < bufferEnd && buffer[idx] == '=') {
            idx++
        }
        return idx - start
    }

    private fun findLineEnd(start: Int): Int {
        var idx = start
        while (idx < bufferEnd && buffer[idx] != '\n' && buffer[idx] != '\r') {
            idx++
        }
        return idx
    }
}
