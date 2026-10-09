# リリース自動化（Google Play 自動アップロード）

タグ push で署名済み AAB をビルドし、Google Play の **内部テストトラック** へ
自動アップロードする。製品版への昇格は Play Console で手動判断する。

## 構成

| 要素 | 内容 |
|---|---|
| Gradle プラグイン | [Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) `com.github.triplet.play` 3.12.1 |
| CI | `.github/workflows/release.yml`（`v*` タグ push / 手動実行） |
| アップロード先 | `internal`（内部テスト） |
| リリースノート | `app/src/main/play/release-notes/<locale>/internal.txt` |

## 初回セットアップ

### 1. Google Cloud 側：サービスアカウントの作成

1. [Google Cloud Console](https://console.cloud.google.com/) でプロジェクトを作成（既存でも可）
2. 「API とサービス」→「ライブラリ」で **Google Play Android Developer API** を有効化
3. 「IAM と管理」→「サービス アカウント」→「サービス アカウントを作成」
   - 名前の例: `play-publisher`
   - ロールの付与は不要（権限は Play Console 側で与える）
4. 作成したサービスアカウント →「キー」→「鍵を追加」→「新しい鍵を作成」→ **JSON**
5. ダウンロードした JSON を控える（後述の 2 か所で使う）

### 2. Play Console 側：権限の付与

1. [Play Console](https://play.google.com/console/) →「ユーザーとアクセス権」
2. 「ユーザーを招待」でサービスアカウントのメールアドレス
   （`play-publisher@<project>.iam.gserviceaccount.com`）を追加
3. kazahana アプリに対して以下の権限を付与
   - **リリースを作成、編集、削除する**
   - **アプリへのアクセス権**（対象アプリに kazahana を指定）
4. 反映には数分〜最大 24 時間かかることがある

> **注意:** アプリの初回アップロードは Play Console から手動で行う必要がある。
> kazahana は対応済みのため、以降は自動アップロードが使える。

### 3. ローカル実行の準備

ダウンロードした JSON をリポジトリ直下に配置する（`.gitignore` 済み）。

```
G:\dev\kazahana-android\play-service-account.json
```

### 4. GitHub Secrets の登録

リポジトリ →「Settings」→「Secrets and variables」→「Actions」→「New repository secret」

| Secret 名 | 値の作り方 |
|---|---|
| `ANDROID_PUBLISHER_CREDENTIALS` | `play-service-account.json` の中身をそのまま貼り付け |
| `KEYSTORE_BASE64` | `base64 -w 0 keystore/release.jks`（WSL）の出力 |
| `KEYSTORE_PASSWORD` | `signing.properties` の `storePassword` |
| `KEY_ALIAS` | `signing.properties` の `keyAlias` |
| `KEY_PASSWORD` | `signing.properties` の `keyPassword` |
| `GOOGLE_SERVICES_JSON` | `app/google-services.json` の中身をそのまま貼り付け |
| `PUSH_API_SECRET` | `secrets.properties` の `PUSH_API_SECRET` |

`KEYSTORE_BASE64` を作るコマンド（WSL）:

```bash
cd /mnt/g/dev/kazahana-android
base64 -w 0 keystore/release.jks
```

> `signing.properties` / `keystore/release.jks` / `app/google-services.json` /
> `secrets.properties` / `play-service-account.json` はいずれも `.gitignore` 済み。
> **リポジトリにコミットしないこと。**

## リリース手順

1. 実装を main にコミット
2. `app/build.gradle.kts` の `versionCode` / `versionName` を更新
3. `RELEASE_NOTES_vX.Y.Z.md` と `RELEASE_NOTES_vX.Y.Z_store.md` を作成
4. **`app/src/main/play/release-notes/en-US/internal.txt` と
   `ja-JP/internal.txt` を `_store.md` の内容で更新**（忘れると前回の文言が
   そのまま Play に出る）
5. コミットしてタグを打つ

```bash
git tag -a vX.Y.Z -m "vX.Y.Z — 概要"
git push origin main
git push origin vX.Y.Z
```

6. GitHub Actions が AAB をビルドし、内部テストトラックへアップロードする
7. Play Console で内部テストを確認し、問題なければ製品版へ昇格

## ローカルから手動アップロードする場合

```bash
./gradlew :app:publishReleaseBundle
```

`play-service-account.json` が配置されていれば、そのまま内部テストトラックへ
アップロードされる。

## トラブルシューティング

| 症状 | 対処 |
|---|---|
| `The caller does not have permission` | Play Console の権限付与が未反映。数時間待って再実行する |
| `APK specifies a version code that has already been used` | `versionCode` の更新漏れ |
| `Changes cannot be sent for review automatically` | Console で未完了の下書きリリースが残っている。Console で破棄してから再実行 |
| KSP の `cleanFilenames` エラー | `./gradlew --stop` の後に `./gradlew clean` を実行（コード起因ではない） |
