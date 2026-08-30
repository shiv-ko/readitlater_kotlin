# Todo共有リンク保存 Androidアプリ 仕様書

## 1. 概要

Android版Chromeで複数タブを選択して共有した際、共有された複数URLを自作Todoアプリの「あとで読む」機能（Bookmark）へ一括登録するAndroidアプリを作成する。

保存先はRaindrop.io（外部サービス）ではなく、自作TodoアプリのバックエンドAPI（`/bookmarks/batch`）とする。認証はTodoアプリが使用するAWS Cognito User Poolのログインで行う。

Todoアプリ本体は別リポジトリで管理される。本リポジトリはAndroidの共有先クライアント（本アプリ）のみを対象とする。

### 想定する操作

```text
Chrome
  ↓
タブ一覧
  ↓
複数タブを選択
  ↓
「タブを共有」
  ↓
共有先から本アプリを選択
  ↓
URLを抽出
  ↓
Todoアプリの Bookmark へ一括登録（status: inbox）
  ↓
「3件保存しました」
```

---

## 2. 開発方針

最初は機能を最小限にする。

MVPでは以下だけ実装する。

- Androidの共有先として表示される
- Chromeから共有されたテキストを受け取る
- テキスト内からURLをすべて抽出する
- Cognitoでログインし、IDトークンを取得する
- Bookmark一括登録API（`/bookmarks/batch`）へ送信する
- 保存結果をユーザーへ表示する
- 保存状態は `inbox` 固定
- 通信失敗時はWorkManagerへ登録し、オフライン復旧後に再送する

登録前のタグ・メモ入力画面、状態選択、履歴などは初期バージョンでは実装しない。タグ・メモは登録後にWeb版で付与する運用とする。

---

## 3. ユースケース

### UC-01 複数Chromeタブを保存する

ユーザーがChromeで複数タブを選択する。

```text
Chrome
→ タブ一覧
→ タブを選択
→ 複数選択
→ タブを共有
→ 本アプリを選択
```

Chromeから例えば以下のような共有データを受け取る。

```text
Google
https://google.com

GitHub
https://github.com

Example
https://example.com/article
```

本アプリはURLのみ抽出する。

```text
https://google.com
https://github.com
https://example.com/article
```

未ログインであればCognitoログイン画面を経由したのち、ログイン済みであれば直ちに、Todoアプリへ3件をstatus: `inbox`として登録する。

完了後、

```text
3件保存しました
```

と表示する。

---

## 4. 画面仕様

### 4.1 設定画面

ランチャーアイコンから通常起動した場合は常にこの画面を表示する（共有Intent経由で起動した場合は表示せず、直接4.2の保存処理画面へ進む。未ログイン時の扱いは4.3参照）。

表示項目（ログイン済み）：

```text
Todo共有リンク保存

ログイン状態: user@example.com でログイン中

[ ログアウト ]

保存先: inbox（固定）
```

表示項目（未ログイン）：

```text
Todo共有リンク保存

未ログインです

[ ログイン ]
```

- ログイン・ログアウトの実処理は9節参照。
- API Tokenの手入力は行わない（旧仕様のRaindrop Test Token方式を廃止）。

### 4.2 保存処理画面

共有からアプリを起動した場合に表示する。

保存前にURL一覧を確認・除外する画面や、タグ・メモを入力する画面は設けない（共有した瞬間に自動で保存を開始する）。

「N件のリンクを検出しました」のNは、重複削除後（実際にAPIへ送信する件数）を表示する。

20件を超え複数バッチに分割して送信する場合は、進捗を件数で逐次更新する。

```text
Todo共有リンク保存

3件のリンクを検出しました。

保存中...
```

大量件数の場合：

```text
Todo共有リンク保存

250件のリンクを検出しました。

保存中... (120/250件処理中)
```

保存完了後：

```text
✓ 3件保存しました

[ 閉じる ]
```

失敗時：

```text
保存に失敗しました

3件中
成功: 2件
失敗: 1件

[ 再試行 ]
[ 閉じる ]
```

「再試行」は失敗したバッチのみを再送信する。バックエンド側でURLを正規化して重複判定するため、成功済みURLが再送されても新しいBookmarkは作られず（既存Bookmarkの更新のみ）安全である。

