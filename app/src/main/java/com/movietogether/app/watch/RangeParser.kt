package com.movietogether.app.watch

/** Single-byte-range subset of RFC 7233; never accepts multiple ranges. */
data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object RangeParser {
    fun parse(header: String?, size: Long): ByteRange? {
        if (size <= 0) return null
        if (header.isNullOrBlank()) return ByteRange(0, size - 1)
        val match = Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(header.trim()) ?: return null
        val left = match.groupValues[1]
        val right = match.groupValues[2]
        if (left.isEmpty() && right.isEmpty()) return null
        if (left.isEmpty()) {
            val suffix = right.toLongOrNull() ?: return null
            if (suffix <= 0) return null
            return ByteRange((size - suffix).coerceAtLeast(0), size - 1)
        }
        val start = left.toLongOrNull() ?: return null
        if (start >= size || start < 0) return null
        val end = if (right.isEmpty()) size - 1 else (right.toLongOrNull() ?: return null).coerceAtMost(size - 1)
        if (end < start) return null
        return ByteRange(start, end)
    }
}
