package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import net.internetisalie.lunar.lang.psi.LuaCommentOwner
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.syntax.collectDescriptionText
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
    override fun getVersion(): Int = 2

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

            val fileUrl = inputData.file.url
            PsiTreeUtil.findChildrenOfType(psiFile, LuaCommentOwner::class.java).forEach { owner ->
                val catsComment = owner.catsComment ?: return@forEach
                val descriptionText = collectDescriptionText(catsComment)
                if (descriptionText.isBlank()) return@forEach
                
                val tokens = descriptionText
                    .lowercase()
                    .split(Regex("[^a-zA-Z0-9_]+"))
                    .filter { it.length >= 2 }
                    .distinct()

                if (tokens.isEmpty()) return@forEach

                val rawName = when (owner) {
                    is LuaLocalVarDecl -> owner.attNameList.firstOrNull()?.nameRef?.text
                    is LuaFuncDecl -> owner.funcName.text
                    is LuaLocalFuncDecl -> owner.nameRef.text
                    else -> null
                } ?: owner.text

                val ownerName = rawName.take(50).replace(Regex("[\t|\n\r]"), " ")
                val value = "$ownerName\t$fileUrl\t${owner.textOffset}"

                for (token in tokens) {
                    result.merge(token, value) { existing, new -> "$existing|$new" }
                }
            }
            
            return result
        }
    }

    companion object {
        val KEY: ID<String, String> = LuaDescriptionIndexName
    }
}
