# TodoBookmark Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `app.md` MVP — Cognito Hosted UI login, batch registration of shared URLs to `POST /bookmarks/batch`, and WorkManager-based offline retry — on top of the existing `ShareIntentParser`/`ShareIntentReader` URL extraction.

**Architecture:** Single-activity Compose app. `MainActivity` routes launcher intents to a Setup screen and share intents (`ACTION_SEND`/`ACTION_SEND_MULTIPLE`) to a Save screen. Pure logic (URL batching, result aggregation, HTTP-outcome classification, WorkManager result mapping) lives in small testable files with no Android dependency; `AuthManager` (AWS Amplify Auth) and the Compose UI are integration glue that can only be verified against a deployed backend, per `docs/backend-requirements.md` and `app.md` §13's stated manual-test policy for UI/network/auth.

**Tech Stack:** Kotlin, Jetpack Compose, AWS Amplify Auth (Cognito plugin, Hosted UI via Custom Tabs + PKCE), Retrofit + OkHttp + kotlinx.serialization, WorkManager, JUnit4 + OkHttp MockWebServer + kotlinx-coroutines-test for pure-logic unit tests.

## Global Constraints

- `applicationId` / package: `com.koukishiba.todobookmark` (already set).
- `minSdk = 26`, `compileSdk = targetSdk = 35` (already set).
- No secrets, API keys, AWS credentials, or deployment-specific IDs hardcoded in Kotlin/JSON source — `AndroidUserPoolClientId` and `HostedUiDomain` are only known after backend deploy (`docs/backend-requirements.md` line 10) and must come from `local.properties` (gitignored) via `BuildConfig`.
- `Authorization` header carries the raw Cognito ID Token — **no `Bearer ` prefix** (`docs/backend-requirements.md` lines 65-71, `app.md` §9 as corrected).
- Batch requests always include explicit `status` and `source: "android"` fields (`docs/backend-requirements.md` line 98, `app.md` §7 as corrected).
- `/bookmarks/batch` accepts at most 20 items per request; URLs are split into ≤20-item batches preserving order and sent sequentially, not in parallel (`docs/backend-requirements.md` lines 102, 164; `app.md` §12).
- Retry policy: 200 → no retry (aggregate `created`/`existing` as success, `invalid` as failure); 400 → no retry; 401/403 → one session-refresh attempt then re-login, no retry; 5xx and network failure/timeout → WorkManager retry with exponential backoff (`app.md` §10 as corrected, `docs/backend-requirements.md` lines 166-176).
- WorkManager input data must **not** contain the auth token; the token is fetched fresh from the session at execution time (`docs/backend-requirements.md` line 208, `app.md` §16 "トークンや共有URLを通常ログへ出力しないでください" / repo-wide no-token-in-logs rule).
- Callback URL `todobookmark://callback`, sign-out URL `todobookmark://signout` (already reserved in CDK per `docs/backend-requirements.md` lines 39-40).
- Per `app.md` §13, automated tests are required only for regex/logic-heavy pure functions (URL parsing, batching, aggregation, classification); UI, live network calls, and the Amplify Auth integration itself are manual-verification only and cannot be fully exercised until the backend is deployed (`docs/backend-requirements.md` "結合前にバックエンド側で行うこと").

---

## Task 1: Gradle configuration, BuildConfig, Manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `local.properties.example`
- Modify: `.gitignore`
- Create: `app/src/main/amplify/amplifyconfiguration.template.json`

**Interfaces:**
- Produces: `BuildConfig.API_BASE_URL`, `BuildConfig.AWS_REGION`, `BuildConfig.COGNITO_USER_POOL_ID`, `BuildConfig.COGNITO_USER_POOL_CLIENT_ID`, `BuildConfig.COGNITO_HOSTED_UI_DOMAIN`, `BuildConfig.COGNITO_CALLBACK_URL`, `BuildConfig.COGNITO_SIGNOUT_URL` (all `String`), and a generated `app/src/main/res/raw/amplifyconfiguration.json` — later tasks (5, 6) read these.

- [ ] **Step 1: Add library versions and coordinates**

Edit `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.0.1"
kotlin = "2.2.10"
activityCompose = "1.10.1"
composeBom = "2025.08.00"
coreKtx = "1.16.0"
junit = "4.13.2"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
retrofitKotlinxSerializationConverter = "1.0.0"
workManager = "2.10.0"
amplify = "2.19.1"
browser = "1.8.0"
lifecycleRuntimeCompose = "2.8.7"
kotlinxCoroutines = "1.9.0"

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "workManager" }
androidx-browser = { module = "androidx.browser:browser", version.ref = "browser" }
junit = { module = "junit:junit", version.ref = "junit" }
squareup-retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
squareup-retrofit-kotlinx-serialization-converter = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version.ref = "retrofitKotlinxSerializationConverter" }
squareup-okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
squareup-okhttp-logging-interceptor = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
squareup-okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
amplify-core = { module = "com.amplifyframework:core", version.ref = "amplify" }
amplify-aws-auth-cognito = { module = "com.amplifyframework:aws-auth-cognito", version.ref = "amplify" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`amplify-core`/`amplify-aws-auth-cognito` are a fast-moving third-party SDK; if `2.19.1` fails to resolve, bump both to the latest matching minor released on Maven Central and continue — the exact patch number is not load-bearing.

- [ ] **Step 2: Wire BuildConfig fields from `local.properties`**

Replace the full contents of `app/build.gradle.kts`:

```kotlin
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

fun localProperty(key: String, default: String): String =
    localProperties.getProperty(key) ?: default