### 4.3 未ログイン時の共有

共有Intent起動時に未ログインの場合、保存処理画面の前にCognitoログイン画面（9節参照）を挟む。ログイン完了後、自動的に保存処理を継続する。ログインをキャンセルした場合は「ログインしてください」と表示してアプリを終了する。

---

## 5. Android共有Intent

Androidの共有先として本アプリを登録する。

最低限以下を受け取る。

```text
ACTION_SEND
ACTION_SEND_MULTIPLE
```

MIME Type：

```text
text/plain
```

共有データは主に、

```kotlin
Intent.EXTRA_TEXT
```

から取得する。`ACTION_SEND_MULTIPLE`の場合は`EXTRA_TEXT`に加えて`ClipData`も確認する。

Chromeの仕様によって複数データとして渡されない可能性があるため、受け取ったすべてのテキストからURLを抽出する。

---

## 6. URL抽出

共有されたテキスト内からHTTP / HTTPS URLを抽出する。

対象：

```text
http://...
https://...
```

例：

入力：

```text
Chrome からの 3 件のリンク

Google
https://google.com

GitHub
https://github.com/test

Example
https://example.com/article?id=1
```

抽出結果：

```json
[
  "https://google.com",
  "https://github.com/test",
  "https://example.com/article?id=1"
]
```

### URL境界の判定

URL直後に改行がなく、全角記号や日本語の句読点・閉じ括弧が続く場合がある（例：「https://example.com）です」「【https://a.com】を見て」）。

抽出後、URL末尾から以下のような区切り文字を、末尾に残らなくなるまで除去する。

```text
） 」 』 】 、 。 「 『 【 , .
```

※ Chromeが実際に共有するテキストの正確な形式（改行位置・タイトル行の有無など）は未検証。実装時に実機でIntent.EXTRA_TEXTの内容を確認し、必要であれば正規表現・境界処理を調整すること。

### 重複URL

同じURLが複数存在する場合、API送信前に重複を削除する。

判定基準は前後の空白を除去（trim）した文字列の完全一致とする。例えば `http://a.com` と `https://a.com` 、`https://a.com` と `https://a.com/` は別URLとして扱う（Android側では正規化はしない）。

より厳密なURL正規化（スキーム・ホスト小文字化、フラグメント除去、既定ポート除去など）はバックエンド側の責務とする。同一記事の再登録は、バックエンドが正規化済みURLをもとに既存Bookmarkを更新する形で吸収する。

---

## 7. Todoアプリ Bookmark API

自作TodoアプリのBookmark一括登録APIを使用する。

### Endpoint

```text
POST /bookmarks/batch
```

### Authorization

```http
Authorization: Bearer {Cognito ID Token}
```

未ログイン、またはトークン失効かつリフレッシュ失敗の場合は9節のログイン画面へ遷移する。

### Request

```json
{
  "items": [
    { "url": "https://google.com" },
    { "url": "https://github.com" },
    { "url": "https://example.com/article" }
  ],
  "status": "inbox"
}
```

タグ・メモは付与しない（MVPではWeb版で後から追加する運用）。タイトル・description・OG画像はバックエンドの非同期メタデータ取得処理に任せる。

### レスポンスと部分失敗の判定

```json
{
  "results": [
    { "url": "https://google.com", "status": "created", "id": "..." },
    { "url": "https://github.com", "status": "existing", "id": "..." },
    { "url": "https://invalid", "status": "invalid" }
  ]
}
```

`results[]`の`status`が`created`または`existing`であれば成功、`invalid`であれば失敗として集計する。どのURLが失敗したかは4.2の失敗表示には使わず、件数のみ表示する。

---

## 8. 保存先・初期状態

MVPではすべて、

```text
inbox
```

へ保存する。

ユーザーに状態を選ばせる機能はAndroid側では実装しない（Web版で後から`inbox`→`reading`→`read`→`archive`と変更する）。

---

## 9. 認証・トークン管理

Todoアプリが使用するAWS Cognito User Poolに対し、Android専用のUser Pool Client（クライアントシークレットなし）を用いてログインする。固定APIキーやAWSアクセスキーはアプリ内に一切持たせない。

