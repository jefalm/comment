package com.example.commentanalyzer.domain.adapter

import com.example.commentanalyzer.domain.analysis.DisabledDeepAnalyzer
import com.example.commentanalyzer.domain.analysis.FakeDeepAnalyzer
import com.example.commentanalyzer.domain.gate.DisabledSemanticGate
import com.example.commentanalyzer.domain.gate.FakeSemanticGate
import com.example.commentanalyzer.domain.model.AnalysisConfig
import com.example.commentanalyzer.domain.model.AnalysisInput
import com.example.commentanalyzer.domain.model.GateConfig
import com.example.commentanalyzer.domain.model.GateInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptersTest {

    @Test
    fun testDisabledAdaptersReturnDisabledOutcome() = runBlocking {
        val disabledGate = DisabledSemanticGate()
        val gateResult = disabledGate.screen(listOf(GateInput("1", "test", null, null)), GateConfig(1L, "jev-1.14", 0.3, 0.7))
        assertEquals("SEMANTIC_GATE_DISABLED", gateResult.error)

        val disabledAnalyzer = DisabledDeepAnalyzer()
        val analysisResult = disabledAnalyzer.analyze(listOf(AnalysisInput("1", "test")), AnalysisConfig(1L, "glm-5.3"))
        assertEquals("DEEP_ANALYZER_DISABLED", analysisResult.error)
    }

    @Test
    fun testFakeAdaptersReturnDeterministicResults() = runBlocking {
        val fakeGate = FakeSemanticGate(0.8)
        val gateResult = fakeGate.screen(listOf(GateInput("1", "test", null, null)), GateConfig(1L, "jev-1.14", 0.3, 0.7))
        assertEquals(1, gateResult.results.size)
        assertEquals(0.8, gateResult.results[0].probability, 0.0001)

        val fakeAnalyzer = FakeDeepAnalyzer()
        val analysisResult = fakeAnalyzer.analyze(listOf(AnalysisInput("1", "test")), AnalysisConfig(1L, "glm-5.3"))
        assertEquals(1, analysisResult.results.size)
        assertEquals("positive", analysisResult.results[0].sentiment)
    }
}
