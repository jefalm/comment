package com.example.commentanalyzer.domain.batching

data class BatchLimits(
    val maxCount: Int,
    val maxBytes: Long,
    val maxTokens: Long
)

object BatchPlanner {
    val JEV_LIMITS = BatchLimits(maxCount = 50, maxBytes = 24 * 1024L, maxTokens = 8000L)
    val GLM_LIMITS = BatchLimits(maxCount = 200, maxBytes = 256 * 1024L, maxTokens = 32000L)

    data class Item(val id: String, val text: String)

    fun planBatches(items: List<Item>, limits: BatchLimits): List<List<Item>> {
        val sorted = items.sortedBy { it.id }
        val batches = mutableListOf<List<Item>>()
        var currentBatch = mutableListOf<Item>()
        var currentBytes = 0L
        var currentTokens = 0L

        for (item in sorted) {
            val itemBytes = item.text.toByteArray(Charsets.UTF_8).size.toLong()
            val itemTokens = (item.text.length / 4).coerceAtLeast(1).toLong()

            val exceedsCount = currentBatch.size >= limits.maxCount
            val exceedsBytes = (currentBytes + itemBytes) > limits.maxBytes
            val exceedsTokens = (currentTokens + itemTokens) > limits.maxTokens

            if (currentBatch.isNotEmpty() && (exceedsCount || exceedsBytes || exceedsTokens)) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentBytes = 0L
                currentTokens = 0L
            }

            currentBatch.add(item)
            currentBytes += itemBytes
            currentTokens += itemTokens
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        return batches
    }
}
