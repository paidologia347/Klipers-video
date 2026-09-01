package com.klipers.video

import kotlin.math.roundToInt

data class Segment(val startSeconds: Int, val endSeconds: Int) {
    init {
        require(startSeconds >= 0) { "startSeconds must be >= 0" }
        require(endSeconds > startSeconds) { "endSeconds must be > startSeconds" }
    }
}

object RecipeParser {
    private val segmentSeparator = Regex("\\s*(?:-|,|>|\\|)\\s*")

    fun parseRecipe(recipeText: String): List<Segment> {
        return recipeText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexedNotNull { index, line ->
                parseLine(line) ?: throw IllegalArgumentException("Format baris tidak valid pada baris ${index + 1}: '$line'")
            }
            .toList()
    }

    fun parseLine(line: String): Segment? {
        val normalized = line
            .replace(Regex("^\\s*\\d+[.)]\\s*"), "")
            .trim()

        val parts = normalized.split(segmentSeparator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (parts.size != 2) return null

        val start = parseTimestamp(parts[0]) ?: return null
        val end = parseTimestamp(parts[1]) ?: return null
        if (end <= start) return null

        return Segment(start, end)
    }

    fun parseTimestamp(raw: String): Int? {
        val value = raw.trim().replace(',', '.')
        if (value.isEmpty()) return null

        val chunks = value.split(":")
        if (chunks.any { it.isBlank() }) return null

        return try {
            val seconds = when (chunks.size) {
                1 -> chunks[0].toDouble()
                2 -> chunks[0].toDouble() * 60 + chunks[1].toDouble()
                3 -> chunks[0].toDouble() * 3600 + chunks[1].toDouble() * 60 + chunks[2].toDouble()
                else -> return null
            }
            if (seconds < 0) return null
            seconds.roundToInt()
        } catch (_: NumberFormatException) {
            null
        }
    }
}
