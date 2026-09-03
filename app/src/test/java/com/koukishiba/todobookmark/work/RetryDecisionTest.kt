package com.koukishiba.todobookmark.work

import androidx.work.ListenableWorker.Result
import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.repository.SaveOutcome
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryDecisionTest {
    @Test
    fun `Completed は success を返す`() {
        assertTrue(SaveOutcome.Completed(SaveSummary(1, 0)).toWorkResult() is Result.Success)
    }

    @Test
    fun `invalidを含むClientErrorも success を返す（再送しない）`() {
        assertTrue(SaveOutcome.ClientError(SaveSummary(1, 1)).toWorkResult() is Result.Success)
    }

    @Test
    fun `AuthExpired は failure を返す（無限再送しない）`() {
        assertTrue(SaveOutcome.AuthExpired.toWorkResult() is Result.Failure)
    }

    @Test
    fun `Retryable は retry を返す`() {
        assertTrue(SaveOutcome.Retryable.toWorkResult() is Result.Retry)
    }
}
