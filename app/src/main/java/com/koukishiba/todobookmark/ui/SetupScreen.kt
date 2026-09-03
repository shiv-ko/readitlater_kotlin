package com.koukishiba.todobookmark.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koukishiba.todobookmark.R
import com.koukishiba.todobookmark.auth.AuthState

@Composable
fun SetupScreen(
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
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
        when (authState) {
            is AuthState.SignedIn -> {
                Text(stringResource(R.string.signed_in_as, authState.email ?: stringResource(R.string.unknown_email)))
                Button(onClick = onSignOut) { Text(stringResource(R.string.sign_out)) }
                Text(
                    stringResource(R.string.save_destination_inbox),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AuthState.SignedOut -> {
                Text(stringResource(R.string.signed_out))
                Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
            }
        }
    }
}
