package com.splitandmerge.mkvslice.domain.cleanup

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleCleaner @Inject constructor(
    private val cleanupRepository: CleanupRepository
) {
    private var cachedPatterns: List<CleanupPatternEntity> = DEFAULT_CLEANUP_PATTERNS

    init {
        CoroutineScope(Dispatchers.IO).launch {
            cleanupRepository.observePatterns().collectLatest { patterns ->
                cachedPatterns = patterns
            }
        }
    }

    fun cleanTitle(filename: String): String {
        var baseName = filename.substringBeforeLast(".")
        val currentPatterns = cachedPatterns

        for (pattern in currentPatterns.filter { it.enabled }) {
            try {
                baseName = Regex(pattern.regex, RegexOption.IGNORE_CASE).replace(baseName, pattern.replacement)
            } catch (e: Exception) {
                // Keep moving if an invalid custom regex is stored
            }
        }

        val fallback = filename.substringBeforeLast(".")
        return if (baseName.length >= 2) baseName.trim() else fallback.trim()
    }
}

fun cleanTitleWith(baseName: String, patterns: List<CleanupPatternEntity>): String {
    var cleaned = baseName
    val sortedPatterns = patterns.sortedBy { it.orderIndex }

    for (pattern in sortedPatterns) {
        try {
            cleaned = Regex(pattern.regex, RegexOption.IGNORE_CASE).replace(cleaned, pattern.replacement)
        } catch (e: Exception) {
            // Keep moving if an invalid custom regex is stored
        }
    }

    return if (cleaned.length >= 2) cleaned.trim() else baseName.trim()
}
