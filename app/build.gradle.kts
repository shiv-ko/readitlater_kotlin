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
    val userPoolId = localProperty("TODOBOOKMARK_USER_POOL_ID", "ap-northeast-3_H9F0jf3UU")
    val userPoolClientId = localProperty("TODOBOOKMARK_USER_POOL_CLIENT_ID", "CHANGEME")
    val awsRegion = localProperty("TODOBOOKMARK_AWS_REGION", "ap-northeast-3")
    val hostedUiDomain = localProperty("TODOBOOKMARK_HOSTED_UI_DOMAIN", "CHANGEME.auth.ap-northeast-3.amazoncognito.com")
    inputs.file(templateFile)
    inputs.property("userPoolId", userPoolId)
    inputs.property("userPoolClientId", userPoolClientId)
    inputs.property("awsRegion", awsRegion)
    inputs.property("hostedUiDomain", hostedUiDomain)
    outputs.file(outputFile)
    doLast {
        outputFile.parentFile.mkdirs()
        val content = templateFile.readText()
            .replace("__USER_POOL_ID__", userPoolId)
            .replace("__USER_POOL_CLIENT_ID__", userPoolClientId)
            .replace("__AWS_REGION__", awsRegion)
            .replace("__HOSTED_UI_DOMAIN__", hostedUiDomain)
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
    testImplementation(libs.squareup.okhttp)
    testImplementation(libs.squareup.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Force OkHttp 4.12.0 across all configurations to avoid compatibility issues with MockWebServer
configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:4.12.0")
        force("com.squareup.okhttp3:logging-interceptor:4.12.0")
    }
}
