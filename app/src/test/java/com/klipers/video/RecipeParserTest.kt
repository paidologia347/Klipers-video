package com.klipers.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeParserTest {

    @Test
    fun parseTimestamp_supportsCommonFormats() {
        assertEquals(5, RecipeParser.parseTimestamp("5"))
        assertEquals(65, RecipeParser.parseTimestamp("01:05"))
        assertEquals(3661, RecipeParser.parseTimestamp("01:01:01"))
    }

    @Test
    fun parseLine_returnsNullWhenInvalid() {
        assertNull(RecipeParser.parseLine("abc"))
        assertNull(RecipeParser.parseLine("00:10-00:05"))
    }

    @Test
    fun parseRecipe_parsesMultipleLines() {
        val result = RecipeParser.parseRecipe(
            """
            00:00:01-00:00:03
            00:00:05,00:00:08
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(Segment(1, 3), result[0])
        assertEquals(Segment(5, 8), result[1])
    }
}
