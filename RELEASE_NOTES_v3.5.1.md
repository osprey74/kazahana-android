# v3.5.1

## English

### Improvements

- **Android 16 (API level 36) support.** The app now targets the latest Android version to meet Google Play's target API level requirement.

### Fixes

- Fixed WorkManager on-demand initialization by removing the default startup initializer from the app manifest.

### Internal

- Upgraded the build toolchain: Android Gradle Plugin 8.11.1, Gradle 8.13, compileSdk/targetSdk 36.

## 日本語

### 改善

- **Android 16（API レベル 36）に対応**しました。Google Play の対象 API レベル要件に準拠するため、最新の Android バージョンを対象にしています。

### 修正

- WorkManager のオンデマンド初期化について、アプリのマニフェストからデフォルトの初期化子を除去して修正しました。

### 内部

- ビルドツールを更新しました（Android Gradle Plugin 8.11.1 / Gradle 8.13 / compileSdk・targetSdk 36）。
