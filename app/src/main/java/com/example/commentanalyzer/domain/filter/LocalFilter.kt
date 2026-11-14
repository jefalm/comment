package com.example.commentanalyzer.domain.filter

data class FilterResult(
    val outcome: FilterOutcome,
    val ruleIds: List<String>,
    val filterVersion: Int = 1
)

enum class FilterOutcome {
    PASS, REJECT, REVIEW
}

object LocalFilter {
    fun evaluate(normalizedText: String): FilterResult {
        val rules = mutableListOf<String>()
        if (normalizedText.isBlank()) {
            rules.add("RULE_EMPTY_TEXT")
            return FilterResult(FilterOutcome.REJECT, rules)
        }
        if (Regex("(.)\\1{9,}").containsMatchIn(normalizedText)) {
            rules.add("RULE_REPEATED_CHARS")
            return FilterResult(FilterOutcome.REJECT, rules)
        }
        val linkCount = Regex("https?://\\S+").findAll(normalizedText).count()
        if (linkCount > 3) {
            rules.add("RULE_EXCESSIVE_LINKS")
            return FilterResult(FilterOutcome.REVIEW, rules)
        }
        rules.add("RULE_PASS")
        return FilterResult(FilterOutcome.PASS, rules)
    }
}
