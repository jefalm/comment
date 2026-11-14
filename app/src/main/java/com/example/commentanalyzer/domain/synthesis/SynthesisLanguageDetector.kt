package com.example.commentanalyzer.domain.synthesis

import java.text.Normalizer
import java.util.Locale

enum class SynthesisLanguage(val code: String) {
    PORTUGUESE("pt-BR"),
    ENGLISH("en")
}

object SynthesisLanguageDetector {
    private val PORTUGUESE_MARKERS = setOf(
        "nao", "que", "para", "com", "uma", "voce", "voces", "muito",
        "sobre", "tambem", "ainda", "isso", "esse", "essa", "otimo",
        "parabens", "conteudo", "inteligencia", "economia", "mercado",
        "brasileiro", "brasil", "mais", "mas", "como", "esta", "estao",
        "porque", "quando", "pelo", "pela", "canal", "bom", "tudo"
    )

    private val ENGLISH_MARKERS = setOf(
        "the", "and", "this", "that", "with", "from", "your", "about",
        "very", "really", "great", "thanks", "video", "content",
        "artificial", "intelligence", "economy", "market", "technology",
        "have", "will", "would", "they", "what", "more", "just", "like"
    )

    private val TOKEN_REGEX = Regex("\\p{L}+")

    fun detectLanguage(texts: Collection<String>): SynthesisLanguage {
        var ptScore = 0
        var enScore = 0

        for (text in texts) {
            val (pt, en) = scoreText(text)
            ptScore += pt
            enScore += en
        }

        return when {
            ptScore > enScore -> SynthesisLanguage.PORTUGUESE
            enScore > ptScore -> SynthesisLanguage.ENGLISH
            else -> SynthesisLanguage.ENGLISH
        }
    }

    private fun scoreText(text: String): Pair<Int, Int> {
        var pt = 0
        var en = 0

        val lower = text.lowercase(Locale.ROOT)

        // Count Portuguese-specific diacritics as supporting evidence (1 point each)
        for (ch in lower) {
            when (ch) {
                'ã', 'õ', 'ç', 'á', 'é', 'í', 'ó', 'ú', 'â', 'ê', 'ô', 'à' -> pt += 1
            }
        }

        // Normalize text to strip diacritics for token matching
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        val tokens = TOKEN_REGEX.findAll(normalized).map { it.value }.toList()
        for (token in tokens) {
            val isPt = token in PORTUGUESE_MARKERS
            val isEn = token in ENGLISH_MARKERS
            if (isPt && !isEn) {
                pt += 2
            } else if (isEn && !isPt) {
                en += 2
            }
        }

        return Pair(pt, en)
    }
}
