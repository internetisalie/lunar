package net.internetisalie.lunar.platform

import com.intellij.execution.process.ProcessOutput
import java.util.regex.Pattern

/**
 * Parses an interpreter's version banner into product/version/full: banner taken from stderr first
 * (else stdout), first line only, split on the leading two whitespace-delimited tokens. Extracted
 * from the deleted `platform/LuaInterpreterService.kt` (TOOLING-05 Phase 5) because its sole
 * surviving readers — `tool/health/LuaToolHealthChecker` and `TestBanner` — are Phase-6 deletions;
 * the probe/banner data itself lives on the TOOLING-01 runtime kinds. Dies with the `tool` package
 * in Phase 6.
 */
data class Banner(
    val product: String,
    val version: String,
    val full: String,
) {
    companion object {
        val VERSION_PATTERN: Pattern = Pattern.compile("^(\\S+)\\s+(\\S+).*$")

        fun create(banner: String): Banner? {
            val matcher = VERSION_PATTERN.matcher(banner)
            if (!matcher.matches()) return null
            return Banner(
                matcher.group(1),
                matcher.group(2),
                banner,
            )
        }

        fun create(processOutput: ProcessOutput): Banner? {
            var outputText = processOutput.stderr.ifEmpty { processOutput.stdout }
            outputText = outputText.trim(' ', '\n', '\t')
            if (outputText.contains('\n')) {
                outputText = outputText.substringBefore('\n')
            }
            return create(outputText)
        }
    }
}
