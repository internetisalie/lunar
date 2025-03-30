package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import net.internetisalie.lunar.lang.LuaFileType
import java.util.*

const val ExternalIdPrefix = "net.internetisalie.lunar"

class LuaFileInputFilter : FileBasedIndex.InputFilter {
    override fun acceptInput(file: VirtualFile): Boolean {
        if (!file.url.startsWith("file:")) return false
        return myFileTypes.contains(file.fileType)
    }

    companion object {
        private val myFileTypes: List<FileType> = java.util.List.of<FileType>(LuaFileType)
    }
}

abstract class ForwardIndexer<V> : DataIndexer<Int, V, FileContent> {
    override fun map(inputData: FileContent): Map<Int, V> {
        val value = computeValue(inputData) ?: return emptyMap()
        return Collections.singletonMap(0, value)
    }

    protected abstract fun computeValue(inputData: FileContent): V?

    companion object {
        const val KEY: Int = 0
    }
}

data class Dotted<T>(val value: T, val prev: Dotted<T>?) {
    override fun toString(): String {
        val sb = StringBuilder()
        appendString(sb)
        return sb.toString()
    }

    private fun appendString(to: StringBuilder) {
        if (prev != null) {
            prev.appendString(to)
            to.append(".")
        }
        to.append(value.toString())
    }
}
