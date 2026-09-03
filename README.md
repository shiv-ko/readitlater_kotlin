# Todo共有リンク保存

Android版Chromeで共有した複数URLを、自作Todoアプリの「あとで読む」機能（Bookmark）へ一括登録するAndroidアプリです。

## 現在の状態

MVPの実装が完了し、実機（Pixel 6a）でCognito Hosted UIログインからBookmark一括登録までの一連の流れを確認済みです。

- Androidの共有先として表示され、共有テキストからURLを抽出する
- Cognito Hosted UIでログインし、セッションを復元する
- `/bookmarks/batch` へ最大20件ずつ登録し、`created`/`existing`/`invalid` を集計して表示する
- 通信エラー時はWorkManagerへ登録し、オフライン復旧後に自動で再送する

## 使い方

- [実機（自分のAndroidスマホ）へインストールする](docs/real-device-install.md)
- [エミュレーターで起動する](docs/emulator-guide.md)
- [アプリ仕様](app.md)
- [バックエンド引き継ぎ](docs/backend-requirements.md)

ドキュメント全体の案内は [docs/README.md](docs/README.md) を参照してください。

## 使用技術

Kotlin / Jetpack Compose / AWS Amplify Auth（Cognito Hosted UI）/ Retrofit / kotlinx.serialization / WorkManager

バックエンドは別リポジトリ（AWS CDK、API Gateway、Lambda、DynamoDB、Cognito）で管理しています。
