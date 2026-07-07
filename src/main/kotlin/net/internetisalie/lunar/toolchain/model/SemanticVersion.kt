package net.internetisalie.lunar.toolchain.model

data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val majorCmp = major.compareTo(other.major)
        if (majorCmp != 0) return majorCmp
        val minorCmp = minor.compareTo(other.minor)
        if (minorCmp != 0) return minorCmp
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(version: String): SemanticVersion? {
            val clean = version.substringBefore('-').trim()
            val parts = clean.split('.')
            if (parts.isEmpty()) return null
            return try {
                val major = parts[0].toInt()
                val minor = parts.getOrNull(1)?.toInt() ?: 0
                val patch = parts.getOrNull(2)?.toInt() ?: 0
                SemanticVersion(major, minor, patch)
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