### ログインフロー

Cognito Hosted UIを`androidx.browser`（Custom Tabs）で起動し、ログイン完了後はカスタムスキームのredirect URI（例: `todobookmark://callback`）でアプリへ戻る。

```text
MainActivity
 └─ 未ログイン時
     └─ Custom TabsでCognito Hosted UIを起動
         └─ ログイン成功 → redirect URIでアプリに戻る
             └─ ID Token / Refresh Tokenを取得
                 └─ EncryptedSharedPreferencesに保存
```

### トークンの利用・更新

- API呼び出し時はOkHttp Interceptorで`Authorization: Bearer <ID Token>`を付与する。
- ID Tokenが失効している場合はRefresh Tokenで自動的に更新する。更新にも失敗した場合は再ログインを促す。
- SDKはCognito対応のもの（例: AWS Amplify Auth）を利用し、トークンの保存・更新ロジックを自前実装しない。

---

## 10. オフライン対応

通信失敗時（オフライン、タイムアウトなど）は、送信予定だったURLリストをWorkManagerへ登録し、ネットワーク復旧後に自動で再送する。

再送によってBookmarkが重複しないよう、重複判定はバックエンド側のURL正規化に委ねる（6節参照）。そのため同じ共有操作が再送されても安全である。

---

## 11. エラー処理

### URLが存在しない

```text
保存できるURLが見つかりませんでした
```

### 未ログイン

Cognitoログイン画面へ遷移する（4.3節参照）。

### 通信エラー

```text
サーバーへの接続に失敗しました
```

WorkManagerへ再送登録した上で、画面には再試行ボタンを表示する。

### 認証エラー（トークン失効・リフレッシュ失敗）

HTTP `401 / 403`

```text
ログインの有効期限が切れました
```

再ログイン画面への導線を表示する。

### 一部登録失敗

成功件数と失敗件数を表示する。

```text
18 / 20件保存しました

2件の保存に失敗しました
```

---

## 12. API上限

1リクエストに大量のURLを送らない。

バックエンドの`/bookmarks/batch`は1リクエスト最大20件のため、Android側もそれに合わせて分割する。

例：

```text
250 URL

↓️

1回目〜12回目 各20件
13回目 10件
```

---

## 13. 使用技術

### Android

```text
Kotlin
```

- applicationId: `com.koukishiba.todobookmark`（仮称。正式名称は未定のため実装着手時に確定する）
- minSdk: 26 (Android 8.0)

### UI

```text
Jetpack Compose
```

### 認証

```text
AWS Amplify Auth（Cognitoプラグイン）
```

Cognito Hosted UIログイン（`Amplify.Auth.signInWithWebUI`等）とトークンの保存・更新を担う。Custom Tabs起動には`androidx.browser`を使用する。

### HTTP

```text
Retrofit
```

Authorizationヘッダーの付与・トークンリフレッシュ連携はOkHttp Interceptorで実装する。

### JSON

```text
kotlinx.serialization
```

またはRetrofit標準Converterを使用する。

### オフライン再送

```text
WorkManager
```

### テスト方針

`ShareIntentParser`（URL抽出ロジック）のみ単体テストを書く。境界文字のトリミングや重複削除など、正規表現ベースで壊れやすい部分の回帰検知を優先する。

UI・API通信・認証部分はMVPでは手動確認のみとし、自動テストは書かない。

---

## 14. アーキテクチャ

大規模な設計は不要。

以下程度にする。

```text
MainActivity
    │
    ├─ ShareIntentParser
    │       ↓
    │    URL抽出
    │
    ├─ AuthManager
    │       ↓
    │    Cognito Hosted UIログイン / TokenStore
    │
    ├─ BookmarkRepository
    │       ↓
    │    BookmarkApi（20件バッチ分割）
    │
    └─ RetrySaveWorker（WorkManager）
```

### MainActivity

- Intent受信（起動方法の判定）
  - ランチャーアイコンからの起動 → 4.1 設定画面を表示
  - 共有Intent（ACTION_SEND / ACTION_SEND_MULTIPLE）からの起動 → 未ログイン時は4.3のログイン誘導を挟み、4.2 保存処理画面へ進む
