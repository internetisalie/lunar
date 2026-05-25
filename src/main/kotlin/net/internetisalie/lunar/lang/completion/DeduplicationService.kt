package net.internetisalie.lunar.lang.completion

import com.intellij.psi.PsiElement

/**
 * Handles deduplication of global symbol suggestions with PSI element identity tracking.
 *
 * Implements COMP-03-02 Phase 2.2: Improved Deduplication
 *
 * Tracks symbols by (file, name) pairs to allow same-named symbols from different files
 * while still eliminating pure duplicates.
 */
object DeduplicationService {

    /**
     * Deduplicate symbols with improved tracking by PSI element identity.
     *
     * Strategy:
     * - Group symbols by name
     * - Within each name group, keep only the highest-weight symbol per file
     * - Return all symbols, sorted by weight (descending)
     *
     * This allows the same symbol name to appear from different files (ranked by proximity),
     * while eliminating duplicate entries from the same file.
     *
     * @param symbols List of global symbol completions
     * @return Deduplicated list sorted by weight (descending)
     */
    fun deduplicateByPsiIdentity(
        symbols: List<GlobalSymbolRankingService.GlobalSymbolCompletion>
    ): List<GlobalSymbolRankingService.GlobalSymbolCompletion> {
        // Group by (name, file path) for deduplication
        val symbolsByKey = mutableMapOf<SymbolKey, GlobalSymbolRankingService.GlobalSymbolCompletion>()

        symbols.forEach { symbol ->
            val key = SymbolKey(
                name = symbol.name,
                filePath = symbol.psiElement.containingFile?.virtualFile?.path ?: return@forEach
            )

            // Keep highest weight symbol for this (name, file) pair
            val existing = symbolsByKey[key]
            if (existing == null || symbol.proximityWeight > existing.proximityWeight) {
                symbolsByKey[key] = symbol
            }
        }

        // Sort by weight (descending)
        return symbolsByKey.values.sortedByDescending { it.proximityWeight }
    }

    /**
     * Deduplicate by name only (simpler strategy).
     *
     * This is the Phase 2.1 behavior: keeps only the highest-weight symbol per name.
     *
     * @param symbols List of global symbol completions
     * @return Deduplicated list sorted by weight (descending)
     */
    fun deduplicateByNameOnly(
        symbols: List<GlobalSymbolRankingService.GlobalSymbolCompletion>
    ): List<GlobalSymbolRankingService.GlobalSymbolCompletion> {
        val deduped = mutableMapOf<String, GlobalSymbolRankingService.GlobalSymbolCompletion>()
        symbols.forEach { symbol ->
            val existing = deduped[symbol.name]
            if (existing == null || symbol.proximityWeight > existing.proximityWeight) {
                deduped[symbol.name] = symbol
            }
        }

        return deduped.values.sortedByDescending { it.proximityWeight }
    }

    /**
     * Key for tracking symbols by name and file path.
     */
    private data class SymbolKey(
        val name: String,
        val filePath: String
    )
}
