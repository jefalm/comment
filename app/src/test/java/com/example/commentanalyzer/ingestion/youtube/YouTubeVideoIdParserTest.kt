package com.example.commentanalyzer.ingestion.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeVideoIdParserTest {
    @Test
    fun acceptsExistingRawAndUrlForms() {
        val cases = listOf(
            "abcdefghijk" to "abcdefghijk",
            "https://youtu.be/abcdefghijk" to "abcdefghijk",
            "https://youtube.com/watch?v=abcdefghijk" to "abcdefghijk",
            "https://www.youtube.com/watch?v=abcdefghijk" to "abcdefghijk",
            "https://m.youtube.com/watch?v=abcdefghijk" to "abcdefghijk",
            "https://www.youtube.com/shorts/abcdefghijk" to "abcdefghijk"
        )

        cases.forEach { (input, expected) ->
            assertEquals(input, expected, YouTubeVideoIdParser.parse(input))
        }
    }

    @Test
    fun rejectsUnsupportedAndAmbiguousForms() {
        listOf(
            "https://youtube.com.evil/watch?v=abcdefghijk",
            "http://youtube.com/watch?v=abcdefghijk",
            "https://youtu.be/abcdefghijk/extra",
            "https://youtube.com/watch?v=abcdefghijk&v=mnopqrstuvw",
            "abcdefghij"
        ).forEach { input ->
            assertNull(input, YouTubeVideoIdParser.parse(input))
        }
    }
}