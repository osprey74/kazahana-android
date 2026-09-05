# kazahana-android タスク管理

## 進捗サマリー

- Phase 1 (基盤構築): 5/5 ✅
- Phase 2 (コア機能): 8/8 ✅
- Phase 3 (通知・プロフィール・検索): 7/7 ✅
- Phase 4 (DM・モデレーション・設定): 8/8 ✅
- Phase 5 (BSAF・高度な機能): 4/4 ✅
- Phase 6 (iOS/Desktop パリティ): 20/20 ✅
- Bluesky v1.123 対応 (gallery / 動画300MB / 投稿進捗UI): 12/15 ✅（残: getUploadLimits / Photo Picker 順序検証 / メモリ検証）
- 不具合修正 + DM返信機能 (2026-06-20): OGP文字化け / 引用投稿表示 / 返信先表示 / DM返信(v1.125) / アカウント切替UI / キーボード自動クローズ ✅
- Desktop パリティ (2026-09-05): 動画ALTテキスト表示（全表示面）/ OPスレッド番号付けバッジ 2/2 ✅
- Google Play 新品質要件 (2027-02/04 施行): メモリ・ビットマップ・DEX 実測クリア ✅ / Zero-Tap Sign-In は当面対応せず様子見（2026-09-05 判断）

## Phase 1: 基盤構築

- [x] Android Studio プロジェクト初期化 (Jetpack Compose, API 29+)
- [x] AT Protocol HTTP クライアント (Ktor Client or OkHttp)
- [x] 認証機能 (ログイン / セッション永続化 / トークンリフレッシュ / ログアウト)
- [x] ホームタイムライン表示 (getTimeline + 投稿カード)
- [x] Pull-to-Refresh + 無限スクロール

## Phase 2: コア機能

- [x] 投稿作成 (テキスト + 画像添付 + Altテキスト)
- [x] リッチテキスト (メンション / URL / ハッシュタグ検出 + ファセット生成)
- [x] いいね / リポスト / ブックマーク
- [x] リプライ / 引用リポスト
- [x] 画像表示 (グリッド + フルスクリーン拡大 + ピンチズーム)
- [x] 動画再生 (Media3 ExoPlayer + HLS)
- [x] リンクカード (OGPプレビュー)
- [x] スレッド表示

## Phase 3: 通知・プロフィール・検索

- [x] 通知一覧 + 未読バッジ
- [x] プッシュ通知 / WorkManager ポーリング
- [x] プロフィール表示 (タブ: 投稿/リプライ/メディア/いいね)
- [x] フォロー / フォロー解除
- [x] 検索 (投稿 / ユーザー)
- [x] カスタムフィード / リストフィード切り替え
- [x] 検索履歴 (最大20件保存 / タップで再検索 / 個別削除 / 一括削除)

## Phase 4: DM・モデレーション・設定

- [x] ダイレクトメッセージ (会話一覧 / 送受信 / リアクション)
- [x] コンテンツモデレーション (ラベル判定 / フィルタ / ブラー)
- [x] 通報機能
- [x] テーマ (ライト / ダーク / システム連動)
- [x] 多言語対応 (11言語)
- [x] 設定画面
- [x] フィード管理 (全フィード/リスト取得 + ユーザーリストマージ + 表示/非表示/並び替え)
- [x] ハードコード英語文字列の多言語対応

## Phase 5: BSAF・高度な機能

- [x] BSAF対応 (Bot登録 / フィルタリング / 深刻度カラーボーダー)
- [x] スレッドゲート / ポストゲート
- [x] 共有シート連携 (Intent Filter)
- [x] ディープリンク (App Links)

## Phase 6: iOS/Desktop パリティ (機能差異解消)