android {
    namespace = "com.koukishiba.todobookmark"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koukishiba.todobookmark"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "API_BASE_URL",
            "\"${localProperty("TODOBOOKMARK_API_BASE_URL", "https://gctpao66n5.execute-api.ap-northeast-3.amazonaws.com/prod")}\"",
        )
        buildConfigField(
            "String", "AWS_REGION",
            "\"${localProperty("TODOBOOKMARK_AWS_REGION", "ap-northeast-3")}\"",
        )
        buildConfigField(
            "String", "COGNITO_USER_POOL_ID",
            "\"${localProperty("TODOBOOKMARK_USER_POOL_ID", "ap-northeast-3_H9F0jf3UU")}\"",
        )
        buildConfigField(
            "String", "COGNITO_USER_POOL_CLIENT_ID",
            "\"${localProperty("TODOBOOKMARK_USER_POOL_CLIENT_ID", "CHANGEME")}\"",
        )
        buildConfigField(
            "String", "COGNITO_HOSTED_UI_DOMAIN",
            "\"${localProperty("TODOBOOKMARK_HOSTED_UI_DOMAIN", "CHANGEME.auth.ap-northeast-3.amazoncognito.com")}\"",
        )
        buildConfigField("String", "COGNITO_CALLBACK_URL", "\"todobookmark://callback\"")
        buildConfigField("String", "COGNITO_SIGNOUT_URL", "\"todobookmark://signout\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val generateAmplifyConfig by tasks.registering {
    val templateFile = file("src/main/amplify/amplifyconfiguration.template.json")
    val outputFile = file("src/main/res/raw/amplifyconfiguration.json")
    inputs.file(templateFile)
    outputs.file(outputFile)
    doLast {
        outputFile.parentFile.mkdirs()
        val content = templateFile.readText()
            .replace("__USER_POOL_ID__", localProperty("TODOBOOKMARK_USER_POOL_ID", "ap-northeast-3_H9F0jf3UU"))
            .replace("__USER_POOL_CLIENT_ID__", localProperty("TODOBOOKMARK_USER_POOL_CLIENT_ID", "CHANGEME"))
            .replace("__AWS_REGION__", localProperty("TODOBOOKMARK_AWS_REGION", "ap-northeast-3"))
            .replace(
                "__HOSTED_UI_DOMAIN__",
                localProperty("TODOBOOKMARK_HOSTED_UI_DOMAIN", "CHANGEME.auth.ap-northeast-3.amazoncognito.com"),
            )
        outputFile.writeText(content)
    }
}

