package com.example.commentanalyzer.data.local.converter

import com.example.commentanalyzer.data.local.entity.ProcessingState
import com.example.commentanalyzer.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testSourceTypeRoundTrip() {
        val original = SourceType.YOUTUBE
        val converted = converters.fromSourceType(original)
        val restored = converters.toSourceType(converted)
        assertEquals(original, restored)
        assertNull(converters.toSourceType(null))
    }

    @Test
    fun testProcessingStateRoundTrip() {
        val original = ProcessingState.READY_JEV
        val converted = converters.fromProcessingState(original)
        val restored = converters.toProcessingState(converted)
        assertEquals(original, restored)
        assertNull(converters.toProcessingState(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnknownEnumThrowsIllegalArgument() {
        converters.toProcessingState("UNKNOWN_STATE_XYZ")
    }
}
