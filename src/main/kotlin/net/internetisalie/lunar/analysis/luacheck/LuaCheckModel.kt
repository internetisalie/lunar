package net.internetisalie.lunar.analysis.luacheck

class Problem(
    var message: String? = null,
    var file: String,
    var lineStart: Int = 0,
    var columnStart: Int = 0,
    var lineEnd: Int = 0,
    var columnEnd: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Problem) return false

        if (message != other.message) return false
        if (file != other.file) return false
        if (lineStart != other.lineStart) return false
        if (columnStart != other.columnStart) return false
        if (lineEnd != other.lineEnd) return false
        if (columnEnd != other.columnEnd) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message?.hashCode() ?: 0
        result = 31 * result + file.hashCode()
        result = 31 * result + lineStart
        result = 31 * result + columnStart
        result = 31 * result + lineEnd
        result = 31 * result + columnEnd
        return result
    }
}
