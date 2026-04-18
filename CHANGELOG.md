# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-04-19

### Added
- Initial release
- Reads `versionCode` and `versionName` from `libs.versions.toml` (keys `app-version-code` and `app-version-name` by default)
- Sets Android `versionCode`/`versionName` on all variants via the AGP variant API
- Auto-detects and rewrites `CURRENT_PROJECT_VERSION` / `MARKETING_VERSION` in `.xcconfig` before KMP iOS framework compilation
- `syncIosVersion` task for manual xcconfig sync
- `setupXcodeVersionSync` task to inject a Gradle pre-action into `.xcscheme` files for Xcode-only builds
- `kmpAppVersion` extension for customising catalog keys and xcconfig path
