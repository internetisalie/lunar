package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.io.File

/**
 * Calculates proximity-based ranking weights for global symbol suggestions.
 *
 * Implements COMP-03-02 Phase 2.2: Ranking Enhancement with:
 * - Module/directory proximity weighting
 * - Recency weighting for recently modified files
 * - @class-specific boost handling
 */
object ProximityCalculator {

    /**
     * Calculate combined proximity and recency weight for a symbol.
     *
     * @param currentFile The file where completion is triggered
     * @param symbolFile The file where the symbol is defined
     * @param isClassType Whether this is a @class-decorated symbol (gets +0.25 boost)
     * @return Combined weight (0.0 to 1.0+)
     */
    fun calculateWeight(
        currentFile: PsiFile,
        symbolFile: PsiFile,
        isClassType: Boolean = false
    ): Double {
        val proximityWeight = calculateProximityWeight(currentFile, symbolFile)
        val recencyBonus = calculateRecencyBonus(symbolFile)
        var combinedWeight = proximityWeight + recencyBonus

        if (isClassType) {
            combinedWeight += 0.25 // Class boost
        }

        return combinedWeight
    }

    /**
     * Calculate proximity weight based on file/module structure.
     *
     * Weights:
     * - Same file: 0.9
     * - Same directory: 0.7
     * - Different module: 0.5
     */
    private fun calculateProximityWeight(currentFile: PsiFile, symbolFile: PsiFile): Double {
        if (currentFile == symbolFile) {
            return 0.9 // Same file
        }

        val currentPath = currentFile.virtualFile?.path ?: return 0.5
        val symbolPath = symbolFile.virtualFile?.path ?: return 0.5

        // Same directory
        val currentDir = File(currentPath).parent
        val symbolDir = File(symbolPath).parent
        if (currentDir != null && currentDir == symbolDir) {
            return 0.7
        }

        // Different module/directory
        return 0.5
    }

    /**
     * Calculate recency bonus based on file modification time.
     *
     * Files modified within the last RECENCY_HOURS get a bonus.
     * This encourages completion suggestions from recently active files.
     *
     * Bonus scale:
     * - Modified < 1 hour ago: +0.15
     * - Modified < 6 hours ago: +0.10
     * - Modified < 24 hours ago: +0.05
     * - Otherwise: 0.0
     *
     * @return Bonus (0.0 to 0.15)
     */
    private fun calculateRecencyBonus(symbolFile: PsiFile): Double {
        val virtualFile = symbolFile.virtualFile ?: return 0.0
        val fileModificationTime = virtualFile.timeStamp
        val currentTime = System.currentTimeMillis()
        val ageMillis = currentTime - fileModificationTime

        // Guard against negative age (future timestamps or clock adjustments)
        if (ageMillis < 0) return 0.0

        return when {
            ageMillis < 1 * 60 * 60 * 1000 -> 0.15 // < 1 hour
            ageMillis < 6 * 60 * 60 * 1000 -> 0.10 // < 6 hours
            ageMillis < 24 * 60 * 60 * 1000 -> 0.05 // < 24 hours
            else -> 0.0
        }
    }
}
