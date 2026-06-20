# v3.5.0

## English

### New

- **Reply to a specific message in DMs / group chats** (Bluesky v1.125). Long-press a message and tap **Reply** to quote it. The conversation now shows which message each reply is responding to, and tapping that preview scrolls to the original message and briefly highlights it.

### Improvements

- **Account switching** now shows a "Switching account…" loading overlay so it's clear the switch is in progress.

### Fixes

- **Link cards (OGP):** fixed garbled titles/descriptions for non-UTF-8 pages (Shift_JIS / EUC-JP, etc.). The character encoding is now detected automatically.
- **Quote posts** that previously failed to appear in the timeline and threads are now displayed correctly.
- **Replies in the timeline** now show who they are replying to ("Replying to @handle").
- The keyboard now closes automatically after sending a DM, so the screen no longer stays pushed up.

## 日本語

### 新機能

- **DM・グループチャットで特定のメッセージに返信**できるようになりました（Bluesky v1.125 対応）。メッセージを長押しして「返信」をタップすると引用できます。各返信がどのメッセージへの返信かが会話内に表示され、その引用部分をタップすると元のメッセージまでスクロールして一瞬ハイライト表示します。

### 改善

- **アカウント切り替え**時に「アカウントを切り替え中…」のローディング表示が出るようになり、処理中であることが分かりやすくなりました。

### 修正

- **リンクカード（OGP）:** UTF-8 以外（Shift_JIS / EUC-JP など）のサイトでタイトル・説明文が文字化けする不具合を修正しました。文字コードを自動判定するようになりました。
- タイムラインやスレッドで**引用投稿が表示されない**不具合を修正しました。
- タイムラインの**返信に返信先（「@handle への返信」）**が表示されるようになりました。
- DM 送信後にキーボードが自動的に閉じるようになり、画面が上にずれたままになる不具合を修正しました。
