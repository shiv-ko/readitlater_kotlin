# 今回の初期設定で追加したもの

この文書は、今回の初期設定で何を追加し、どこまで動くようにしたかを説明します。最初に「追加した範囲」と「まだ追加していない範囲」を読むと、現在地を把握できます。各設定の詳しい意味は、必要になったときに後半を参照してください。

## Android アプリの起動と URL 抽出までを用意した

今回の変更で、空に近かったリポジトリを Android Studio で開けるプロジェクト構成にしました。通常起動では初期設定済みの案内を表示し、Chrome などからテキストを共有すると、含まれる HTTP / HTTPS URL を抽出して一覧表示します。

実装済みの範囲は次のとおりです。

- Kotlin と Jetpack Compose を使う Android アプリのビルド設定
- `minSdk 26`、`compileSdk/targetSdk 35` の指定
- `ACTION_SEND` と `ACTION_SEND_MULTIPLE` の受信設定
- 共有テキストと `ClipData` の読み取り
- URL 抽出、末尾記号の除去、重複削除
- URL 抽出ロジックの単体テスト
- Kotlin 学習用ドキュメント

## 認証と保存処理は次のコミットで実装する

`app.md` にある機能のうち、次はまだ実装していません。

- AWS Amplify Auth を使った Cognito ログイン
- Retrofit を使った `/bookmarks/batch` への送信
- 20 件ごとのバッチ分割と部分失敗の集計
- WorkManager を使ったオフライン再送
- Cognito Hosted UI から戻るための redirect Intent Filter

現在の共有画面には「保存処理は未実装」と表示します。URL を実際にバックエンドへ送らないため、初期設定の確認中にデータが作られることはありません。

## プロジェクトは役割ごとに分かれている

```text
.
├── app.md                         アプリ全体の仕様
├── build.gradle.kts               プロジェクト共通のプラグイン宣言
├── settings.gradle.kts            モジュールとリポジトリの設定
├── gradle/libs.versions.toml       依存ライブラリのバージョン管理
├── gradle/wrapper/                 Gradle Wrapper の設定
├── app/
│   ├── build.gradle.kts            Android アプリ固有のビルド設定
│   └── src/
│       ├── main/                   アプリ本体
│       └── test/                   JVM 上で動く単体テスト
└── docs/                           学習・変更解説
```

`app` は一つの Android アプリを表すモジュールです。機能が増えても、MVP の間はモジュールを細かく分割せず、この中でファイルの責務を分けます。

## Gradle Wrapper が同じビルド環境を作る

`./gradlew` を使うと、開発者が Gradle を別途インストールしていなくても、プロジェクトが指定した Gradle 9.2.0 を利用できます。macOS と Linux は `gradlew`、Windows は `gradlew.bat` を使います。

依存関係のバージョンは `gradle/libs.versions.toml` に集約しました。ビルドファイルへ同じバージョンを何度も書かないため、更新箇所を見つけやすくなります。

今回固定した主なバージョンは次のとおりです。

| 項目 | バージョン | 役割 |
| --- | ---: | --- |
| Gradle | 9.2.0 | ビルド全体を実行する |
| Android Gradle Plugin | 9.0.1 | Android 用のビルド機能を追加する |
| Kotlin / Compose Compiler | 2.2.10 | Kotlin と Compose のコードをコンパイルする |
| Compose BOM | 2025.08.00 | Compose 関連ライブラリの組み合わせをそろえる |

Android Gradle Plugin 9 では Kotlin のサポートが組み込まれています。そのため、従来の `org.jetbrains.kotlin.android` プラグインは追加していません。Compose のコンパイラープラグインだけを Kotlin と同じバージョンで指定しています。

## AndroidManifest が共有先への表示を決める

[`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml) に `ACTION_SEND` と `ACTION_SEND_MULTIPLE` の Intent Filter を追加しました。MIME Type は仕様どおり `text/plain` です。この宣言によって、対応するアプリの共有シートに本アプリが候補として表示されます。

Cognito Hosted UI の redirect 設定は、実際のスキームとホストが決まってから追加します。仮の値を先に入れると、認証環境との差異を見落としやすいためです。

## URL 抽出を Android の処理から分離した

共有処理を二つの責務に分けました。

- `ShareIntentReader` は Android の Intent から文字列を集める
- `ShareIntentParser` は文字列から URL を抽出する

`ShareIntentParser` は Android API に依存しません。そのため、端末やエミュレーターを起動しなくても通常の JVM 単体テストで確認できます。正規表現や境界文字の変更による不具合を早く見つけるための分離です。

現時点の抽出規則は `app.md` に合わせています。

1. `http://` または `https://` で始まる文字列を探す
2. 日本語の閉じ括弧や句読点を URL に含めない
3. URL 末尾のカンマとピリオドを取り除く
4. 完全一致する URL の重複を、最初の出現順を保って除く

URL の正規化は行いません。`https://example.com` と `https://example.com/` は別の URL として残し、同一記事かどうかの判定はバックエンドに任せます。

## 動作確認は三段階で行う

JDK 17 と Android SDK 35 を用意したうえで、プロジェクトルートから実行します。

```bash
./gradlew test
```

最初に URL 抽出の単体テストを確認します。次にデバッグ用 APK を作ります。

```bash
./gradlew assembleDebug
```

端末またはエミュレーターが接続されていれば、アプリをインストールできます。

```bash
./gradlew installDebug
```

インストール後は、通常起動で初期設定画面が出ること、Chrome からテキストを共有して URL 一覧が出ることを手動で確認します。Chrome が複数タブをどの形式で渡すかは実機確認が必要です。形式が想定と違う場合は、`ShareIntentReader` とテストケースを一緒に更新します。

このMacで使うエミュレーター、具体的な起動コマンド、SDKとAVDの保存場所は、[エミュレーターで起動する](emulator-guide.md)にまとめています。

## 次は認証を単独で接続する

次の実装では、保存 API と同時に進めず、まず Cognito のログインとログアウトだけを完成させるのが安全です。認証状態を確認できた後に Retrofit、最後に WorkManager を追加すると、問題が起きた層を切り分けやすくなります。

設定やコードについて分からない点があれば、対象ファイルと疑問のある行を Issue またはレビューコメントに残してください。この文書で不足していた説明も、その質問をもとに追記します。
