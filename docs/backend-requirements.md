# Androidアプリ実装用 バックエンド引き継ぎ

最終更新日: 2026-09-03  
対象: Kotlinアプリ `todobookmark` の実装担当Agent

## 最初に確認すること

Bookmark用バックエンドは、このリポジトリへ実装済みです。Android側では、既存のURL抽出処理を残したまま、Cognito Hosted UIによるログイン、一括登録APIへの送信、WorkManagerによる再送を実装してください。

ただし、AWSへのデプロイと実環境での疎通確認はまだ行っていません。`AndroidUserPoolClientId`と`HostedUiDomain`は、バックエンドをデプロイした後にCloudFormationの出力から取得します。Android側で値を直書きせず、ビルド設定や環境別設定へ切り出してください。

初回は「実装状況」「認証」「一括登録API」「Android側の実装範囲」まで通読してください。それ以降は、エラー処理とAPIリファレンスを必要に応じて参照してください。

## 実装状況

バックエンド側では、次の機能まで実装と自動テストが完了しています。

- DynamoDBの`Bookmarks`テーブル
- Cognito認証付きBookmark API
- Android専用Cognito User Pool Client
- Hosted UIのAuthorization code grant
- URL正規化とSHA-256による重複排除
- 最大20件の一括登録と、URL単位の部分失敗
- SQSとLambdaによるWebページ情報の非同期取得
- LINEメッセージからのBookmark登録
- Web版の一覧、検索、タグ、状態、お気に入り、編集、削除

バックエンド全体のテストは63件、フロントエンドのテストは47件が成功しています。CDK Synthとフロントエンドの本番ビルドも成功しています。

### デプロイ後に確定する項目

| 項目 | 値・取得方法 | 状態 |
|---|---|---|
| Region | `ap-northeast-3` | 確定 |
| User Pool ID | `ap-northeast-3_H9F0jf3UU` | 既存Web設定から確認済み |
| API Base URL | `https://gctpao66n5.execute-api.ap-northeast-3.amazonaws.com/prod` | 既存API。Bookmarkルートは次回デプロイで追加 |
| Android Client ID | CloudFormation出力`AndroidUserPoolClientId` | デプロイ後に確定 |
| Hosted UI Domain | CloudFormation出力`HostedUiDomain` | デプロイ後に確定 |
| Callback URL | `todobookmark://callback` | CDKへ登録済み |
| Sign-out URL | `todobookmark://signout` | CDKへ登録済み |

バックエンドをデプロイすると、`ApiUrl`、`UserPoolId`、`AndroidUserPoolClientId`、`HostedUiDomain`、`Region`、`AndroidCallbackUrl`が出力されます。Android側の結合テストは、この出力値を受け取ってから実施してください。

## Cognito Hosted UIでログインする

Android用クライアントは、次の設定で作成されます。

| 設定 | 値 |
|---|---|
| Client name | `todo-app-android` |
| Client secret | なし |
| OAuth flow | Authorization code grant |
| OAuth scopes | `openid`, `email`, `profile` |
| ID Token有効期限 | 60分 |
| Access Token有効期限 | 60分 |
| Refresh Token有効期限 | 30日 |
| Token revocation | 有効 |

AndroidではAuthorization code grantとPKCEを使ってください。可能であればAmplify AuthなどのCognito対応SDKに、コード交換、トークン保存、更新を任せます。クライアントシークレット、固定APIキー、AWSアクセスキーはアプリへ保存しません。

Callback URLを受け取れるよう、AndroidManifestに`todobookmark://callback`用のIntent Filterを設定してください。サインアウト後の戻り先には`todobookmark://signout`を使います。

### APIへ送る認証ヘッダー

現在のAPI Gatewayは、Cognito User Pools Authorizerで`Authorization`ヘッダーを検証します。Web版と同じく、ID TokenのJWT文字列をそのまま設定してください。

```http
Authorization: eyJraWQiOiJ...
```

`Bearer `は付けません。401または403が返った場合は、SDKでセッション更新を1回試し、成功したら元のリクエストを再送してください。更新できない場合は保存済みトークンを破棄し、再ログイン画面へ移動します。

## Bookmarkを最大20件ずつ登録する

### エンドポイント

```http
POST {API_BASE_URL}/bookmarks/batch
Authorization: {Cognito ID Token}
Content-Type: application/json
```

### リクエスト