tasks.named("preBuild") {
    dependsOn(generateAmplifyConfig)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.kotlinx.serialization.converter)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)

    implementation(libs.amplify.core)
    implementation(libs.amplify.aws.auth.cognito)

    testImplementation(libs.junit)
    testImplementation(libs.squareup.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 3: Add the Amplify config template**

Create `app/src/main/amplify/amplifyconfiguration.template.json`:

```json
{
  "UserAgent": "aws-amplify-cli/2.0",
  "Version": "1.0",
  "auth": {
    "plugins": {
      "awsCognitoAuthPlugin": {
        "UserAgent": "aws-amplify-cli/0.1.0",
        "Version": "0.1.0",
        "IdentityManager": {
          "Default": {}
        },
        "CognitoUserPool": {
          "Default": {
            "PoolId": "__USER_POOL_ID__",
            "AppClientId": "__USER_POOL_CLIENT_ID__",
            "Region": "__AWS_REGION__"
          }
        },
        "Auth": {
          "Default": {
            "OAuth": {
              "WebDomain": "__HOSTED_UI_DOMAIN__",
              "AppClientId": "__USER_POOL_CLIENT_ID__",
              "SignInRedirectURI": "todobookmark://callback",
              "SignOutRedirectURI": "todobookmark://signout",
              "Scopes": ["openid", "email", "profile"]
            }
          }
        }
      }
    }
  }
}
```

The generated `app/src/main/res/raw/amplifyconfiguration.json` must never be committed with real values — it is machine-generated from `local.properties` on every build.

- [ ] **Step 4: local.properties.example and .gitignore**

Create `local.properties.example`:

```properties
# local.properties は .gitignore 済み。このファイルをコピーして local.properties を作成し、
# バックエンドデプロイ後の CloudFormation 出力値を設定すること（docs/backend-requirements.md 参照）。
sdk.dir=/path/to/Android/sdk

TODOBOOKMARK_API_BASE_URL=https://gctpao66n5.execute-api.ap-northeast-3.amazonaws.com/prod
TODOBOOKMARK_AWS_REGION=ap-northeast-3
TODOBOOKMARK_USER_POOL_ID=ap-northeast-3_H9F0jf3UU
TODOBOOKMARK_USER_POOL_CLIENT_ID=CHANGEME
TODOBOOKMARK_HOSTED_UI_DOMAIN=CHANGEME.auth.ap-northeast-3.amazoncognito.com
```

Edit `.gitignore`, add after the `local.properties` line:

```gitignore
# Generated from local.properties at build time; never commit real Cognito values
app/src/main/res/raw/amplifyconfiguration.json
```

- [ ] **Step 5: Manifest — permission, callback deep link, Application class, singleTask**

Replace `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".TodoBookmarkApp"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@drawable/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.TodoBookmark">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="todobookmark" android:host="callback" />
                <data android:scheme="todobookmark" android:host="signout" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`TodoBookmarkApp` is created in Task 6; the Manifest reference will not compile until then, so Step 6 of this task only verifies the Gradle/resource wiring, not a full app compile.

- [ ] **Step 6: Verify Gradle configuration resolves**

Run: `./gradlew :app:tasks --console=plain`
Expected: Gradle configuration succeeds (no "could not resolve" errors for the new dependencies). A full `assembleDebug` will fail at this point because `TodoBookmarkApp` doesn't exist yet — that's expected and resolved by Task 6.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml \
  app/src/main/amplify/amplifyconfiguration.template.json local.properties.example .gitignore
git commit -m "$(cat <<'EOF'
feat: add networking/auth/workmanager dependencies and BuildConfig-driven config

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 2: Bookmark API models and Retrofit interface

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApiModels.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApi.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/network/BookmarkApiModelsTest.kt`

**Interfaces:**
- Produces: `BatchRequestItem(url: String)`, `BatchRequestBody(items: List<BatchRequestItem>, status: String = "inbox", source: String = "android")`, `BatchResultStatus` (enum: `CREATED`, `EXISTING`, `INVALID`), `BatchResultItem(url: String, status: BatchResultStatus, id: String? = null)`, `BatchResponseBody(results: List<BatchResultItem>)`, `BookmarkApi.postBatch(body: BatchRequestBody): Response<BatchResponseBody>` — consumed by Tasks 3, 5, 7.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/koukishiba/todobookmark/network/BookmarkApiModelsTest.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkApiModelsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `バッチリクエストは status と source を既定値付きで送る`() {
        val body = BatchRequestBody(
            items = listOf(BatchRequestItem("https://example.com/a")),
        )

        val encoded = json.encodeToString(BatchRequestBody.serializer(), body)

        assertEquals(
            """{"items":[{"url":"https://example.com/a"}],"status":"inbox","source":"android"}""",
            encoded,
        )
    }

    @Test
    fun `バッチレスポンスの created と existing と invalid を判定できる`() {
        val responseJson = """
            {"results":[
              {"url":"https://a.com","status":"created","id":"1"},
              {"url":"https://b.com","status":"existing","id":"2"},
              {"url":"invalid-url","status":"invalid"}
            ]}
        """.trimIndent()

        val decoded = json.decodeFromString(BatchResponseBody.serializer(), responseJson)

        assertEquals(
            listOf(BatchResultStatus.CREATED, BatchResultStatus.EXISTING, BatchResultStatus.INVALID),
            decoded.results.map { it.status },
        )
        assertEquals(null, decoded.results.last().id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.network.BookmarkApiModelsTest"`
Expected: FAIL — compilation error, `BatchRequestBody` etc. are unresolved references.

- [ ] **Step 3: Write the models**

Create `app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApiModels.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BatchRequestItem(
    val url: String,
)

@Serializable
data class BatchRequestBody(
    val items: List<BatchRequestItem>,
    val status: String = "inbox",
    val source: String = "android",
)

@Serializable
enum class BatchResultStatus {
    @SerialName("created") CREATED,
    @SerialName("existing") EXISTING,
    @SerialName("invalid") INVALID,
}

@Serializable
data class BatchResultItem(
    val url: String,
    val status: BatchResultStatus,
    val id: String? = null,
)

@Serializable
data class BatchResponseBody(
    val results: List<BatchResultItem>,
)
```

- [ ] **Step 4: Write the Retrofit interface**

Create `app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApi.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BookmarkApi {
    @POST("bookmarks/batch")
    suspend fun postBatch(@Body body: BatchRequestBody): Response<BatchResponseBody>
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.network.BookmarkApiModelsTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApiModels.kt \
  app/src/main/java/com/koukishiba/todobookmark/network/BookmarkApi.kt \
  app/src/test/java/com/koukishiba/todobookmark/network/BookmarkApiModelsTest.kt
git commit -m "$(cat <<'EOF'
feat: add Bookmark batch API models and Retrofit interface

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 3: URL batching and result aggregation

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/batch/BatchPlanning.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/batch/BatchPlanningTest.kt`

**Interfaces:**
- Consumes: `BatchResultItem`, `BatchResultStatus` (Task 2).
- Produces: `chunkUrls(urls: List<String>, maxBatchSize: Int = 20): List<List<String>>`, `data class SaveSummary(successCount: Int, failureCount: Int)` with `SaveSummary.ZERO` and `operator fun plus`, `List<BatchResultItem>.toSaveSummary(): SaveSummary` — consumed by Tasks 4, 7.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/koukishiba/todobookmark/batch/BatchPlanningTest.kt`:

```kotlin
package com.koukishiba.todobookmark.batch

import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchPlanningTest {
    @Test
    fun `21件は20件と1件のバッチに分割する`() {
        val urls = (1..21).map { "https://example.com/$it" }

        val chunks = chunkUrls(urls)

        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(1, chunks[1].size)
        assertEquals(urls, chunks.flatten())
    }

    @Test
    fun `20件はちょうど1バッチになる`() {
        val urls = (1..20).map { "https://example.com/$it" }

        assertEquals(1, chunkUrls(urls).size)
    }

    @Test
    fun `空リストはバッチを生成しない`() {
        assertEquals(emptyList<List<String>>(), chunkUrls(emptyList()))
    }

    @Test
    fun `SaveSummary は加算できる`() {
        val total = SaveSummary(successCount = 2, failureCount = 1) + SaveSummary(successCount = 3, failureCount = 0)

        assertEquals(SaveSummary(successCount = 5, failureCount = 1), total)
    }

    @Test
    fun `created と existing は成功、invalid は失敗として集計する`() {
        val results = listOf(
            BatchResultItem("https://a.com", BatchResultStatus.CREATED, id = "1"),
            BatchResultItem("https://b.com", BatchResultStatus.EXISTING, id = "2"),
            BatchResultItem("invalid-url", BatchResultStatus.INVALID),
        )

        assertEquals(SaveSummary(successCount = 2, failureCount = 1), results.toSaveSummary())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.batch.BatchPlanningTest"`
Expected: FAIL — compilation error, `chunkUrls`/`SaveSummary`/`toSaveSummary` are unresolved.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/koukishiba/todobookmark/batch/BatchPlanning.kt`:

```kotlin
package com.koukishiba.todobookmark.batch

import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus

private const val MAX_BATCH_SIZE = 20

/** URL一覧を、API上限（既定20件）ごとのバッチへ順序を保ったまま分割する。 */
fun chunkUrls(urls: List<String>, maxBatchSize: Int = MAX_BATCH_SIZE): List<List<String>> {
    if (urls.isEmpty()) return emptyList()
    return urls.chunked(maxBatchSize)
}

data class SaveSummary(
    val successCount: Int,
    val failureCount: Int,
) {
    operator fun plus(other: SaveSummary): SaveSummary =
        SaveSummary(successCount + other.successCount, failureCount + other.failureCount)

    companion object {
        val ZERO = SaveSummary(successCount = 0, failureCount = 0)
    }
}

/** created / existing を成功、invalid を失敗として集計する。 */
fun List<BatchResultItem>.toSaveSummary(): SaveSummary {
    val success = count { it.status == BatchResultStatus.CREATED || it.status == BatchResultStatus.EXISTING }
    val failure = count { it.status == BatchResultStatus.INVALID }
    return SaveSummary(successCount = success, failureCount = failure)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.batch.BatchPlanningTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/batch/BatchPlanning.kt \
  app/src/test/java/com/koukishiba/todobookmark/batch/BatchPlanningTest.kt
git commit -m "$(cat <<'EOF'
feat: add URL batch chunking and save-result aggregation

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 4: HTTP outcome classification

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/repository/SaveOutcome.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/repository/SaveOutcomeTest.kt`

**Interfaces:**
- Consumes: `SaveSummary` (Task 3).
- Produces: `sealed interface SaveOutcome` with `Completed(summary)`, `ClientError(summary)`, `AuthExpired`, `Retryable`; `classifyResponse(httpCode: Int, resultsSummary: SaveSummary?, chunkSize: Int): SaveOutcome`; `classifyNetworkFailure(): SaveOutcome` — consumed by Tasks 7, 8.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/koukishiba/todobookmark/repository/SaveOutcomeTest.kt`:

```kotlin
package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveOutcomeTest {
    @Test
    fun `200 は結果summaryをそのまま Completed にする`() {
        val summary = SaveSummary(successCount = 3, failureCount = 0)

        assertEquals(SaveOutcome.Completed(summary), classifyResponse(200, summary, chunkSize = 3))
    }

    @Test
    fun `200 でも summary が欠けていれば全件失敗扱いにする`() {
        assertEquals(
            SaveOutcome.Completed(SaveSummary(successCount = 0, failureCount = 3)),
            classifyResponse(200, null, chunkSize = 3),
        )
    }

    @Test
    fun `400 は再送しない ClientError にする`() {
        assertEquals(
            SaveOutcome.ClientError(SaveSummary(successCount = 0, failureCount = 2)),
            classifyResponse(400, null, chunkSize = 2),
        )
    }

    @Test
    fun `401 と 403 は AuthExpired にする`() {
        assertEquals(SaveOutcome.AuthExpired, classifyResponse(401, null, chunkSize = 1))
        assertEquals(SaveOutcome.AuthExpired, classifyResponse(403, null, chunkSize = 1))
    }

    @Test
    fun `5xx は Retryable にする`() {
        assertEquals(SaveOutcome.Retryable, classifyResponse(500, null, chunkSize = 1))
        assertEquals(SaveOutcome.Retryable, classifyResponse(503, null, chunkSize = 1))
    }

    @Test
    fun `想定外のコードは再送しない ClientError にする`() {
        assertEquals(
            SaveOutcome.ClientError(SaveSummary(successCount = 0, failureCount = 1)),
            classifyResponse(404, null, chunkSize = 1),
        )
    }

    @Test
    fun `通信エラーは Retryable にする`() {
        assertEquals(SaveOutcome.Retryable, classifyNetworkFailure())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.repository.SaveOutcomeTest"`
Expected: FAIL — compilation error, `SaveOutcome`/`classifyResponse`/`classifyNetworkFailure` unresolved.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/koukishiba/todobookmark/repository/SaveOutcome.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.repository.SaveOutcomeTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/repository/SaveOutcome.kt \
  app/src/test/java/com/koukishiba/todobookmark/repository/SaveOutcomeTest.kt
git commit -m "$(cat <<'EOF'
feat: classify batch API responses into retry/no-retry outcomes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 5: AuthInterceptor and ApiClient

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/network/AuthInterceptor.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/network/ApiClient.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/network/AuthInterceptorTest.kt`

**Interfaces:**
- Consumes: `BookmarkApi` (Task 2), `BuildConfig.API_BASE_URL` (Task 1).
- Produces: `fun interface IdTokenProvider { suspend fun currentIdToken(): String? }`, `class AuthInterceptor(tokenProvider: IdTokenProvider) : Interceptor`, `object ApiClient { fun create(tokenProvider: IdTokenProvider): BookmarkApi }` — consumed by Tasks 6 (AuthManager implements `IdTokenProvider`), 7, 8.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/koukishiba/todobookmark/network/AuthInterceptorTest.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Authorization ヘッダーに Bearer を付けずトークンをそのまま設定する`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "dummy-id-token" })
            .build()

        client.newCall(Request.Builder().url(server.url("/bookmarks/batch")).build()).execute()

        val recorded = server.takeRequest()
        assertEquals("dummy-id-token", recorded.getHeader("Authorization"))
        assertFalse(recorded.getHeader("Authorization")!!.startsWith("Bearer"))
    }

    @Test
    fun `トークンが取得できない場合は Authorization ヘッダーを付けない`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { null })
            .build()

        client.newCall(Request.Builder().url(server.url("/bookmarks/batch")).build()).execute()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.network.AuthInterceptorTest"`
Expected: FAIL — compilation error, `AuthInterceptor` unresolved.

- [ ] **Step 3: Implement `AuthInterceptor`**

Create `app/src/main/java/com/koukishiba/todobookmark/network/AuthInterceptor.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

fun interface IdTokenProvider {
    suspend fun currentIdToken(): String?
}

/** Cognito ID Token をそのまま `Authorization` ヘッダーへ設定する（`Bearer ` は付けない）。 */
class AuthInterceptor(private val tokenProvider: IdTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking { tokenProvider.currentIdToken() }
        val authorizedRequest = if (token != null) {
            request.newBuilder().header("Authorization", token).build()
        } else {
            request
        }
        return chain.proceed(authorizedRequest)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.network.AuthInterceptorTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Implement `ApiClient`**

Create `app/src/main/java/com/koukishiba/todobookmark/network/ApiClient.kt`:

```kotlin
package com.koukishiba.todobookmark.network

import com.koukishiba.todobookmark.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // encodeDefaults=true: status/source を常に明示送信する（docs/backend-requirements.md 推奨）。
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun create(tokenProvider: IdTokenProvider): BookmarkApi {
        // BASIC はメソッド/パス/レスポンスコードのみを記録し、Authorizationヘッダーやリクエストボディ
        // （共有URL）はログに出さない。トークン・URLを通常ログへ出力しない要件を満たす。
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(logging)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("${BuildConfig.API_BASE_URL}/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(BookmarkApi::class.java)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/network/AuthInterceptor.kt \
  app/src/main/java/com/koukishiba/todobookmark/network/ApiClient.kt \
  app/src/test/java/com/koukishiba/todobookmark/network/AuthInterceptorTest.kt
git commit -m "$(cat <<'EOF'
feat: add token-only Authorization interceptor and Retrofit client

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 6: AuthManager (Amplify Auth) and Application class

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/TodoBookmarkApp.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/auth/AuthManager.kt`

**Interfaces:**
- Consumes: `IdTokenProvider` (Task 5).
- Produces: `sealed interface AuthState { data class SignedIn(val email: String?); data object SignedOut }`, `class AuthManager : IdTokenProvider` with `suspend fun signIn(activity: Activity): AuthSignInResult`, `suspend fun signOut()`, `suspend fun currentState(): AuthState`, `override suspend fun currentIdToken(): String?` — consumed by Tasks 7, 8, 9.

**Note:** This task integrates a third-party SDK (AWS Amplify Auth) whose exact method signatures can drift between versions. Manual verification only, per Global Constraints — after writing the code, compile against the version Gradle actually resolved and fix any signature mismatches against that version's API before moving on.

- [ ] **Step 1: Application class registering the Cognito plugin**

Create `app/src/main/java/com/koukishiba/todobookmark/TodoBookmarkApp.kt`:

```kotlin
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
```

- [ ] **Step 2: AuthManager**

Create `app/src/main/java/com/koukishiba/todobookmark/auth/AuthManager.kt`:

```kotlin
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
```

- [ ] **Step 3: Compile and fix against the resolved Amplify version**

Run: `./gradlew :app:compileDebugKotlin`
Expected: compiles. If the resolved Amplify version renamed/moved any of `AWSCognitoAuthSession`, `userPoolTokensResult`, `signInWithWebUI`, `fetchUserAttributes`, fix the call sites here to match that version's actual API (check the installed JAR's decompiled sources or the version's release notes) — do not guess further, use what the compiler reports.

- [ ] **Step 4: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. This is the first point since Task 1 the whole app compiles again (Manifest referenced `TodoBookmarkApp`, now present).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/TodoBookmarkApp.kt \
  app/src/main/java/com/koukishiba/todobookmark/auth/AuthManager.kt
git commit -m "$(cat <<'EOF'
feat: add Amplify Cognito Hosted UI login/logout/session wrapper

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 7: BookmarkRepository

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/repository/BookmarkRepository.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/repository/BookmarkRepositoryTest.kt`

**Interfaces:**
- Consumes: `BookmarkApi`, `BatchRequestBody`, `BatchRequestItem`, `BatchResponseBody`, `BatchResultItem`, `BatchResultStatus` (Task 2); `chunkUrls`, `SaveSummary`, `toSaveSummary` (Task 3); `SaveOutcome`, `classifyResponse`, `classifyNetworkFailure` (Task 4).
- Produces: `data class SaveProgress(val processed: Int, val total: Int)`, `data class SaveResult(val outcome: SaveOutcome, val pendingUrls: List<String>)`, `class BookmarkRepository(api: BookmarkApi) { suspend fun save(urls: List<String>, status: String = "inbox", onProgress: (SaveProgress) -> Unit = {}): SaveResult }` — consumed by Tasks 8, 9.

This is the one "API通信" component with a fully fake-able dependency (`BookmarkApi` is a plain suspend interface), so — unlike `AuthManager`/UI — its branching logic is unit tested here with a hand-written fake, no MockWebServer or Android framework needed.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/koukishiba/todobookmark/repository/BookmarkRepositoryTest.kt`:

```kotlin
package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.network.BatchRequestBody
import com.koukishiba.todobookmark.network.BatchResponseBody
import com.koukishiba.todobookmark.network.BatchResultItem
import com.koukishiba.todobookmark.network.BatchResultStatus
import com.koukishiba.todobookmark.network.BookmarkApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

private class FakeBookmarkApi(
    private val responder: (BatchRequestBody) -> Response<BatchResponseBody>,
) : BookmarkApi {
    val requests = mutableListOf<BatchRequestBody>()

    override suspend fun postBatch(body: BatchRequestBody): Response<BatchResponseBody> {
        requests += body
        return responder(body)
    }
}

class BookmarkRepositoryTest {
    @Test
    fun `21件のURLは20件と1件のバッチに分割して順番に送信する`() = runTest {
        val api = FakeBookmarkApi { body ->
            Response.success(
                BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
            )
        }
        val repository = BookmarkRepository(api)
        val urls = (1..21).map { "https://example.com/$it" }

        val result = repository.save(urls)

        assertEquals(2, api.requests.size)
        assertEquals(20, api.requests[0].items.size)
        assertEquals(1, api.requests[1].items.size)
        assertEquals(SaveOutcome.Completed(SaveSummary(successCount = 21, failureCount = 0)), result.outcome)
        assertEquals(emptyList<String>(), result.pendingUrls)
    }

    @Test
    fun `5xxが返った以降のバッチは再送対象として残す`() = runTest {
        var callCount = 0
        val api = FakeBookmarkApi { body ->
            callCount++
            if (callCount == 1) {
                Response.success(
                    BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
                )
            } else {
                Response.error(503, "".toResponseBody(null))
            }
        }
        val repository = BookmarkRepository(api)
        val urls = (1..45).map { "https://example.com/$it" }

        val result = repository.save(urls)

        assertEquals(SaveOutcome.Retryable, result.outcome)
        assertEquals(25, result.pendingUrls.size)
        assertEquals("https://example.com/21", result.pendingUrls.first())
    }

    @Test
    fun `invalidを含むバッチは再送せず失敗件数として集計する`() = runTest {
        val api = FakeBookmarkApi { body ->
            val results = body.items.mapIndexed { index, item ->
                val status = if (index == 0) BatchResultStatus.INVALID else BatchResultStatus.CREATED
                BatchResultItem(item.url, status, id = if (status == BatchResultStatus.CREATED) item.url else null)
            }
            Response.success(BatchResponseBody(results))
        }
        val repository = BookmarkRepository(api)
        val urls = listOf("invalid-url", "https://example.com/ok1", "https://example.com/ok2")

        val result = repository.save(urls)

        assertEquals(SaveOutcome.ClientError(SaveSummary(successCount = 2, failureCount = 1)), result.outcome)
        assertEquals(emptyList<String>(), result.pendingUrls)
    }

    @Test
    fun `401はAuthExpiredとして未送信分すべてを残す`() = runTest {
        val api = FakeBookmarkApi { Response.error(401, "".toResponseBody(null)) }
        val repository = BookmarkRepository(api)
        val urls = listOf("https://example.com/a")

        val result = repository.save(urls)

        assertEquals(SaveOutcome.AuthExpired, result.outcome)
        assertEquals(urls, result.pendingUrls)
    }

    @Test
    fun `進捗コールバックは処理済み件数を通知する`() = runTest {
        val api = FakeBookmarkApi { body ->
            Response.success(
                BatchResponseBody(body.items.map { BatchResultItem(it.url, BatchResultStatus.CREATED, id = it.url) }),
            )
        }
        val repository = BookmarkRepository(api)
        val urls = (1..25).map { "https://example.com/$it" }
        val progressUpdates = mutableListOf<SaveProgress>()

        repository.save(urls) { progressUpdates += it }

        assertEquals(listOf(SaveProgress(20, 25), SaveProgress(25, 25)), progressUpdates)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.repository.BookmarkRepositoryTest"`
Expected: FAIL — compilation error, `BookmarkRepository`/`SaveProgress`/`SaveResult` unresolved.

- [ ] **Step 3: Implement**

Create `app/src/main/java/com/koukishiba/todobookmark/repository/BookmarkRepository.kt`:

```kotlin
package com.koukishiba.todobookmark.repository

import com.koukishiba.todobookmark.batch.SaveSummary
import com.koukishiba.todobookmark.batch.chunkUrls
import com.koukishiba.todobookmark.batch.toSaveSummary
import com.koukishiba.todobookmark.network.BatchRequestBody
import com.koukishiba.todobookmark.network.BatchRequestItem
import com.koukishiba.todobookmark.network.BookmarkApi
import java.io.IOException

data class SaveProgress(val processed: Int, val total: Int)

data class SaveResult(val outcome: SaveOutcome, val pendingUrls: List<String>)

/** URLを20件ずつのバッチへ分割し、順番にBookmark APIへ送信する。 */
class BookmarkRepository(private val api: BookmarkApi) {

    suspend fun save(
        urls: List<String>,
        status: String = "inbox",
        onProgress: (SaveProgress) -> Unit = {},
    ): SaveResult {
        val chunks = chunkUrls(urls)
        var summary = SaveSummary.ZERO
        var processed = 0

        chunks.forEachIndexed { index, chunk ->
            when (val outcome = sendChunk(chunk, status)) {
                is SaveOutcome.Completed -> {
                    summary += outcome.summary
                    processed += chunk.size
                    onProgress(SaveProgress(processed, urls.size))
                }
                is SaveOutcome.ClientError -> {
                    summary += outcome.summary
                    processed += chunk.size
                    onProgress(SaveProgress(processed, urls.size))
                }
                SaveOutcome.AuthExpired -> {
                    return SaveResult(SaveOutcome.AuthExpired, remainingUrlsFrom(chunks, index))
                }
                SaveOutcome.Retryable -> {
                    return SaveResult(SaveOutcome.Retryable, remainingUrlsFrom(chunks, index))
                }
            }
        }

        val finalOutcome = if (summary.failureCount > 0) {
            SaveOutcome.ClientError(summary)
        } else {
            SaveOutcome.Completed(summary)
        }
        return SaveResult(finalOutcome, pendingUrls = emptyList())
    }

    private suspend fun sendChunk(chunk: List<String>, status: String): SaveOutcome {
        val body = BatchRequestBody(items = chunk.map(::BatchRequestItem), status = status)
        return try {
            val response = api.postBatch(body)
            val summary = response.body()?.results?.toSaveSummary()
            classifyResponse(response.code(), summary, chunk.size)
        } catch (error: IOException) {
            classifyNetworkFailure()
        }
    }

    private fun remainingUrlsFrom(chunks: List<List<String>>, fromIndex: Int): List<String> =
        chunks.drop(fromIndex).flatten()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.repository.BookmarkRepositoryTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/repository/BookmarkRepository.kt \
  app/src/test/java/com/koukishiba/todobookmark/repository/BookmarkRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat: orchestrate batched Bookmark saves with per-chunk outcome handling

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 8: WorkManager retry (RetryDecision, RetrySaveWorker, WorkScheduler)

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/work/RetryDecision.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/work/RetrySaveWorker.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/work/WorkScheduler.kt`
- Test: `app/src/test/java/com/koukishiba/todobookmark/work/RetryDecisionTest.kt`

**Interfaces:**
- Consumes: `SaveOutcome` (Task 4); `AuthManager` (Task 6); `ApiClient`, `IdTokenProvider` (Task 5); `BookmarkRepository` (Task 7).
- Produces: `fun SaveOutcome.toWorkResult(): ListenableWorker.Result`, `class RetrySaveWorker : CoroutineWorker` with `companion object { fun inputData(urls: List<String>, status: String): Data }`, `object WorkScheduler { fun enqueueRetry(context: Context, urls: List<String>, status: String) }` — consumed by Task 9.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/koukishiba/todobookmark/work/RetryDecisionTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.work.RetryDecisionTest"`
Expected: FAIL — compilation error, `toWorkResult` unresolved.

- [ ] **Step 3: Implement `RetryDecision`**

Create `app/src/main/java/com/koukishiba/todobookmark/work/RetryDecision.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.koukishiba.todobookmark.work.RetryDecisionTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Implement `RetrySaveWorker`**

Create `app/src/main/java/com/koukishiba/todobookmark/work/RetrySaveWorker.kt`:

```kotlin
package com.koukishiba.todobookmark.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.koukishiba.todobookmark.auth.AuthManager
import com.koukishiba.todobookmark.network.ApiClient
import com.koukishiba.todobookmark.repository.BookmarkRepository

private const val KEY_URLS = "urls"
private const val KEY_STATUS = "status"
private const val URL_SEPARATOR = "\n"

/**
 * WorkManagerが保持するのはURLと保存状態のみで、認証トークンは含めない。
 * 実行時に最新のCognitoセッションから取得する（AuthManager経由）。
 */
class RetrySaveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val urls = inputData.getString(KEY_URLS)
            ?.split(URL_SEPARATOR)
            .orEmpty()
            .filter(String::isNotBlank)
        val status = inputData.getString(KEY_STATUS) ?: "inbox"
        if (urls.isEmpty()) return Result.success()

        val authManager = AuthManager()
        val api = ApiClient.create(authManager)
        val repository = BookmarkRepository(api)

        val result = repository.save(urls, status)
        return result.outcome.toWorkResult()
    }

    companion object {
        fun inputData(urls: List<String>, status: String): Data =
            Data.Builder()
                .putString(KEY_URLS, urls.joinToString(URL_SEPARATOR))
                .putString(KEY_STATUS, status)
                .build()
    }
}
```

- [ ] **Step 6: Implement `WorkScheduler`**

Create `app/src/main/java/com/koukishiba/todobookmark/work/WorkScheduler.kt`:

```kotlin
package com.koukishiba.todobookmark.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val BACKOFF_DELAY_SECONDS = 30L

    /** ネットワーク接続制約付きで、指数バックオフによる再送をキューへ登録する。 */
    fun enqueueRetry(context: Context, urls: List<String>, status: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RetrySaveWorker>()
            .setInputData(RetrySaveWorker.inputData(urls, status))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
```

- [ ] **Step 7: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/work/RetryDecision.kt \
  app/src/main/java/com/koukishiba/todobookmark/work/RetrySaveWorker.kt \
  app/src/main/java/com/koukishiba/todobookmark/work/WorkScheduler.kt \
  app/src/test/java/com/koukishiba/todobookmark/work/RetryDecisionTest.kt
git commit -m "$(cat <<'EOF'
feat: add WorkManager-based offline retry with exponential backoff

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 9: UI (Setup/Save screens), strings, and MainActivity wiring

**Files:**
- Create: `app/src/main/java/com/koukishiba/todobookmark/ui/HomeViewModel.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/ui/SetupScreen.kt`
- Create: `app/src/main/java/com/koukishiba/todobookmark/ui/SaveScreen.kt`
- Modify: `app/src/main/java/com/koukishiba/todobookmark/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AuthManager`, `AuthState` (Task 6); `ApiClient` (Task 5); `BookmarkRepository`, `SaveProgress`, `SaveOutcome` (Tasks 7, 4); `WorkScheduler` (Task 8); `ShareIntentParser`, `ShareIntentReader` (existing).
- Produces: `HomeViewModel` (StateFlow-based UI state), `SetupScreen`, `SaveScreen` composables. Terminal task — nothing downstream depends on these.

This task is UI/integration glue — manual verification only per Global Constraints (no automated tests), but every line must be real, working code.

- [ ] **Step 1: Replace strings.xml**

Replace `app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Todo共有リンク保存</string>

    <!-- Setup screen (app.md §4.1) -->
    <string name="signed_in_as">ログイン状態: %1$s でログイン中</string>
    <string name="unknown_email">メールアドレス不明</string>
    <string name="sign_out">ログアウト</string>
    <string name="signed_out">未ログインです</string>
    <string name="sign_in">ログイン</string>
    <string name="save_destination_inbox">保存先: inbox（固定）</string>

    <!-- Save screen (app.md §4.2) -->
    <string name="detected_links">%1$d件のリンクを検出しました</string>
    <string name="saving">保存中...</string>
    <string name="saving_progress">保存中... (%1$d/%2$d件処理中)</string>
    <string name="save_success">✓ %1$d件保存しました</string>
    <string name="save_partial_failure_title">保存に失敗しました</string>
    <string name="save_partial_failure_summary">%1$d / %2$d件保存しました</string>
    <string name="save_partial_failure_count">%1$d件の保存に失敗しました</string>
    <string name="retry">再試行</string>
    <string name="close">閉じる</string>

    <!-- Errors (app.md §11) -->
    <string name="no_links">保存できるURLが見つかりませんでした</string>
    <string name="login_required">ログインしてください</string>
    <string name="session_expired">ログインの有効期限が切れました</string>
    <string name="connection_failed">サーバーへの接続に失敗しました</string>
</resources>
```

- [ ] **Step 2: HomeViewModel**

Create `app/src/main/java/com/koukishiba/todobookmark/ui/HomeViewModel.kt`:

```kotlin
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
```

- [ ] **Step 3: SetupScreen**

Create `app/src/main/java/com/koukishiba/todobookmark/ui/SetupScreen.kt`:

```kotlin
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
```

- [ ] **Step 4: SaveScreen**

Create `app/src/main/java/com/koukishiba/todobookmark/ui/SaveScreen.kt`:

```kotlin
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
            SaveUiState.AuthRequired -> Text(stringResource(R.string.session_expired))
            SaveUiState.LoginRequired -> Text(stringResource(R.string.login_required))
            SaveUiState.NetworkQueued -> {
                Text(stringResource(R.string.connection_failed))
                Button(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            SaveUiState.NoUrls -> Text(stringResource(R.string.no_links))
        }
    }
}
```

- [ ] **Step 5: MainActivity wiring**

Replace `app/src/main/java/com/koukishiba/todobookmark/MainActivity.kt`:

```kotlin
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
import com.koukishiba.todobookmark.auth.AuthState
import com.koukishiba.todobookmark.ui.HomeViewModel
import com.koukishiba.todobookmark.ui.SaveScreen
import com.koukishiba.todobookmark.ui.SaveUiState
import com.koukishiba.todobookmark.ui.SetupScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private var pendingUrls: List<String> = emptyList()
    private var isShareIntent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.refreshAuthState()
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
        if (!isShareIntent) return

        pendingUrls = ShareIntentParser.extractUrls(ShareIntentReader.readTexts(intent))
        startSavingWithAuthCheck()
    }

    private fun startSavingWithAuthCheck() {
        if (viewModel.authState.value is AuthState.SignedIn) {
            startSaving()
        } else {
            viewModel.signIn(this) { startSaving() }
        }
    }

    private fun startSaving() {
        viewModel.save(applicationContext, pendingUrls)
    }
}
```

- [ ] **Step 6: Full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests pass (ShareIntentParserTest + the 4 new pure-logic test files from Tasks 2-4, 7, 8).

- [ ] **Step 8: Manual verification (requires a deployed backend and a device/emulator — cannot be completed in this session)**

Document, do not attempt to fake: install on a device or emulator, confirm the app appears as a Chrome share target, confirm Hosted UI login works once `local.properties` has real `TODOBOOKMARK_USER_POOL_CLIENT_ID`/`TODOBOOKMARK_HOSTED_UI_DOMAIN` values from the CloudFormation output, confirm a shared URL reaches `/bookmarks/batch` and shows up on the Web frontend, confirm offline sharing queues into WorkManager and auto-sends on reconnect.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/koukishiba/todobookmark/ui/HomeViewModel.kt \
  app/src/main/java/com/koukishiba/todobookmark/ui/SetupScreen.kt \
  app/src/main/java/com/koukishiba/todobookmark/ui/SaveScreen.kt \
  app/src/main/java/com/koukishiba/todobookmark/MainActivity.kt \
  app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat: wire Setup/Save screens, login flow, and batch save into MainActivity

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01HPuHQEvcTkh94erW4LgBxN
EOF
)"
```

---

## Task 10: Final review against app.md §17 MVP completion checklist

**Files:** none (verification only).

- [ ] **Step 1: Run the full build and test suite one more time**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Walk `app.md` §17 item by item and record status**

For each of the 14 checklist items in `app.md` §17, note whether it is:
- (a) implemented and unit-tested (list which test),
- (b) implemented but only manually verifiable, and blocked on backend deploy (per `docs/backend-requirements.md` "結合前にバックエンド側で行うこと" — the checklist there is not yet ticked), or
- (c) not yet done.

Expected split: URL extraction/dedup and unit-testable batching/classification/retry-mapping logic are (a); login, session persistence, actual registration, offline queue-and-resume, dedup-on-resend, 401/403→re-login, 400→no-retry, 5xx→backoff are (b) — they compile and are wired correctly but cannot be exercised end-to-end until `AndroidUserPoolClientId`/`HostedUiDomain` exist and a device is available.

- [ ] **Step 3: Report findings to the user**

Summarize which items are done-and-verified vs. done-but-blocked-on-deployment vs. not done, and what's needed to close the remaining gap (backend deploy + `local.properties` real values + device testing — matches `docs/backend-requirements.md`'s own pre-integration checklist).

---
