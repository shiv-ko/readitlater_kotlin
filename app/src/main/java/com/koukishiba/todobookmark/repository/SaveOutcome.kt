package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary

sealed interface SaveOutcome {
    data class Completed(val summary: SaveSummary) : SaveOutcome
    data class ClientError(val summary: SaveSummary) : SaveOutcome
    data object AuthExpired : SaveOutcome
    data object Retryable : SaveOutcome
}

/**
 * バックエンドの応答を [SaveOutcome] へ分類する。
 * `resultsSummary` が取れない場合（400など、`results`を含まない応答）は
 * そのバッチの全件を失敗扱いとして安全側に倒す。
 */
fun classifyResponse(httpCode: Int, resultsSummary: SaveSummary?, chunkSize: Int): SaveOutcome {
    val fallbackSummary = resultsSummary ?: SaveSummary(successCount = 0, failureCount = chunkSize)
    return when {
        httpCode == 200 -> SaveOutcome.Completed(fallbackSummary)
        httpCode == 401 || httpCode == 403 -> SaveOutcome.AuthExpired
        httpCode in 500..599 -> SaveOutcome.Retryable
        else -> SaveOutcome.ClientError(fallbackSummary)
    }
}

/** 通信エラー（オフライン・タイムアウトなど、HTTPレスポンス自体を受け取れなかった場合）は再送対象。 */
fun classifyNetworkFailure(): SaveOutcome = SaveOutcome.Retryable
