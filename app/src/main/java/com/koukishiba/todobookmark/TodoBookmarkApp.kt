package com.koukishiba.todobookmark

import android.app.Application
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.core.Amplify

class TodoBookmarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(applicationContext)
        } catch (error: AmplifyException) {
            Log.e("TodoBookmarkApp", "Amplify の初期化に失敗しました", error)
        }
    }
}