```json
{
  "items": [
    { "url": "https://example.com/article-1" },
    { "url": "https://example.com/article-2" }
  ],
  "tags": ["技術", "週末"],
  "memo": "週末に読む",
  "status": "inbox",
  "source": "android"
}
```

`items`だけが必須です。`tags`、`memo`、`status`、`source`は省略できます。`status`の既定値は`inbox`、`source`の既定値は`android`です。Androidからは、入力値を明確にするため`status`と`source`も送る実装を推奨します。

| フィールド | 制約 |
|---|---|
| `items` | 1〜20件 |
| `items[].url` | `http`または`https`、最大2,048文字 |
| `tags` | 最大10件。各タグはtrim後1〜30文字 |
| `memo` | 最大2,000文字 |
| `status` | `inbox`, `reading`, `read`, `archive` |
| `source` | Androidからは`android`を指定 |

タグは前後の空白を除去し、大文字・小文字を区別せず重複をまとめます。タイトル、description、サイト名、OG画像URLは送信しません。保存後にバックエンドが非同期で取得します。

### 成功レスポンスには入力順の結果が入る

```json
{
  "results": [
    {
      "url": "https://example.com/article-1",
      "status": "created",
      "id": "8b2d..."
    },
    {
      "url": "https://example.com/article-2",
      "status": "existing",
      "id": "1c3a..."
    },
    {
      "url": "invalid-url",
      "status": "invalid"
    }
  ]
}
```

`results`は、`items`と同じ順序・同じ件数です。

| status | Android側の扱い |
|---|---|
| `created` | 登録成功 |
| `existing` | 登録成功。既存Bookmarkが更新された |
| `invalid` | そのURLだけ登録失敗 |

レスポンス全体が200でも、`invalid`が含まれる場合があります。`created + existing`を成功件数、`invalid`を失敗件数として表示してください。

## 再送しても同じBookmarkは増えない

明示的な`Idempotency-Key`は不要です。バックエンドは正規化したURLのSHA-256をBookmark IDに使うため、WorkManagerが同じURLを再送しても新しいレコードは作られません。

正規化では、次の処理を行います。

- スキームとホスト名を小文字化
- URLフラグメントを削除
- `http:80`と`https:443`の既定ポートを削除
- クエリ文字列を保持
- ユーザー名またはパスワードを含むURLを拒否

`https://example.com`と`https://example.com/`は同じBookmarkになります。一方、`http`と`https`、クエリ文字列が異なるURLは別のBookmarkです。

既存Bookmarkへ同じURLを送ると、`updatedAt`を更新します。送信したタグは既存タグへ追加され、`memo`と`status`はリクエストに含めた場合だけ上書きされます。

サーバー内部で保存後のSQS送信に失敗した場合などは、登録済みの項目があってもバッチ全体が5xxになる可能性があります。この場合も同じリクエストを再送して構いません。

## WorkManagerでは通信エラーと5xxだけを自動再送する

URLが20件を超える場合は、受信順を保ったまま20件ずつに分割してください。大量共有時は、APIを同時に多数呼ばず、各バッチを順番に送ります。

推奨する分岐は次のとおりです。

| 状況 | Android側の処理 |
|---|---|
| 200 | `results`を集計して完了。`invalid`だけ失敗件数へ加算 |
| 400 | リクエスト形式の誤り。自動再送せず、入力を見直せる表示にする |
| 401 / 403 | トークン更新を1回試す。失敗したら再ログイン |
| 5xx | WorkManagerへ登録して指数バックオフで再送 |
| タイムアウト・オフライン | WorkManagerへ登録してネットワーク復旧後に再送 |

Bookmark API Lambdaのタイムアウトは10秒です。AndroidのHTTP呼び出しは15秒程度を目安にし、実環境の計測後に調整してください。この15秒はAndroid側の推奨値であり、バックエンドの保証値ではありません。

API Gatewayにアプリ独自のレート制限やUsage Planは設定していません。250件を13バッチに分ける場合も、まずは直列送信してください。短時間の連続登録は未負荷試験のため、結合テストで所要時間と失敗率を確認します。

## メタデータの完了をAndroid側で待つ必要はない

新規Bookmarkは`metadataStatus: "pending"`で保存されます。その後、SQSから起動するLambdaが`title`、`description`、`siteName`、`imageUrl`を取得します。取得できれば`ready`、失敗すれば`failed`になります。取得失敗でもBookmarkは削除されません。

メタデータ取得処理には、次の制限があります。