- [x] タイムライン自動ポーリング (設定間隔で自動更新)
- [x] 自動更新間隔設定UI (30/60/90/120秒)
- [x] DM自動ポーリング (会話15秒 / 一覧30秒)
- [x] DM URL/ハッシュタグのリンク化 (RichTextContent)
- [x] 投稿テキストのリッチテキスト表示 (サーバーfacets対応 + ハッシュタグ/メンション/URLタップ)
- [x] 画像自動圧縮 (950KB上限、JPEG品質段階圧縮)
- [x] DMメッセージ削除 (deleteMessageForSelf API)
- [x] プロフィール フィード一覧タブ
- [x] プロフィール リスト一覧タブ
- [x] プロフィール スターターパックタブ
- [x] DMメッセージリクエスト承認 (acceptConvo API + UI)
- [x] DM新規会話作成 (NewConversationScreen + 検索履歴)
- [x] プロフィールからの自動メンション挿入 (FABタップで@handle付きCompose)
- [x] Claude APIキー管理 (設定画面のSecureField)
- [x] ALTテキスト自動生成 (Claude Haiku API、アプリ言語設定連動)
- [x] 画像クロップ (オリジナル/正方形/自由 3モード)
- [x] 画像回転 (90度単位)
- [x] 下書き保存 (最大20件、画像/動画ファイル永続化)
- [x] フィード並び替え ドラッグ&ドロップ
- [x] PreferenceItem.items デシリアライズ修正 (hiddenPostsPref対応)

## コード品質改善

- [x] Json インスタンスの共通化 (AppJson シングルトン導入)
- [x] SettingsViewModel のボイラープレート削除 (combine + stateIn)
- [x] ComposeViewModel からファイル I/O を ImageHelper に分離

## UI/UX 改善

- [x] フィードドロップダウンから非表示フィード選択時にタブの青い下線が残るバグ修正
- [x] スレッド内の非フォーカスポストタップで新しいスレッド画面を開く (iOS版準拠)
- [x] 設定画面で投稿ボタン (FAB) を非表示にする
- [x] 設定画面にサポートセクション追加 (Ko-fi ウィジェット)
- [x] ポスト表示のアバタータップでプロフィール画面を開くよう修正 (全画面共通: ホーム/検索/通知/メッセージ/プロフィール/リスト・フィード)
- [x] ポスト表示のハンドル名タップはスレッド表示に遷移するよう修正 (全画面共通: ホーム/検索/通知/メッセージ/プロフィール/リスト・フィード)
- [x] ホームタブのリストタブを再タップで強制再読み込み＆最新ポストを最上部に表示
- [x] ポストにLANGSとVIA (投稿元アプリ名) を追加表示 (iOS版参照)
- [x] 設定画面に「投稿元を表示 (via)」トグルを追加 (iOS版参照)
- [x] 画像/動画を含むポストにALTテキストを表示 (ポスト上は最長128文字、Lightbox表示では全文表示) (iOS版参照)

## UI調整

- [x] プロフィール画面からフィード・リストタブを削除 (軽量クライアント方針に合わせて簡素化)
- [x] BSAF BOT投稿の左ボーダー太さを4dp→8dpに変更

## standard.site 連携 (長文投稿サービス ハンドオフ)

