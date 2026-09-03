package com.koukishiba.todobookmark.auth

import android.app.Activity
import com.amplifyframework.auth.AuthException
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.core.Amplify
import com.koukishiba.todobookmark.network.IdTokenProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface AuthState {
    data class SignedIn(val email: String?) : AuthState
    data object SignedOut : AuthState
}

/**
 * Cognito Hosted UI ログイン・ログアウト・IDトークン取得を扱う。
 * ID Token / Refresh Token の保存・更新自体は Amplify Auth（AWSCognitoAuthPlugin）に委ねる。
 */
class AuthManager : IdTokenProvider {

    suspend fun signIn(activity: Activity): AuthSignInResult = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.signInWithWebUI(
            activity,
            { result -> continuation.resume(result) },
            { error -> continuation.resumeWithException(error) },
        )
    }

    suspend fun signOut() = suspendCancellableCoroutine<Unit> { continuation ->
        Amplify.Auth.signOut { continuation.resume(Unit) }
    }

    suspend fun currentState(): AuthState = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.fetchAuthSession(
            { session ->
                val cognitoSession = session as? AWSCognitoAuthSession
                if (cognitoSession?.isSignedIn == true) {
                    Amplify.Auth.fetchUserAttributes(
                        { attributes ->
                            val email = attributes.firstOrNull { it.key.keyString == "email" }?.value
                            continuation.resume(AuthState.SignedIn(email))
                        },
                        { continuation.resume(AuthState.SignedIn(email = null)) },
                    )
                } else {
                    continuation.resume(AuthState.SignedOut)
                }
            },
            { continuation.resume(AuthState.SignedOut) },
        )
    }

    /** [com.koukishiba.todobookmark.network.AuthInterceptor] から呼ばれる。期限切れなら Amplify が自動更新を試みる。 */
    override suspend fun currentIdToken(): String? = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.fetchAuthSession(
            { session ->
                val cognitoSession = session as? AWSCognitoAuthSession
                continuation.resume(cognitoSession?.userPoolTokensResult?.value?.idToken)
            },
            { _: AuthException -> continuation.resume(null) },
        )
    }
}
