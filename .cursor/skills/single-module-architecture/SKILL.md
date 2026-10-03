---
name: single-module-architecture
description: >-
  Step-by-step guide to set up a new single-module Android project with
  Clean Architecture, Hilt, Compose, Navigation 3, supabase-kt, and OkHttp.
  Include DataStore prefs and Room. Do not scaffold Retrofit. Triggers: create single module project,
  setup single module, new android project, setup android project, single module setup.
---

# Setup Single-Module Android Project

Use this skill when setting up a new single-module Android project with Clean Architecture.

**Stack:** supabase-kt (Auth + PostgREST) + Ktor engine, OkHttp for SSE, DataStore
(`healthtrack_prefs`), Room, kotlinx.serialization. **Not** Retrofit or Moshi.

**Version source of truth:** `gradle/libs.versions.toml` in this repo. Keep AGP 9,
Kotlin 2.3.x, Compose BOM, Navigation 3, and the supabase-kt BOM. Do not copy older pins from
archived templates (AGP 8.x, Kotlin 1.9, Navigation Compose 2.x, Retrofit).

AGP 9 includes Kotlin. **Do not** apply `org.jetbrains.kotlin.android`.
Use Navigation 3 (`NavDisplay` / `NavKey`), not Navigation Compose `NavHost`.


## Step 1: Create Project Structure

After creating a new Android project in Android Studio, organize packages:

```
app/src/main/kotlin/com/mascill/healthtrack/
├── data/
│   ├── datasource/
│   │   ├── local/          # DataStore prefs and Room DAO
│   │   └── remote/         # supabase-kt / SSE
│   ├── mapper/
│   ├── model/
│   │   ├── entity/         # Room
│   │   ├── request/
│   │   └── response/
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── presentation/
│   ├── common/
│   │   └── component/
│   ├── navigation/
│   ├── theme/
│   └── home/
│       ├── component/
│       └── model/
├── di/
├── util/
├── App.kt
└── MainActivity.kt
```

## Step 2: Configure build.gradle.kts

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mascill.healthtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mascill.healthtrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Supabase (Ktor engine). Do not add Retrofit or Moshi here.
    // Room is required for local tables. Add it from the version catalog.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
```

## Step 3: Configure libs.versions.toml

```toml
# gradle/libs.versions.toml
[versions]
# Copy current pins from gradle/libs.versions.toml — do not use older numbers.
# Include supabase, ktor, okhttp, datastore, room, desugarJdkLibs. Do not add retrofit or moshi.

[libraries]
# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.8.2" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }

# Navigation 3
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
androidx-lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }

# Lifecycle
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Hilt — copy pins from this repo’s gradle/libs.versions.toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-lifecycle-viewmodel-compose = { group = "androidx.hilt", name = "hilt-lifecycle-viewmodel-compose", version = "1.3.0" }

# Supabase + Ktor + OkHttp — copy versions from this repo’s catalog (supabase, ktor, okhttp)
supabase-bom = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-auth-kt = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
supabase-postgrest-kt = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
desugar-jdk-libs = { group = "com.android.tools", name = "desugar_jdk_libs", version.ref = "desugarJdkLibs" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# DataStore
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.10.1" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version = "5.10.1" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.8" }
turbine = { group = "app.cash.turbine", name = "turbine", version = "1.0.0" }
androidx-test-ext = { group = "androidx.test.ext", name = "junit", version = "1.1.5" }

[plugins]
android-application = { id = "com.android.application", version.ref = "androidGradlePlugin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

## Step 4: Create Base Files

### Application Class
```kotlin
// App.kt
package com.mascill.healthtrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application()
```

### MainActivity
```kotlin
// MainActivity.kt
package com.mascill.healthtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mascill.healthtrack.presentation.navigation.AppNavHost
import com.mascill.healthtrack.presentation.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}
```

### AppDispatcher
```kotlin
// util/AppDispatcher.kt
package com.mascill.healthtrack.util

import kotlinx.coroutines.CoroutineDispatcher

data class AppDispatcher(
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
```

### Constants
```kotlin
// util/Constants.kt
package com.mascill.healthtrack.util

object Constants {
    const val TIMEOUT_SECONDS = 30L
    // URL and keys: BuildConfig from local.properties — never here.
}
```

## Step 5: Create DI Modules

See `sm-dependency-injection.mdc`: `SupabaseModule` (Auth + Postgrest), `NetworkModule` (OkHttp only), `RepositoryModule` (`@Binds`), `DataStoreModule`, `DatabaseModule`. Do not add Retrofit.

## Step 6: Create Navigation

See `sm-navigation.mdc` for Navigation 3 (`NavDisplay`, `NavKey`, `rememberNavBackStack`).

## Step 7: Create Theme

```kotlin
// presentation/theme/Theme.kt
package com.mascill.healthtrack.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme()

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
```

## Step 8: Update AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppName">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.AppName">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

## Checklist

- [ ] Project created in Android Studio
- [ ] Package structure created
- [ ] build.gradle.kts configured
- [ ] libs.versions.toml configured
- [ ] App.kt created with @HiltAndroidApp
- [ ] MainActivity.kt created with @AndroidEntryPoint
- [ ] AppDispatcher utility created
- [ ] Constants file created
- [ ] DI modules created (`SupabaseModule`, `NetworkModule` OkHttp, `RepositoryModule`, `DataStoreModule`, `DatabaseModule`)
- [ ] Navigation setup
- [ ] Theme setup
- [ ] AndroidManifest.xml updated (`INTERNET`)
- [ ] DataStore `healthtrack_prefs` and Room are both present
- [ ] No Retrofit / Moshi
- [ ] Build successful
