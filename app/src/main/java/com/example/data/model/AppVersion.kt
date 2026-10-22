package com.example.data.model

/**
 * A version the app can compare.
 *
 * The version of a GitHub release arrives as a tag (`v2.0.1`), the version of the installed build
 * arrives from `BuildConfig` (`2.0.1`), and both may carry a prerelease marker. Comparing them as
 * strings is how an app ends up offering a downgrade: "2.0.10" is smaller than "2.0.9" as a
 * string and larger as a version.
 *
 * So the version is read as what it is, a list of numbers with an optional prerelease marker, and
 * compared the way a version is compared.
 */
data class AppVersion(
    /** The numeric components, in order. */
    val numbers: List<Int>,
    /** The prerelease marker, e.g. `beta.1`. Null for a stable version. */
    val prerelease: String? = null,
    /** The text the version was read from, without the leading `v`. */
    val text: String = ""
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(numbers.size, other.numbers.size)
        for (index in 0 until length) {
            // A version with fewer components is padded with zeros: 2.0 and 2.0.0 are the same
            // version, and 2.0.1 is newer than both.
            val mine = numbers.getOrElse(index) { 0 }
            val theirs = other.numbers.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return comparePrerelease(prerelease, other.prerelease)
    }

    /** Whether this version is strictly newer than [other]. */
    fun isNewerThan(other: AppVersion): Boolean = this > other

    override fun toString(): String = text

    companion object {

        /**
         * Reads a version.
         *
         * @return null when the text is not a version at all. A release whose version cannot be
         *         read is never offered as an update: guessing would be worse than saying nothing.
         */
        fun parse(raw: String?): AppVersion? {
            val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.trim().orEmpty()
            if (cleaned.isEmpty()) return null

            // The numeric part runs until something that is not a number and a dot.
            val numericPart = cleaned.takeWhile { it.isDigit() || it == '.' }
            if (numericPart.isEmpty()) return null

            // An empty component is not a version: "2..1" and "2.0." are both malformed, and a
            // malformed version is never offered as an update.
            val parts = numericPart.split('.')
            if (parts.isEmpty() || parts.any { it.isEmpty() || it.toIntOrNull() == null }) return null
            val numbers = parts.map { it.toInt() }

            // Anything after the numbers is the prerelease marker: "-beta.1", "+build.7", "rc2".
            val rest = cleaned.substring(numericPart.length).trim()
            val prerelease = rest
                .removePrefix("-")
                .removePrefix("+")
                .trim()
                .takeIf { it.isNotEmpty() }

            return AppVersion(
                numbers = numbers,
                prerelease = prerelease,
                text = cleaned
            )
        }

        /**
         * Compares two prerelease markers.
         *
         * A version without a marker is the finished one, so it is newer than the same version
         * with one. Two markers are compared the way semantic versioning compares them: the numeric
         * parts of the identifiers as numbers, the rest as words.
         */
        private fun comparePrerelease(mine: String?, theirs: String?): Int {
            if (mine == null && theirs == null) return 0
            if (mine == null) return 1
            if (theirs == null) return -1

            val myParts = mine.split('.')
            val theirParts = theirs.split('.')
            val length = maxOf(myParts.size, theirParts.size)
            for (index in 0 until length) {
                val myPart = myParts.getOrNull(index) ?: return -1
                val theirPart = theirParts.getOrNull(index) ?: return 1
                val myNumber = myPart.toIntOrNull()
                val theirNumber = theirPart.toIntOrNull()
                val comparison = when {
                    myNumber != null && theirNumber != null -> myNumber.compareTo(theirNumber)
                    myNumber != null -> -1
                    theirNumber != null -> 1
                    else -> myPart.compareTo(theirPart)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}
