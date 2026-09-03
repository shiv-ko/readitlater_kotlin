package com.koukishiba.todobookmark.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koukishiba.todobookmark.auth.AuthManager
import com.koukishiba.todobookmark.auth.AuthState
import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.network.ApiClient
import com.koukishiba.todobookmark.repository.BookmarkRepository
import com.koukishiba.todobookmark.repository.SaveOutcome
import com.koukishiba.todobookmark.repository.SaveProgress
import com.koukishiba.todobookmark.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SaveUiState {
    data object Idle : SaveUiState
    data class Saving(val processed: Int, val total: Int) : SaveUiState
    data class Success(val summary: SaveSummary) : SaveUiState
    data class PartialFailure(val summary: SaveSummary) : SaveUiState
    data object AuthRequired : SaveUiState
    data object LoginRequired : SaveUiState
    data object NetworkQueued : SaveUiState
    data object NoUrls : SaveUiState
}

class HomeViewModel(
    private val authManager: AuthManager = AuthManager(),
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _saveState = MutableStateFlow<SaveUiState>(SaveUiState.Idle)
    val saveState: StateFlow<SaveUiState> = _saveState.asStateFlow()

    fun refreshAuthState() {
        viewModelScope.launch {
            _authState.value = authManager.currentState()
        }
    }

    fun signIn(activity: Activity, onSignedIn: () -> Unit) {
        viewModelScope.launch {
            runCatching { authManager.signIn(activity) }
                .onSuccess {
                    _authState.value = authManager.currentState()
                    onSignedIn()
                }
                .onFailure {
                    _saveState.value = SaveUiState.LoginRequired
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _authState.value = AuthState.SignedOut
        }
    }

    fun save(context: Context, urls: List<String>) {
        if (urls.isEmpty()) {
            _saveState.value = SaveUiState.NoUrls
            return
        }
        _saveState.value = SaveUiState.Saving(processed = 0, total = urls.size)
        viewModelScope.launch {
            val repository = BookmarkRepository(ApiClient.create(authManager))
            val result = repository.save(urls) { progress: SaveProgress ->
                _saveState.value = SaveUiState.Saving(progress.processed, progress.total)
            }
            _saveState.value = when (val outcome = result.outcome) {
                is SaveOutcome.Completed ->
                    if (outcome.summary.failureCount > 0) {
                        SaveUiState.PartialFailure(outcome.summary)
                    } else {
                        SaveUiState.Success(outcome.summary)
                    }
                is SaveOutcome.ClientError -> SaveUiState.PartialFailure(outcome.summary)
                SaveOutcome.AuthExpired -> SaveUiState.AuthRequired
                SaveOutcome.Retryable -> {
                    WorkScheduler.enqueueRetry(context, result.pendingUrls, "inbox")
                    SaveUiState.NetworkQueued
                }
            }
        }
    }
}
