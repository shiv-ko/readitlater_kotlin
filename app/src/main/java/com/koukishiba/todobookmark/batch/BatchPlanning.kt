package com.koukishiba.todobookmark.batch

import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus

private const val MAX_BATCH_SIZE = 20

/** URL一覧を、API上限（既定20件）ごとのバッチへ順序を保ったまま分割する。 */
fun chunkUrls(urls: List<String>, maxBatchSize: Int = MAX_BATCH_SIZE): List<List<String>> {
    if (urls.isEmpty()) return emptyList()
    return urls.chunked(maxBatchSize)
}

data class SaveSummary(
    val successCount: Int,
    val failureCount: Int,
) {
    operator fun plus(other: SaveSummary): SaveSummary =
        SaveSummary(successCount + other.successCount, failureCount + other.failureCount)

    companion object {
        val ZERO = SaveSummary(successCount = 0, failureCount = 0)
    }
}

/** created / existing を成功、invalid を失敗として集計する。 */
fun List<BatchResultItem>.toSaveSummary(): SaveSummary {
    val success = count { it.status == BatchResultStatus.CREATED || it.status == BatchResultStatus.EXISTING }
    val failure = count { it.status == BatchResultStatus.INVALID }
    return SaveSummary(successCount = success, failureCount = failure)
}
