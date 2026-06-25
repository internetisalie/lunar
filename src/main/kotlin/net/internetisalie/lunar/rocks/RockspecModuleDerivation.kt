package net.internetisalie.lunar.rocks

import net.internetisalie.lunar.lang.path.SourcePathPattern

object RockspecModuleDerivation {
    /**
     * Derives source path patterns from a rockspec's build.modules table.
     * @param dir The rockspec parent dir (absolute, '/'-normalised, no trailing slash).
     * @param luaModules Map of module name to source path.
     */
    fun derive(dir: String, luaModules: Map<String, String>): List<SourcePathPattern> {
        val roots = LinkedHashSet<String>()

        for ((module, source) in luaModules) {
            val normalizedSource = source.replace('\\', '/')
            val modSlash = module.replace('.', '/')
            
            var root = when {
                normalizedSource.endsWith("$modSlash/init.lua") -> 
                    normalizedSource.removeSuffix("$modSlash/init.lua")
                normalizedSource.endsWith("$modSlash.lua") -> 
                    normalizedSource.removeSuffix("$modSlash.lua")
                else -> {
                    val fallback = normalizedSource.substringBeforeLast('/', "")
                    if (fallback.isEmpty()) "" else "$fallback/"
                }
            }
            
            // Normalise to end with exactly one '/' or be ""
            root = if (root.isNotEmpty() && !root.endsWith('/')) {
                "$root/"
            } else if (root.isEmpty()) {
                ""
            } else {
                root.replace(Regex("/+$"), "/")
            }
            
            roots += root
        }

        val result = mutableListOf<SourcePathPattern>()
        
        // Output order: first-seen, sorted for determinism
        for (root in roots.sorted()) {
            val base = if (root.isEmpty()) {
                if (dir.endsWith("/")) dir else "$dir/"
            } else {
                val dirPrefix = if (dir.endsWith("/")) dir else "$dir/"
                val rootSuffix = if (root.startsWith("/")) root.substring(1) else root
                dirPrefix + rootSuffix
            }
            
            result.add(SourcePathPattern("$base?.lua"))
            result.add(SourcePathPattern("$base?/init.lua"))
        }

        return result
    }
}