package com.example.commentanalyzer.domain.synthesis

import org.junit.Assert.assertEquals
import org.junit.Test

class SynthesisLanguageDetectorTest {

    @Test
    fun predominantlyPortugueseEvidenceSelectsPtBr() {
        val comments = listOf(
            "Nvidia, Microsoft e OpenAI: o que está por trás da bolha da IA?",
            "Excelente vídeo, parabéns pelo conteúdo incrível sobre inteligência artificial!",
            "Concordo plenamente com o que você disse sobre o mercado de tecnologia no Brasil.",
            "Não acho que seja uma bolha, mas sim uma revolução tecnológica necessária."
        )

        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.PORTUGUESE, detected)
        assertEquals("pt-BR", detected.code)
    }

    @Test
    fun predominantlyEnglishEvidenceSelectsEn() {
        val comments = listOf(
            "Great explanation about Nvidia and OpenAI!",
            "I really think this AI bubble is going to burst very soon.",
            "Thanks for the video, amazing breakdown of the quarterly reports.",
            "Do you think Microsoft will continue investing so heavily in this space?"
        )

        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.ENGLISH, detected)
        assertEquals("en", detected.code)
    }

    @Test
    fun portugueseCommentsContainingEnglishTechnicalTermsSelectsPtBr() {
        val comments = listOf(
            "A inteligência artificial da OpenAI está mudando o mercado brasileiro",
            "Excelente análise sobre Nvidia, GPU e Big Tech"
        )
        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.PORTUGUESE, detected)
        assertEquals("pt-BR", detected.code)
    }

    @Test
    fun englishCommentsContainingPortugueseProperNounsSelectsEn() {
        val comments = listOf(
            "Great analysis of the Brazilian market and Nvidia",
            "The discussion about Brasil was very informative"
        )
        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.ENGLISH, detected)
        assertEquals("en", detected.code)
    }

    @Test
    fun mostlyEmojiNamesNumbersUsesDeterministicEnFallback() {
        val comments = listOf(
            "🔥🔥 OpenAI 2026",
            "Nvidia!!!"
        )
        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.ENGLISH, detected)
        assertEquals("en", detected.code)
    }

    @Test
    fun mixedExactTieUsesDeterministicEnFallback() {
        // "the" (EN marker = 2) vs "que" (PT marker = 2), exact tie -> en
        val comments = listOf("the que")
        val detected = SynthesisLanguageDetector.detectLanguage(comments)
        assertEquals(SynthesisLanguage.ENGLISH, detected)
        assertEquals("en", detected.code)
    }

    @Test
    fun ambiguousOrMixedEvidenceUsesDeterministicFallback() {
        // Numbers and punctuation without language clues
        val ambiguous = listOf(
            "123 456 789",
            "!!! ??? ..."
        )
        val detectedAmbiguous = SynthesisLanguageDetector.detectLanguage(ambiguous)
        assertEquals(SynthesisLanguage.ENGLISH, detectedAmbiguous)
        assertEquals("en", detectedAmbiguous.code)

        // Empty list fallback
        val emptyList = emptyList<String>()
        assertEquals(SynthesisLanguage.ENGLISH, SynthesisLanguageDetector.detectLanguage(emptyList))
    }
}
