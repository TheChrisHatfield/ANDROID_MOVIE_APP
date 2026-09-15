package com.torrentmovie.core.data

object SearchFilterValidation {
    private val maxSizePattern = Regex(
        """^\d+(\.\d+)?\s*(GB|MB|KB|GiB|MiB|KiB|TB|TiB|B)$""",
        RegexOption.IGNORE_CASE,
    )

    fun isValidMaxSize(value: String): Boolean = maxSizePattern.matches(value)
}
