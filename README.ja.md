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

## 機能

- **タイムライン** — ホームタイムライン、Pull-to-Refresh、無限スクロール、フィード/リスト切り替え
- **投稿** — テキスト、画像（ALTテキスト付き）、リプライ、引用リポスト、スレッドゲート/ポストゲート
- **リッチテキスト** — メンション、URL、ハッシュタグの自動ファセット生成
- **メディア** — 画像グリッド、ピンチズーム対応フルスクリーンLightbox、ALTテキスト表示、動画再生（HLS）
- **リンクカード** — OGPメタデータプレビュー
- **通知** — 通知一覧＋未読バッジ、バックグラウンドポーリング
- **プロフィール** — ユーザープロフィール（投稿/リプライ/メディア/いいね/スターターパックタブ）、フォロー/フォロー解除
- **検索** — 投稿・ユーザー検索、検索履歴（保存/削除/一括削除）
- **フィード管理** — カスタムフィード/リストの表示・非表示・並び替え
- **ダイレクトメッセージ** — 会話一覧、送受信、リアクション
- **モデレーション** — コンテンツラベル、フィルタリング、ブラー、通報
- **BSAF対応** — 構造化アラートフィード、Botバッジ、深刻度カラーボーダー
- **共有** — 共有シート連携（Intent Filter）、ディープリンク（App Links）
- **設定** — テーマ（ライト/ダーク/システム連動）、投稿元表示トグル、サポートセクション（Ko-fi）
- **多言語対応** — 11言語（EN, JA, DE, ES, FR, ID, KO, PT, RU, ZH-CN, ZH-TW）

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