- 1リクエスト5秒
- 最大5回のリダイレクト
- 最大1MBのHTML
- HTML以外のレスポンスを拒否
- localhost、プライベートIP、リンクローカルIPなどを拒否
- リダイレクト先でもURLとIPを再検証

Androidの共有完了画面では、メタデータ取得を待たず、バッチ登録APIのレスポンスだけで成功件数を表示してください。

## Android側の実装範囲

既存の`ACTION_SEND`、`ACTION_SEND_MULTIPLE`、`EXTRA_TEXT`、`ClipData`からのURL抽出処理を入力元として、次を実装してください。

1. Hosted UIを開き、Authorization code grantとPKCEでログインする。
2. Callback URLを受け取り、Cognitoセッションを安全に保存する。
3. 共有されたURLを確認画面に表示し、タグ、メモ、初期状態を入力できるようにする。
4. URLを20件ずつ`POST /bookmarks/batch`へ送る。
5. `created`、`existing`、`invalid`を集計して結果を表示する。
6. オフライン、タイムアウト、5xxでは、同じリクエスト本文をWorkManagerへ保存する。
7. WorkManagerにネットワーク接続制約と指数バックオフを設定する。
8. 401/403ではセッション更新または再ログインへ誘導する。

トークンや共有URLを通常ログへ出力しないでください。WorkManagerへ保存するリクエストにも認証トークンを含めず、実行時に最新セッションから取得します。

### Android側の完了条件

- 未ログイン時にHosted UIからログインできる
- アプリ終了後も有効なセッションを復元できる
- 単一共有と複数共有を登録できる
- 21件以上を20件単位で送信できる
- `created`、`existing`、`invalid`を正しく集計できる
- オフライン時にキューへ入り、復旧後に自動登録される
- 同じWorkを再実行してもBookmarkが重複しない
- 401/403で無限再送せず、再ログインへ進む
- 400では自動再送しない
- 5xxと通信失敗では指数バックオフで再送する
- ID Tokenや共有URLがログへ漏れない

## APIリファレンス

Android MVPで必須なのは`POST /bookmarks/batch`だけです。将来Android側に一覧・編集を追加する場合は、次のAPIも利用できます。

| Method | Path | 成功時 | 用途 |
|---|---|---|---|
| `GET` | `/bookmarks` | 200 | 全件を`updatedAt`降順で取得 |
| `POST` | `/bookmarks/batch` | 200 | 1〜20件を登録 |
| `PATCH` | `/bookmarks/{id}` | 200 | URL、タイトル、状態、タグ、メモ、お気に入りを更新 |
| `DELETE` | `/bookmarks/{id}` | 204 | Bookmarkを削除 |

`PATCH`で更新できる値は、`url`、`title`、`status`、`tags`、`memo`、`favorite`です。URLを変更するとIDも変わり、メタデータを取り直します。変更後のURLがすでに存在する場合は409を返します。

Bookmarkのレスポンス形式は次のとおりです。

```json
{
  "id": "正規化URLのSHA-256",
  "url": "https://example.com/article",
  "normalizedUrl": "https://example.com/article",
  "title": "記事タイトル",
  "description": "記事の説明",
  "siteName": "Example",
  "imageUrl": "https://example.com/og.png",
  "status": "inbox",
  "tags": ["技術"],
  "memo": "週末に読む",
  "favorite": false,
  "source": "android",
  "metadataStatus": "ready",
  "createdAt": "2026-09-03T00:00:00.000Z",
  "updatedAt": "2026-09-03T00:00:01.000Z"
}
```

`title`、`description`、`siteName`、`imageUrl`、`memo`は存在しない場合があります。

## 結合前にバックエンド側で行うこと

- [ ] `npm run deploy`でAWSへ反映する
- [ ] CloudFormation出力をAndroid担当へ渡す
- [ ] Hosted UIでログインできることを実機で確認する
- [ ] ID Tokenを使って`POST /bookmarks/batch`が200になることを確認する
- [ ] トークンなし、期限切れトークンで401または403になることを確認する
- [ ] 登録後にWeb版へBookmarkが表示されることを確認する
- [ ] メタデータが`pending`から`ready`または`failed`へ変わることを確認する
- [ ] 250件相当の連続送信を結合テストする

未確定のAWS出力値や実環境の挙動について疑問がある場合は、推測でAndroidコードへ固定せず、バックエンド担当へ確認してください。
s