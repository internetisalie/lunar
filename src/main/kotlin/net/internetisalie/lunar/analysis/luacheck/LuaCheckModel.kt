package net.internetisalie.lunar.analysis.luacheck

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

class ProblemSummary

class ProblemFile(
    var file : String
) {
    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProblemFile) return false

        if (file != other.file) return false

        return true
    }
}

class ProblemFilter(var file:String? = null, var code:String? = null) {
    fun filter(problems : Collection<Problem>) : Collection<Problem> {
        var result = problems
        if (file != null) result = result.filter { it.file == file }
        if (code != null) result = result.filter { it.code == code }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProblemFilter) return false

        if (file != other.file) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = file?.hashCode() ?: 0
        result = 31 * result + (code?.hashCode() ?: 0)
        return result
    }
}

class Problem(
    var name: String? = null,
    var code: String? = null,
    var message: String? = null,
    var file: String,
    var lineStart: Int = 0,
    var columnStart: Int = 0,
    var lineEnd: Int = 0,
    var columnEnd: Int = 0,
    var absFile: String? = null,
    var psiFile: PsiFile? = null,
) {
    val problemFile: ProblemFile
        get() = ProblemFile(file)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Problem) return false

        if (name != other.name) return false
        if (code != other.code) return false
        if (message != other.message) return false
        if (file != other.file) return false
        if (lineStart != other.lineStart) return false
        if (columnStart != other.columnStart) return false
        if (lineEnd != other.lineEnd) return false
        if (columnEnd != other.columnEnd) return false
        if (absFile != other.absFile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (code?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + file.hashCode()
        result = 31 * result + lineStart
        result = 31 * result + columnStart
        result = 31 * result + lineEnd
        result = 31 * result + columnEnd
        result = 31 * result + (absFile?.hashCode() ?: 0)
        return result
    }
}