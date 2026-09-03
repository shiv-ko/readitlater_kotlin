# 実機（自分のAndroidスマホ）へインストールする

この文書は、`Todo共有リンク保存`（`TodoBookmark`）をエミュレーターではなく、自分が普段使っているAndroidスマホへインストールするための手順です。エミュレーターでの起動は [`emulator-guide.md`](emulator-guide.md) を参照してください。

現時点ではバックエンドが未デプロイのため、インストールはできてもCognitoログインは動きません。「ログイン機能についての制限」の節を必ず先に読んでください。

## 必要なもの

| 項目 | 内容 |
| --- | --- |
| Androidスマホ | Android 8.0（API 26）以上。共有シートからURLを渡す確認に使う実機 |
| USBケーブル | スマホとMacをデータ通信できるケーブルで接続する（充電専用ケーブルは不可） |
| JDK | Gradle/Kotlinのビルドに必要。未導入なら `brew install openjdk@17` |
| Android SDK | `~/Library/Android/sdk` を想定。未導入ならAndroid Studioを一度インストールすると自動で入る |
| ADB | Android SDKに含まれる（`~/Library/Android/sdk/platform-tools/adb`）。Homebrewでも導入可（`brew install android-platform-tools`） |

## 1. スマホ側で開発者向けオプションとUSBデバッグを有効にする

1. 「設定」→「デバイス情報」→「ビルド番号」を7回連続でタップする（「デベロッパーになりました」と表示される）
2. 「設定」→「システム」→「開発者向けオプション」を開く
3. 「USBデバッグ」をオンにする

メーカーやAndroidバージョンによりメニュー階層が多少異なります。見つからない場合は「開発者向けオプション」で機種名を検索してください。

## 2. Macに接続し、信頼ダイアログを許可する

USBケーブルでスマホとMacをつなぐと、スマホ側に「USBデバッグを許可しますか」というダイアログが出ます。**「このパソコンを常に許可する」にチェックを入れてから許可**してください。毎回聞かれると、以降の手順が止まります。

接続を確認します。

```bash
/Users/shiv/Library/Android/sdk/platform-tools/adb devices -l
```

スマホの機種名付きで `device` と表示されれば認識成功です。`unauthorized` と表示される場合は、スマホ側の許可ダイアログを見落としています。ロックを解除してダイアログを確認してください。

## 3. `local.properties` を用意する

プロジェクト直下（`/Users/shiv/P/readitlater_kotlin`）に `local.properties` がまだなければ作成します。すでにある場合は `sdk.dir` の行だけ確認します。

```properties
sdk.dir=/Users/shiv/Library/Android/sdk
```

バックエンド関連の値（`TODOBOOKMARK_USER_POOL_CLIENT_ID` など）は「ログイン機能についての制限」の節を参照してください。今はデフォルト値（`CHANGEME` を含む）のままで構いません。ビルドは通ります。

`local.properties` はMac固有のパスやCloudFormation出力値を含むため、`.gitignore` でGit管理から除外されています。書き換えてもコミットには含まれません。

## 4. ビルドしてインストールする

このMac上でGradleを動かすには、Homebrewで入れたJDK 17を使います（未導入なら `brew install openjdk@17`）。

```bash
cd /Users/shiv/P/readitlater_kotlin

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/Users/shiv/Library/Android/sdk

./gradlew installDebug
```

`installDebug` は、コンパイル・デバッグAPKの生成・**USB接続中の実機へのインストール**をまとめて行います。エミュレーターと実機の両方が同時に接続されている場合は、対象を絞る必要があります。

```bash
# 接続中の端末一覧からシリアル番号を確認する
/Users/shiv/Library/Android/sdk/platform-tools/adb devices -l

# シリアル番号を指定してインストール先を絞る（例）
ANDROID_SERIAL=XXXXXXXXXXXX ./gradlew installDebug
```

成功すると `BUILD SUCCESSFUL` と表示され、スマホのアプリ一覧に「Todo共有リンク保存」が追加されます。

### Android Studioから入れる場合

コマンドラインの代わりに、Android Studioでこのプロジェクトを開き、実機を接続した状態で画面上部の端末選択に自分のスマホを選び、緑の再生ボタン（Run）を押しても同じ結果になります。GUIで進捗やログを見たい場合はこちらが手軽です。

## 5. 共有ターゲットとして使う

1. スマホでChromeを開き、任意のページを表示する
2. タブ一覧から複数タブを選択する（または単一ページの共有メニューを開く）
3. 「共有」→ 共有先の一覧に「Todo共有リンク保存」が表示されるので選択する

現時点ではログインができないため、共有した時点でログイン画面（Custom Tabs）に遷移して止まります。これは想定どおりの挙動です。

## ログイン機能についての制限

このアプリはAWS Cognito Hosted UIでログインします。ログインに必要な `AndroidUserPoolClientId` と `HostedUiDomain` は、バックエンドをデプロイした後にCloudFormationの出力から確定します（詳細は [`backend-requirements.md`](backend-requirements.md) 参照）。現状はプレースホルダー値（`CHANGEME`）のままなので、アプリはインストールできても次の操作はまだ成功しません。

- ログインボタンを押してもHosted UIが正しく開けない、または開けても失敗する
- 共有した瞬間に始まる自動ログイン誘導も同様に失敗する

バックエンドがデプロイされ、実際の値を受け取ったら、`local.properties` を次のように更新してからアプリを入れ直してください（値は担当から共有される実際のものに置き換える）。

```properties
TODOBOOKMARK_USER_POOL_CLIENT_ID=（デプロイ後にもらう実際の値）
TODOBOOKMARK_HOSTED_UI_DOMAIN=（デプロイ後にもらう実際の値）
```

書き換えたら、`./gradlew installDebug` をもう一度実行してください。値の変更は自動的にビルドへ反映され、再インストールされます。

## アンインストールする

```bash
/Users/shiv/Library/Android/sdk/platform-tools/adb uninstall com.koukishiba.todobookmark
```

スマホ側のアプリ一覧から通常どおりアンインストールしても構いません。

## うまくいかないときは

### `unauthorized` のまま変わらない

スマホの画面ロックを解除し、USBデバッグ許可ダイアログが裏に隠れていないか確認します。それでも出ない場合は、USBケーブルを一度抜き差しするか、`adb kill-server && adb start-server` を実行してから再確認します。

### `SDK location not found`

`local.properties` に `sdk.dir` の行があるか、パスが実際のSDKの場所と一致しているかを確認します（「3. `local.properties` を用意する」参照）。

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE` などインストールエラー

同じアプリIDで署名の異なるビルドが以前入っている場合に起きます。一度アンインストールしてから、もう一度 `installDebug` を実行してください。

### 複数端末が同時に見えて `installDebug` が失敗する

「4. ビルドしてインストールする」の `ANDROID_SERIAL` 指定を使い、対象の実機だけに絞ってください。
