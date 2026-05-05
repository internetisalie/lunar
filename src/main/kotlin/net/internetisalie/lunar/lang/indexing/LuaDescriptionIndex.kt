package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.psi.LuaFile
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput

private val LuaDescriptionIndexName: @NonNls ID<String, String> = ID.create("lunar.luacats.descriptions")

class LuaDescriptionIndex : FileBasedIndexExtension<String, String>() {
    private val myExternalizer: DataExternalizer<String> = StringDataExternalizer()
    private val myIndexer: DataIndexer<String, String, FileContent> = Indexer()

    override fun getName(): ID<String, String> = LuaDescriptionIndexName
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = myExternalizer
    override fun getIndexer(): DataIndexer<String, String, FileContent> = myIndexer
    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true
    override fun indexDirectories(): Boolean = false
    
    override fun getInputFilter(): FileBasedIndex.InputFilter = InputFilter()

    private class InputFilter : FileBasedIndex.InputFilter {
        override fun acceptInput(file: VirtualFile): Boolean {
            return file.extension == "lua"
        }
    }

    private class StringDataExternalizer : DataExternalizer<String> {
        override fun save(output: DataOutput, value: String) {
            output.writeUTF(value)
        }

        override fun read(input: DataInput): String {
            return input.readUTF()
        }
    }

    private class Indexer : DataIndexer<String, String, FileContent> {
        override fun map(inputData: FileContent): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val psiFile = inputData.psiFile
            
            if (psiFile !is LuaFile) return result
            
            // Index description text from LuaCATS comments
            // This is a simple implementation that returns an empty map
            // In a full implementation, you would traverse the PSI tree and index all descriptions
            
            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaDescriptionIndexName
    }
}