- [x] 設定画面に「長文投稿サービス URL」入力欄を追加 (https:// バリデーション + standard.site への外部リンク)
- [x] DataStore に `long_form_service_url` キーを追加
- [x] コンポーザに「長文を書く」ボタンを追加 (URL 設定時のみ表示、Custom Tabs で起動)
- [x] androidx.browser:browser 依存を追加
- [x] 11ロケール (en/ja/de/es/fr/id/ko/pt/ru/zh-rCN/zh-rTW) に文字列リソース追加

## Standard Site 拡張リンクカード (HANDOFF_kazahana-standard-site-embed.md)

### Phase 1: 受信・表示

- [x] `ExternalView` を Standard Site 拡張フィールド対応に拡張 (createdAt/updatedAt/readingTime/source/associatedRefs/associatedProfiles)
- [x] `ExternalSource` / `ExternalSourceTheme` / `ColorRGB` / `StrongRef` データクラス追加
- [x] `LinkCard` を3パターン分岐に拡張 (document only / document+publication / publication only)
- [x] 読了時間 (Schedule アイコン + "Nm") と公開日 (ロケール準拠 MEDIUM) を表示
- [x] パブリケーションセクション (アイコン + タイトル + 説明 + View publication ボタン) を追加
- [x] accentRGB/accentForegroundRGB のみテーマ適用 (background/foreground は不採用、ダークモード可読性優先)
- [x] 11ロケールに `link_card_view_publication` 文字列を追加
- [x] Desktop 版に合わせ、ボタン文言を「公開元を見る」に変更 (ja)
- [x] パブリケーションセクションに著者名 (`associatedProfiles[0].handle`) を「著者：@handle」形式で表示 (`link_card_author`)

### Phase 2: コンポーザー連携

- [x] 投稿レコード組み立て側に `associatedRefs` 送信を追加 (`ExternalEmbedData` + `buildExternalEmbed`)
- [x] リンクプレビュー取得を `app.bsky.embed.getEmbedExternalView` に切り替え (HTML から site.standard.* AT-URI 抽出 + OGP フォールバック)
- [x] `OgpService` に `fetchHtml` / `extractStandardSiteUris` / `parseOgp` を追加
- [x] `PostRepository.getEmbedExternalView(url, uris)` を追加 (`uris` 必須)
- [x] コンポーザープレビューで拡張カード表示 (`LinkCard(hideSubscribe = true)`)

## Bluesky v1.123 対応 (HANDOFF_kazahana-bsky-v1.123.md) — 2026-06-09

> 設計書: `../../HANDOFF_kazahana-bsky-v1.123.md`
> 背景: 2026-06-06 リリースの Bluesky 公式 v1.123 で `app.bsky.embed.gallery`（写真 10 枚 / 5 枚以上カルーセル）が正式リリース、動画 300MB 化の feature gate も解除された
> 一次情報: atproto #4827 (merged 2026-06-03) / social-app #10707 #10497 #10683 / lexicon: `app.bsky.embed.gallery`（`maxLength: 20`、ソフト上限 10、`#image.required = ["image", "alt", "aspectRatio"]`）
> 重要: 動画 lexicon `video.maxSize` は **100MB のまま**。300MB はサーバ受容範囲（トランスコード前提）。`app.bsky.video.getUploadLimits` 応答の尊重を推奨
> Android 特記: 複数枚画像表示は既に `HorizontalPager` 採用済 (`ImageGrid.kt:221-244`)。Pager 基盤を流用してカルーセル拡張可能

### Phase A: 受信側 embed.gallery viewer 対応（最優先・3 プラットフォーム同時リリース推奨）

- [x] **[A-G1] `app.bsky.embed.gallery#view` レンダラ追加** — `PostEmbedView.displayImages` で `items` (gallery) を `ImageView` にマップし既存 `ImageGrid` / `HorizontalPager` 基盤に流す
- [x] **[A-G2] ≤4 枚グリッド / ≥5 枚カルーセル分岐** — Android は元々複数枚を `HorizontalPager` カルーセルで表示する設計のため、gallery (5–10 枚) もそのまま同基盤で表示（2–4 枚をグリッドに変える既存設計変更は見送り）
- [x] **[A-G3] `recordWithMedia` 内 gallery 対応** — `post.embed?.displayImages ?: post.embed?.media?.displayImages`（PostCard / NotificationScreen / QuoteCard の全受信箇所）
- [x] **[A-G4] 未知 union ref のスキップ実装** — `GalleryViewImage.toImageView()` が `thumbnail`/`fullsize` 欠落（未知 ref）を null で返し `mapNotNull` でスキップ

### Phase B: 送信側 composer 対応 + 動画 300MB

- [x] **[A-G5] `MAX_IMAGES = 4` → `10` に拡張** — `ComposeViewModel.kt:146`（`ComposeUiState.MAX_IMAGES`）。`PickMultipleVisualMedia(maxItems = MAX_IMAGES)` も連動
- [x] **[A-G6] 5 枚以上選択時に `embed.gallery` で送信** — `PostRepository.buildMediaImagesEmbed`：`images.size > GALLERY_PROMOTE_THRESHOLD(4)` で `buildGalleryEmbed`、≤4 は `buildImagesEmbed`（recordWithMedia の media 側も同分岐）
- [x] **[A-G7] gallery `#image` に alt / aspectRatio 必須付与** — `buildGalleryEmbed` で `$type=app.bsky.embed.gallery#image` + `image`/`alt`/`aspectRatio`（aspectRatio 不明時は 1:1 フォールバックで required を担保）
- [x] **[A-G8] 動画上限 100MB → 300MB** — `ComposeViewModel.kt` `MAX_VIDEO_BYTES = 300_000_000L`（エラー文言も "Max 300 MB" に更新）
- [ ] **[A-G9] `app.bsky.video.getUploadLimits` 応答尊重** — 未着手（follow-up）。現状は 300MB 固定ガードのみ。将来サーバ応答の `canUpload` / `remainingDailyBytes` を尊重する実装に置換推奨
- [x] **[A-G10] 動画ジョブポーリング調整** — `PostRepository` のポーリングを 60 回 → 150 回（×2 秒 = ~300 秒）に延長
- [ ] **[A-G11] Photo Picker 選択順保持確認** — 未検証（follow-up）。実機 / OS バージョン別の選択順保持を要確認

### Phase C: 品質改善（任意）

- [x] **[A-G12] カルーセル枚数バッジ表示** — `ImageGrid.kt`：5 枚以上で右上に "現在/総数" バッジ（`pagerState.currentPage + 1`/`images.size`、ブラー時は非表示）
- [ ] **[A-G13] メモリ使用量検証** — 未検証（follow-up）。10 枚 ×2MB 同時保持で OOM が起きないか、`inSampleSize` 段階デコードと並行処理数の制御を検証
- [x] **[A-G14] gallery embed モデル定義の追加** — `Post.kt`：`PostEmbedView.items: List<GalleryViewImage>` + 算出 `displayImages`、`GalleryViewImage`（`thumbnail`/`fullsize`/`alt`/`aspectRatio` + `toImageView()`）を追加。`thumbnail` フィールド差分・union 安全性に対応済
- [x] **[A-G15] 投稿進捗インジケーター（デスクトップ版パリティ）** — 投稿中に段階表示：画像カウンタ＋進捗バー（`画像をアップロード中 (n/total)`）→ 動画アップロード `%` → `変換処理中…` → `投稿を準備中…` → `投稿中…`。`ComposeUiState.imageProgress`/`videoProgress`、`PostRepository.uploadVideo(onProgress)` + Ktor `onUpload` バイト進捗、`ComposeScreen` の `LinearProgressIndicator`。全11ロケールに文字列追加（desktop 訳流用）

## 不具合修正 + DM返信機能 (HANDOFF_kazahana-bugs-2026-06-20.md ほか) — 2026-06-20

> 起票元: HANDOFF_kazahana-bugs-2026-06-20.md（OGP 文字化け / 引用投稿 / 返信先表示）+ ユーザ報告（DM 返信機能・アカウント切替 UI・キーボード）

### 不具合修正

- [x] **OGP 文字化け** — `OgpService.fetchHtml` を `bodyAsText()`（UTF-8 固定）から `readBytes()` + 文字コード自動判定に変更。HTTP `Content-Type` charset → 先頭4096バイトの `<meta charset>`/`http-equiv` → UTF-8 の順（HTML Living Standard 準拠）。Shift_JIS / EUC-JP 等の文字化けを解消
- [x] **引用投稿が表示されない** — `PostEmbedView.quotedRecord` を追加し、`record#view`（1段）/`recordWithMedia#view`（2段）のネスト差を構造ベースで吸収。`PostCard` の引用抽出を差し替え（handoff の「media.record」説は誤りで、実際は通常引用が 2 段固定参照で落ちていた）
- [x] **タイムラインで返信先が不明** — `PostCard` の返信インジケータを `feedPost.reply.parentPost?.author?.handle` で「@handle への返信」表示に変更（取得不可時は「返信」にフォールバック）。文字列 `post_reply_to` を EN/JA 追加

### DM / グループチャット 返信機能（Bluesky v1.125 互換）

- [x] **送信** — `ChatRepository.sendMessage(convoId, text, replyToMessageId?)`。`message.replyTo = { messageId }`（`chat.bsky.convo.defs#replyRef`）。失敗時はエラーコード名を surface
- [x] **受信モデル** — `ChatMessageOrDeleted.replyTo`（union `#messageView`/`#deletedMessageView`）+ `replyToRef`（`ChatReplyRef`）。`SendMessageResponse.replyTo` 追加
- [x] **ViewModel** — `ChatReplyTarget` / `replyTo` / `sendError` state、`startReply`/`cancelReply`/`consumeSendError`。楽観的バブルはサーバ反響 or ローカル構築 JSON で引用即表示。`ReplyTargetNotFound` で返信解除
- [x] **UI** — 長押しピッカーに返信アイコン、コンポーザ上部の「返信先」バー（キャンセル可）、受信バブル内の引用プレビュー。文字列 `messages_reply*` を EN/JA 追加
- [x] **引用タップでスクロール + 青フラッシュ** — `animateScrollToItem` + `Animatable`（0→1→0、約0.9秒、`Color(0xFF3884FF)`）でデスクトップ `kazahana-message-flash` 相当
- [x] **DM 送信後キーボード自動クローズ** — 送信時に `SoftwareKeyboardController.hide()` + `FocusManager.clearFocus()`。`imePadding` による画面ずれを解消

### アカウント切り替え UI

- [x] **切り替え中ローディング** — `AuthViewModel.isSwitchingAccount` + `switchAccount` の再入ガード/フラグ管理。`AccountPickerScreen` にフルスクリーンオーバーレイ（暗幕 + スピナー + 「アカウントを切り替え中…」、タップ無効化）。iOS `auth.switchingAccount` 相当の文字列を EN/JA 追加

### follow-up（任意・未対応）

- [ ] 引用投稿のうち feed/list/starterPack 引用は author 不在で未表示（通常引用は対応済）
- [ ] 引用先が削除済み/ブロックの場合のプレースホルダ表示（デスクトップは「削除されました」表示あり）

## Desktop パリティ (PLATFORM_MATRIX 差異是正) — 2026-09-05

> 起票元: `../kazahana/docs/PLATFORM_MATRIX.md` の Android 列 `❓` / `⬜` 解消
> 対象: 「動画 ALT テキスト表示」(2章) / 「OP スレッド番号付けバッジ」(2章)

### 動画 ALT テキスト表示（全表示面で網羅）

- [x] **`PostEmbedView.displayVideo` 追加** — `Post.kt`：`app.bsky.embed.video#view`（トップレベル）と `recordWithMedia#view` の `media` 側を `VideoEmbedView`（playlist / thumbnail / alt / aspectRatio）に正規化。従来 `post.embed?.playlist` 直参照だったため recordWithMedia の動画が描画されない不具合も同時に解消
- [x] **動画サムネの ALT バッジ** — `VideoPlayer.kt`：`alt` パラメータ追加、左上に半透明「ALT」バッジ（`semantics { contentDescription }` に ALT 全文）。デスクトップ版パリティ
- [x] **引用投稿内（QuoteCard）の動画描画 + ALT** — `PostCard.kt`：`viewRecord.embeds` から `displayVideo` を抽出して描画。従来は引用カード内で画像のみ表示し動画を破棄していた
- [x] **通知画面の ALT バッジ + 本文** — `NotificationScreen.kt`：動画サムネ左下に ALT バッジ、直下に ALT 本文（128 文字トランケート）
- [x] **描画の共通化** — `PostCard.VideoEmbed` にプレイヤー + ALT 本文 + モデレーション警告を集約し、本文・引用カードで共用。ALT トランケートは `truncateAlt`（`ImageGrid.kt`、`internal` 化）に統一
- [x] **メディア一括保存の追随** — `hasMedia` / `onSaveMedia` を `displayVideo` 基準に変更し、recordWithMedia の動画も保存対象に

### OP スレッド番号付けバッジ

- [x] **`FeedViewPost.opThreadPostIndex` / `opThreadPostCount` 追加** — `Post.kt`。AppView が `CanonicalPostNumberingEnable` フラグ下で配信する暫定フィールド（social-app PR #11472・公式 v1.130 互換）。canonical lexicon 未反映のため optional
- [x] **`opThreadNumbering` アクセサ** — `count >= 2` かつ `1 <= index <= count` のときのみバッジを出す（フィールド欠落・フラグ OFF・不整合値は null）。デスクトップ `src/lib/opThread.ts` と同一判定
- [x] **バッジ描画** — `PostCard.kt` 著者行の時刻右に `FormatListNumbered` アイコン + `index/count`。`contentDescription` に `post_op_thread`
- [x] **文字列 `post_op_thread` を全11ロケールに追加** — ja/en/pt/de/zh-TW/zh-CN/fr/ko/es/ru/id

## Google Play 新品質要件への対応 — 2026-09-05 起票

> 一次情報: https://android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html
> しきい値の実数値: https://support.google.com/googleplay/android-developer/answer/17492799
> 施行: メモリ / ビットマップ / DEX = 2027-02、Zero-Tap Sign-In = 2027-04
> 非準拠時はアプリの表示（visibility）と公開機能に影響

### 調査・実測（完了）

- [x] **[Q-1] DEX コード最適化（≥25%、DEX 10MB 超のアプリに適用）** — 対応済みかつ適用対象外。`app/build.gradle.kts` で `isMinifyEnabled` / `isShrinkResources` + `proguard-android-optimize.txt`、`proguard-rules.pro` に `-dontobfuscate` / `-dontoptimize` / `-dontshrink` なし。リリース AAB の DEX 実測 **7.34 MB**（`base/dex/classes.dex` 7,698,468 バイト）で 10MB 閾値を下回る
- [x] **[Q-2] 計測スクリプト作成** — `tools/measure-memory.sh`。`/proc/<pid>/status` の `RssAnon + VmSwap`（Android vitals の "Memory usage (Anonymous RSS + swap)" と同一定義）+ `dumpsys meminfo` の App Summary + `oom_score_adj` からのプロセス状態判定。`ADB` / `PKG` を環境変数で差し替え可能
- [x] **[Q-3] 実機メモリ実測** — moto g66j 5G / Android 16 (API 36) / RAM 7.42GB（**8GB バケット**）/ `com.kazahana.app.debug` v3.6.0。**結論: 全項目クリア、対応不要**

| 計測点 | 状態 | Anon RSS+Swap | しきい値 | 使用率 |
|---|---|---:|---:|---:|
| ベースライン | 前景 | 98.3 MB | 2.25 GB | 4.3% |
| 150スワイプ後 | 前景 | 117.0 MB | 2.25 GB | 5.1% |
| 350スワイプ後 | 前景 | 126.8 MB | 2.25 GB | 5.5% |
| バックグラウンド30秒 | 背景 | 100.1 MB | 1.5 GB | 6.7% |
| サイクル2 背景30秒 | 背景 | 104.8 MB | 1.5 GB | 7.0% |

- [x] **[Q-4] ビットマップメモリ実測** — バックグラウンドで Native Heap **27.7 MB**（しきい値 200 MB / 13.9%）。Coil 3.0.2 の `AndroidSystemCallbacks.onTrimMemory` が `TRIM_MEMORY_BACKGROUND`(40) で `memoryCache.clear()` を実行することをバイトコードで確認し、実機でも解放を確認（Java Heap −50% / Graphics −39% / Anon RSS −18%）
- [x] **[Q-5] メモリリーク判定** — 陰性。公式基準は「P90/P50 比 3.5倍超でリーク疑い」。2 サイクル計 350 スワイプで前景ピーク +8%、背景復帰値 +4.7% に留まり、セッション延長による蓄積なし。公式が名指しする最難シナリオ「media-heavy infinite scrolling」での結果

> 計測は debug ビルド（R8 未適用）のため上限値。リリースビルドはこれを下回る。
> 未検証: `FullscreenImageViewer` の連続表示 / ExoPlayer の連続再生 / user-perceived services 状態（いずれも UI 自動操作で到達不可）。公開後は Play Console の実データ（28日分 P50/P90/P99）で再確認すること

### 未対応

- [ ] **[Q-6] Zero-Tap Sign-In（Restore Credentials API）** — 施行 2027-04。**当面対応しない方針（2026-09-05 判断）。様子見のうえ再検討する。**

#### 要件の中身

「機種変更してもサインイン状態を維持できるようにせよ」という要求。kazahana は「サインインをサポートするアプリ」に該当し、免除条件（Block Store 統合を 2026-09-30 までに本番投入 / 完全非公開・企業端末管理 / 金融・医療の規制要件 / ゲーム）のいずれにも非該当。

#### 見送りの判断根拠

1. **Restore Credentials API は FIDO 準拠バックエンド必須** — 実装ガイドに "Yes, a backend server is required. Use the same FIDO-compliant server implementation as passkeys" と明記。ペイロードは `PublicKeyCredentialCreationOptionsJSON`（WebAuthn）で、登録・認証の両方でサーバ検証が要る。kazahana は Bluesky PDS に直接認証する構成でサーバを持たず（`kazahana-push-backend` はプッシュ専用）、AT Protocol にサードパーティ向け FIDO エンドポイントも存在しない。実装するには Bluesky 認証情報をサーバ側で保持する構造への変更が必要で、「認証情報は端末内に留まる」という設計の放棄になる

2. **OAuth 移行では対応不能** — AT Protocol OAuth 仕様が `"Tokens must not be shared or reused across client devices"` と端末間移動を明示的に禁止。DPoP 必須、パブリッククライアントのセッション/リフレッシュトークンは 2 週間上限・単回使用。**セキュリティ的に優れた OAuth へ移行するほど Play 要件を満たせなくなる**という構造的矛盾がある

3. **セキュリティ的な後退を伴う** — 現行は `SessionStore.kt` の `EncryptedSharedPreferences` + Android Keystore `MasterKey`。Keystore 鍵はハードウェア束縛で端末外に持ち出せないため機種変更で復号不能になる（＝これが再ログインの理由）。要件充足には秘密を Keystore の保護外（Block Store / Restore Credentials）へ出す必要がある。Google 側も E2EE + 画面ロック必須で無防備ではないが、攻撃面が広がるのは事実。加えて現在 kazahana はアプリパスワード自体を保存していない（`Session` は did/handle/accessJwt/refreshJwt/email のみ）ため、Block Store 対応は「今は保存していない、より強い秘密を保存し始める」ことを意味する。refreshJwt を運ぶ案はローテーションで失効する窓が残る

4. **制裁内容が未公表** — 公式は "may see reduced app visibility and publishing capabilities" とのみ述べ、"Additional details will be provided later this year"（2026年内に詳細提供）としている。既存の technical quality bar（2022-11 運用開始）の実績では、制裁は「発見面(recommendations)からの除外」と「ストア掲載への警告表示」で、**アプリ削除・更新公開停止・既存ユーザーへの影響は行われていない**。kazahana は指名検索中心のサードパーティクライアントで、おすすめ経由の新規流入依存度が低い

5. **猶予が長い** — 施行 2027-04 まで約19か月。Block Store 免除（2026-09-30 期限）は意図的に見送る

#### 再検討トリガー（いずれかが起きたら再評価する）

- [ ] **Google が制裁の詳細を公表したとき**（2026年内予定）— 「publishing capabilities」の具体的制限内容が判明した時点で影響度を再評価
- [ ] **Play Console の Android vitals 概要ページに警告が出たとき** — 公式が "we'll provide a warning directly on the Android vitals overview page" としており、これが実際の判定シグナル
- [ ] **Bluesky がアプリパスワードを廃止 / kazahana が OAuth へ移行するとき** — 移行後は Zero-Tap 対応が仕様上不能になるため、Google への免除申請等の別対応が必要になる
- [ ] **2027-04 の施行が近づいたとき**（目安 2027-01）— それまでに状況が変わっていなければ、非充足を受容するか再検討するかを最終判断

#### 参照

- https://developer.android.com/identity/sign-in/restore-credentials-implementation （バックエンド必須の記述）
- https://atproto.com/specs/oauth （端末間移動の禁止）
- https://developer.android.com/identity/block-store （免除経路・2026-09-30 期限）
- https://support.google.com/googleplay/android-developer/answer/17492799#zero-tap_sign-in_restoration （免除条項）