- UI表示
- 保存処理開始

### ShareIntentParser

責務：

```text
共有Intent
↓
共有テキスト
↓
URL抽出・重複除去
↓
List<String>
```

### AuthManager

責務：

```text
Cognito Hosted UIログイン起動
ID Token / Refresh Tokenの取得・保存・更新
ログイン状態の判定
```

### BookmarkRepository

責務：

```text
URL一覧
↓
20件ごとのバッチ分割
↓
APIリクエスト生成（Authorizationヘッダー付き）
↓
Bookmark API
```

### RetrySaveWorker

責務：

```text
送信失敗した未送信URLの保持
ネットワーク復旧後の自動再送
```

---

## 15. データフロー

```text
Chrome

   ↓ ACTION_SEND

Android Share Sheet

   ↓

MainActivity

   ↓（未ログイン時のみ）

AuthManager（Cognito Hosted UIログイン）

   ↓

ShareIntentParser

   ↓

List<String>

[
 https://a.com,
 https://b.com,
 https://c.com
]

   ↓

BookmarkRepository（20件バッチ分割）

   ↓

Bookmark API（/bookmarks/batch）

   ↓（失敗時）

RetrySaveWorker（WorkManager）

   ↓

保存結果

   ↓

MainActivity

   ↓

「3件保存しました」
```

---

## 16. Manifest

共有先として表示するため、`AndroidManifest.xml`へIntent Filterを追加する。

概念的には以下。

```xml
<intent-filter>

    <action android:name="android.intent.action.SEND" />

    <category android:name="android.intent.category.DEFAULT" />

    <data android:mimeType="text/plain" />

</intent-filter>
```

`ACTION_SEND_MULTIPLE`にも対応する。

加えて、Cognito Hosted UIからのredirectを受け取るため、カスタムスキーム（例: `todobookmark://callback`）のIntent Filterを追加する。

```xml
<intent-filter>

    <action android:name="android.intent.action.VIEW" />

    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />

    <data android:scheme="todobookmark" android:host="callback" />

</intent-filter>
```

---

## 17. MVP完成条件

以下がすべて動けばVersion 1完成とする。

- [ ] アプリをAndroid端末へインストールできる
- [ ] Chromeの共有先にアプリが表示される
- [ ] Chromeで複数タブを選択できる
- [ ] 「タブを共有」から本アプリを選択できる
- [ ] 複数URLを認識できる
- [ ] URLの重複を削除できる（trim完全一致）
- [ ] Cognitoでログインできる
- [ ] 未ログイン時の共有でログイン画面へ誘導される
- [ ] 複数URLをTodoアプリのBookmarkへ登録できる（`/bookmarks/batch`）
- [ ] `inbox`へ保存できる
- [ ] 成功件数を表示できる
- [ ] 通信エラーを表示し、WorkManagerで再送できる
- [ ] トークン失効時に再ログイン導線が表示される
- [ ] ShareIntentParserの単体テストが通る

---

## 18. 将来的な拡張

Version 2以降で検討する。

### 登録前のタグ・メモ入力

```text
共有
↓
URL検出

タグ: [ ________ ]
メモ: [ ________ ]
状態: ○ inbox ○ reading

[ 保存 ]
```

### 状態選択

`inbox`固定をやめ、共有時に`reading`等を選べるようにする。

### 重複表示

すでにBookmarkとして存在するURLの場合、

```text
既存: 4件
新規: 16件
```

などを表示する。

### 自動保存モード（現状のMVPがこれに相当）

アプリ画面をほぼ表示せず、

```text
Chrome
→ 共有
→ Todo共有リンク保存
→ Toast「12件保存しました」
```

だけで終了する形を維持・洗練する。

---

## 19. 最終的に目指すUX

ユーザー操作を極力少なくする。

```text
Chrome
↓
タブ一覧
↓
20タブ選択
↓
共有
↓
Todo共有リンク保存
↓
「20件保存しました」
```

**これだけでChromeの大量タブをTodoアプリの「あとで読む」へ退避できるアプリを完成形とする。**
