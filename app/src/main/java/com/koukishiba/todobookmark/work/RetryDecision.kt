package com.koukishiba.todobookmark.work

import androidx.work.ListenableWorker.Result
import com.koukishiba.todobookmark.repository.SaveOutcome

/**
 * [SaveOutcome] を WorkManager の [Result] へ変換する。
 * ClientError（invalidを含む）は再送しても直らないため success 扱いとし、
 * 失敗件数は呼び出し元の集計（Success/PartialFailure表示）側で扱う。
 */
fun SaveOutcome.toWorkResult(): Result = when (this) {
    is SaveOutcome.Completed -> Result.success()
    is SaveOutcome.ClientError -> Result.success()
    SaveOutcome.AuthExpired -> Result.failure()
    SaveOutcome.Retryable -> Result.retry()
}
