package com.koukishiba.todobookmark.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koukishiba.todobookmark.R

@Composable
fun SaveScreen(
    state: SaveUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onReLogin: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        when (state) {
            SaveUiState.Idle -> Unit
            is SaveUiState.Saving -> {
                Text(stringResource(R.string.detected_links, state.total))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(
                        if (state.total > 20) {
                            stringResource(R.string.saving_progress, state.processed, state.total)
                        } else {
                            stringResource(R.string.saving)
                        },
                    )
                }
            }
            is SaveUiState.Success -> {
                Text(stringResource(R.string.save_success, state.summary.successCount))
                Button(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            is SaveUiState.PartialFailure -> {
                val total = state.summary.successCount + state.summary.failureCount
                Text(stringResource(R.string.save_partial_failure_title))
                Text(stringResource(R.string.save_partial_failure_summary, state.summary.successCount, total))
                Text(stringResource(R.string.save_partial_failure_count, state.summary.failureCount))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    Button(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
            }
            is SaveUiState.AuthRequired -> {
                Text(stringResource(R.string.session_expired))
                Button(onClick = { onReLogin(state.pendingUrls) }) { Text(stringResource(R.string.sign_in)) }
            }
            SaveUiState.LoginRequired -> Text(stringResource(R.string.login_required))
            SaveUiState.NetworkQueued -> {
                Text(stringResource(R.string.save_queued_for_retry))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    Button(onClick = onClose) { Text(stringResource(R.string.close)) }
                }
            }
            SaveUiState.NoUrls -> Text(stringResource(R.string.no_links))
        }
    }
}
