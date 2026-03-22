[English](README.md)

# kazahana for Android

**軽量な Bluesky Android クライアント**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 概要

kazahana for Android は、Kotlin と Jetpack Compose で構築されたネイティブ Bluesky クライアントです。
[デスクトップ版](https://github.com/osprey74/kazahana)と同じ「軽量・高速・シンプル」な体験を Android デバイスで提供します。

## 設計思想

kazahana は全機能を網羅するスタンドアロンアプリではなく、**軽快に日常利用する閲覧・投稿特化クライアント**です。

- **日常操作は kazahana で** — タイムライン閲覧、投稿、通知確認、検索（履歴付き）、フィード管理、DM など。
- **設定・管理は Bluesky ウェブ版で** — アカウント管理、ブロック/ミュート一覧管理などは [bsky.app](https://bsky.app/) で行う前提です。

## 技術スタック

| 技術 | 用途 |
|------|------|
| [Kotlin](https://kotlinlang.org/) | プログラミング言語 |
| [Jetpack Compose](https://developer.android.com/develop/ui/compose) | UIフレームワーク |
| [AT Protocol](https://atproto.com/) | Bluesky API |

## 動作要件

- Android 10 (API 29) 以上
- Android Studio Ladybug (2024.2) 以降

## 開発

```bash
# リポジトリのクローン
git clone https://github.com/osprey74/kazahana-android.git

# Android Studio で開く
# File > Open > kazahana-android ディレクトリを選択
```

## 関連プロジェクト

- [kazahana](https://github.com/osprey74/kazahana) — デスクトップ版 (Windows / macOS)
- [kazahana-ios](https://github.com/osprey74/kazahana-ios) — iOS版
- [BSAF Protocol](https://github.com/osprey74/bsaf-protocol) — Bluesky Structured Alert Feed 仕様

## ライセンス

[MIT License](LICENSE)
