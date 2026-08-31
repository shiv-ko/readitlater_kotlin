package com.koukishiba.todobookmark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var screenState by mutableStateOf(HomeScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenState = intent.toScreenState()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TodoBookmarkScreen(screenState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        screenState = intent.toScreenState()
    }

    private fun Intent.toScreenState(): HomeScreenState {
        val isShareIntent = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        val urls = if (isShareIntent) {
            ShareIntentParser.extractUrls(ShareIntentReader.readTexts(this))
        } else {
            emptyList()
        }
        return HomeScreenState(isShareIntent = isShareIntent, urls = urls)
    }
}

data class HomeScreenState(
    val isShareIntent: Boolean = false,
    val urls: List<String> = emptyList(),
)

@androidx.compose.runtime.Composable
private fun TodoBookmarkScreen(state: HomeScreenState) {
    Scaffold { innerPadding ->
        if (state.isShareIntent) {
            SharedLinksContent(
                urls = state.urls,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            SetupContent(modifier = Modifier.padding(innerPadding))
        }
    }
}

@androidx.compose.runtime.Composable
private fun SetupContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.setup_complete))
        Text(
            text = stringResource(R.string.setup_next),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@androidx.compose.runtime.Composable
private fun SharedLinksContent(
    urls: List<String>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Text(
                text = if (urls.isEmpty()) {
                    stringResource(R.string.no_links)
                } else {
                    stringResource(R.string.detected_links, urls.size)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (urls.isNotEmpty()) {
            items(urls) { url ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = url, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.save_not_implemented),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

