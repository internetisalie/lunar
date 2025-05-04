package net.internetisalie.lunar.lang.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

data class FileUserData<T>(val hash: Int, val data: T)

fun <T> Key<FileUserData<T>>.cacheFileUserData(element: PsiElement, calc: (psiFile: PsiFile) -> T): T {
    val psiFile = element.containingFile
    val documentHash = psiFile.fileDocument.text.hashCode()

    val existing = psiFile.getUserData(this)
    if (existing != null && documentHash == existing.hash) {
        return existing.data
    }

    val fresh = calc(psiFile)
    psiFile.putUserData(this, FileUserData(documentHash, fresh))
    return fresh
}