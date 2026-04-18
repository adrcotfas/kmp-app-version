# kmp-app-version

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Gradle plugin that keeps your Android and iOS app versions in sync from a single source of truth: the Gradle version catalog (`libs.versions.toml`).

## What it does

| Platform | How |
|----------|-----|
| **Android** | Sets `versionCode` and `versionName` on all variants automatically at build time via the AGP variant API |
| **iOS** | Rewrites `CURRENT_PROJECT_VERSION` and `MARKETING_VERSION` in your `.xcconfig` before the KMP framework is compiled |

No more updating versions in multiple places.

## Setup

### 1. Add to `libs.versions.toml`

```toml
[versions]
app-version-code = "9"
app-version-name = "1.0"

kmp-app-version = "1.0.0"

[plugins]
kmp-app-version = { id = "io.github.adrcotfas.kmp-app-version", version.ref = "kmp-app-version" }
```

### 2. Apply the plugin

In your **root** `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kmp.app.version) apply false
}
```

In your **Android app module** `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kmp.app.version)
}
```

You can remove the hardcoded `versionCode`/`versionName` from your `android { defaultConfig { } }` block; the plugin overrides them.

### 3. (Optional) Configure

If your version catalog keys differ from the defaults, or you want to specify the xcconfig path explicitly:

```kotlin
kmpAppVersion {
    versionCodeKey = "my-version-code"   // default: "app-version-code"
    versionNameKey = "my-version-name"   // default: "app-version-name"
    xcconfigFile   = file("iosApp/Configuration/Config.xcconfig")  // auto-detected if omitted
}
```

## iOS integration

The plugin auto-detects your `.xcconfig` file (the one containing `CURRENT_PROJECT_VERSION=`) and updates it before any KMP iOS framework compilation task runs (`link*Ios*`, `embedAndSignAppleFrameworkForXcode`). This means iOS builds from Android Studio work automatically.

### For Xcode-only builds

When building directly from Xcode, the xcconfig is read before the build starts. To ensure the version is always up-to-date on the first build, run this **once**:

```
./gradlew setupXcodeVersionSync
```

This injects a pre-build action into your `.xcscheme` files that calls `./gradlew syncIosVersion` before every Xcode build.

You can also run the sync manually at any time:

```
./gradlew syncIosVersion
```

## Requirements

- Gradle version catalog named `libs`
- AGP 9.x (for Android variant support)
- KMP project with iOS targets using an `.xcconfig` file
