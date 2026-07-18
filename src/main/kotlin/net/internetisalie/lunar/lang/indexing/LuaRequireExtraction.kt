package net.internetisalie.lunar.lang.indexing

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.indexing.FileBasedIndex
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * MAINT-30-03 (§2.4): the single canonical require extractor for a consumer.
 *
 * Reads the module names a file `require(...)`s from the [LuaFileBindingsIndexName] record (produced
 * by the indexer's own AST walk — which cannot read itself, so it stays a separate primitive), memoized
 * per file via [CachedValuesManager] and invalidated on [PsiModificationTracker.MODIFICATION_COUNT].
 *
 * Replaces the raw-AST `extractRequires` copies formerly in `LuaNameReference` and
 * `LuaCrossFileCompletionProvider`.
 */
fun fileRequires(file: LuaFile): List<String> {
    val cachedValue: CachedValue<List<String>> = CachedValuesManager.getManager(file.project)
        .createCachedValue(
            {
                val record = FileBasedIndex.getInstance()
                    .getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, GlobalSearchScope.fileScope(file))
                    .firstOrNull()
                CachedValueProvider.Result.create(
                    record?.requires ?: emptyList(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            },
            /* trackValue = */ false,
        )
    return cachedValue.value
}
