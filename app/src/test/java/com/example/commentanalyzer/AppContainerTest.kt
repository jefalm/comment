package com.example.commentanalyzer

import com.example.commentanalyzer.domain.analysis.FakeDeepAnalyzer
import com.example.commentanalyzer.domain.gate.FakeSemanticGate
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerTest {

    private class TestAppContainer : AppContainer {
        override val semanticGate = FakeSemanticGate()
        override val deepAnalyzer = FakeDeepAnalyzer()
    }

    @Test
    fun testContainerWiringAndFakeReplacement() {
        val container: AppContainer = TestAppContainer()
        assertNotNull(container.semanticGate)
        assertNotNull(container.deepAnalyzer)
        assertTrue(container.semanticGate is FakeSemanticGate)
        assertTrue(container.deepAnalyzer is FakeDeepAnalyzer)
    }
}
