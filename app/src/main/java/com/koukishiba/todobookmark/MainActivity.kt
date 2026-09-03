package com.koukishiba.todobookmark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.koukishiba.todobookmark.auth.AuthState
import com.koukishiba.todobookmark.ui.HomeViewModel
import com.koukishiba.todobookmark.ui.SaveScreen
import com.koukishiba.todobookmark.ui.SaveUiState
import com.koukishiba.todobookmark.ui.SetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private var pendingUrls: List<String> = emptyList()
    private var isShareIntent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authState by viewModel.authState.collectAsStateWithLifecycle()
                    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

                    LaunchedEffect(saveState) {
                        if (saveState == SaveUiState.LoginRequired) {
                            delay(1500)
                            finish()
                        }
                    }

                    if (isShareIntent) {
                        SaveScreen(
                            state = saveState,
                            onRetry = { startSaving() },
                            onClose = { finish() },
                        )
                    } else {
                        SetupScreen(
                            authState = authState,
                            onSignIn = { viewModel.signIn(this@MainActivity) {} },
                            onSignOut = { viewModel.signOut() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        isShareIntent = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
        if (!isShareIntent) {
            // ランチャーアイコンからの起動: SetupScreen は authState の更新を待たず、
            // Flow が後から更新されれば再コンポーズされるため fire-and-forget で問題ない。
            viewModel.refreshAuthState()
            return
        }

        pendingUrls = ShareIntentParser.extractUrls(ShareIntentReader.readTexts(intent))
        startSavingWithAuthCheck()
    }

    private fun startSavingWithAuthCheck() {
        // シェアIntentのコールドスタートでは authState の StateFlow がまだ既定値
        // (SignedOut) のままの可能性があるため、判定前に必ず最新状態を取得し直す。
        lifecycleScope.launch {
            val state = viewModel.ensureFreshAuthState()
            if (state is AuthState.SignedIn) {
                startSaving()
            } else {
                viewModel.signIn(this@MainActivity) { startSaving() }
            }
        }
    }

    private fun startSaving() {
        viewModel.save(applicationContext, pendingUrls)
    }
}
