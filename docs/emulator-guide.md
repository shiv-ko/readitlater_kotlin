# エミュレーターで起動する

この文書は、このMacで `Todo Bookmark` を Android エミュレーターへインストールし、画面を開くための手順です。「最短の起動手順」までは最初に通読し、ファイル一覧とトラブル対処は必要なときに参照してください。

パスは2026年8月31日に確認した値です。Android SDKやJDKを移動した場合は、現在の配置に読み替える必要があります。

## 追加インストールなしで起動できる

このMacには、アプリの起動に必要な次の部品がすでにあります。

| 部品 | 確認した内容 |
| --- | --- |
| CPU | Apple Silicon `arm64` |
| Java | Temurin OpenJDK 21.0.11 |
| Android SDK Platform | Android 35 |
| Android Build Tools | 35.0.0、36.0.0 |
| Android Emulator | 34.2.16 |
| システムイメージ | Android 35 / Google Play / `arm64-v8a` |
| 仮想端末 | `Pixel_Fold_API_35` |
| ADB | 36.0.0 |

Android Studioは必須ではありません。この環境では、コマンドラインからビルド、エミュレーター起動、APKインストールまで確認しています。SDKや仮想端末をGUIで管理したい場合は、Android Studioを追加すると操作しやすくなります。

## 最短の起動手順は4ステップ

ターミナルを開き、最初にこのプロジェクトへ移動します。

```bash
cd /Users/shiv/P/readitlater_kotlin
```

### 1. エミュレーターを安定する設定で起動する

```bash
/Users/shiv/Library/Android/sdk/emulator/emulator \
  @Pixel_Fold_API_35 \
  -cores 4 \
  -memory 4096 \
  -no-snapshot-load
```

このコマンドを実行したターミナルは、エミュレーターの実行中にログを表示し続けます。閉じずに残し、次の操作は別のターミナルで行います。

`-cores 4` と `-memory 4096` は今回の起動にだけ適用する値です。AVDの `config.ini` は書き換えません。`-no-snapshot-load` は保存済みの途中状態を使わず、Androidを最初から起動します。

### 2. Androidの起動完了を確認する

```bash
/opt/homebrew/bin/adb devices -l
/opt/homebrew/bin/adb shell getprop sys.boot_completed
```

1行目の結果に `emulator-5554 device` のような端末が出て、2行目が `1` を返せば起動完了です。空欄なら、数秒待ってからもう一度確認します。

### 3. APKをビルドしてインストールする

このMacでは、VS CodeのJavaをビルドに使います。

```bash
export JAVA_HOME=/Users/shiv/.vscode/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64
export ANDROID_HOME=/Users/shiv/Library/Android/sdk

./gradlew installDebug
```

`installDebug` は、Kotlinのコンパイル、デバッグAPKの生成、接続中のエミュレーターへのインストールをまとめて行います。成功すると、最後に `BUILD SUCCESSFUL` と表示されます。

### 4. アプリの初期画面を開く

```bash
/opt/homebrew/bin/adb shell am start \
  -n com.koukishiba.todobookmark/.MainActivity
```

エミュレーターに「Todo共有リンク保存」と表示されれば完了です。

## 共有Intentをコマンドで試す

Chromeを操作しなくても、ADBから共有テキストを渡せます。

```bash
/opt/homebrew/bin/adb shell am start \
  -n com.koukishiba.todobookmark/.MainActivity \
  -a android.intent.action.SEND \
  -t text/plain \
  --es android.intent.extra.TEXT \
  'Google https://google.com Example https://example.com/article'
```

画面に「2件のリンクを検出しました」とURL一覧が表示されれば、共有Intentの受信とURL抽出が動いています。現在の実装は一覧表示までで、Bookmark APIへの保存はまだ行いません。

## 停止はエミュレーターのウィンドウを閉じる

通常はエミュレーターのウィンドウを閉じれば停止します。コマンドから止めたい場合は次を使います。

```bash
/opt/homebrew/bin/adb emu kill
```

エミュレーター内のアプリや設定はAVDのユーザーデータへ残ります。AVDフォルダを削除しない限り、停止だけでデータは消えません。

## プロジェクト内ではソースからAPKまでを使う

次のファイルとフォルダは、このGitリポジトリに属します。

| パス | 役割 | Git管理 |
| --- | --- | --- |
| `app/build.gradle.kts` | Android 35、アプリID、Compose、依存ライブラリを設定する | 対象 |
| `app/src/main/AndroidManifest.xml` | 起動Activityと共有Intent Filterを宣言する | 対象 |
| `app/src/main/java/com/koukishiba/todobookmark/` | Kotlinのアプリ本体とURL抽出処理を置く | 対象 |
| `app/src/main/res/` | アプリ名、テーマ、アイコンを置く | 対象 |
| `app/src/test/` | URL抽出の単体テストを置く | 対象 |
| `gradle/`、`gradlew` | 指定したGradleでビルドを実行する | 対象 |
| `app/build/outputs/apk/debug/app-debug.apk` | `assembleDebug` や `installDebug` が生成するAPK | 対象外 |
| `app/build/` | コンパイル結果、テスト結果、Lintレポートを置く | 対象外 |

`app/build/` は何度でも作り直せる生成物なので、`.gitignore` でGit管理から除外しています。

## Mac側ではSDK、JDK、AVDを使う

次はこのMacにだけ存在し、リポジトリへは保存しないファイルです。

| 実際のパス | 役割 |
| --- | --- |
| `/Users/shiv/Library/Android/sdk/` | Android SDK全体のルート |
| `/Users/shiv/Library/Android/sdk/emulator/emulator` | 仮想端末を動かす実行ファイル |
| `/Users/shiv/Library/Android/sdk/platforms/android-35/android.jar` | Android 35 APIをコンパイルするときの基準 |
| `/Users/shiv/Library/Android/sdk/build-tools/36.0.0/` | APK作成、DEX変換、署名などに使うツール群 |
| `/Users/shiv/Library/Android/sdk/system-images/android-35/google_apis_playstore/arm64-v8a/` | 仮想端末が起動するAndroid OSイメージ |
| `/Users/shiv/Library/Android/sdk/platform-tools/adb` | Android端末との通信に使うSDK付属ADB |
| `/opt/homebrew/bin/adb` | 今回コマンドで使ったHomebrew版ADBへのリンク |
| `/Users/shiv/.android/avd/Pixel_Fold_API_35.ini` | AVD名と実データの場所を結び付ける設定 |
| `/Users/shiv/.android/avd/Pixel_Fold_API_35.avd/config.ini` | 画面、CPU、メモリ、システムイメージを指定するAVD設定 |
| `/Users/shiv/.android/avd/Pixel_Fold_API_35.avd/userdata-qemu.img` | インストールしたアプリや端末設定を保持するユーザーデータ |
| `/Users/shiv/.vscode/extensions/redhat.java-1.55.0-darwin-arm64/jre/21.0.11-macosx-aarch64/` | Gradleを動かすために使ったJDK |
| `/Users/shiv/.gradle/` | Gradle本体と依存ライブラリのキャッシュ |

システムイメージはAndroid OSの読み取り元です。AVDフォルダは個別端末の設定と利用データを持ちます。同じシステムイメージから複数のAVDを作っても、ユーザーデータはAVDごとに分かれます。

## `System UI isn't responding` → CPUとメモリを一時指定する

今回、既存AVDをそのまま起動すると、初回コールドブート中に `System UI isn't responding` が表示されました。アプリのクラッシュではなく、仮想端末側のSystem UIが応答できていない状態です。

`Pixel_Fold_API_35.avd/config.ini` はCPU 1コア、RAM約11GBという偏った設定でした。エミュレーターを閉じ、次の指定で起動し直すと正常表示を確認できました。

```bash
/Users/shiv/Library/Android/sdk/emulator/emulator \
  @Pixel_Fold_API_35 \
  -cores 4 \
  -memory 4096 \
  -no-snapshot-load
```

同じ問題が繰り返す場合は、Android StudioのDevice ManagerでCPUとRAMを変更するか、Pixel系の通常端末を使う新しいAVDを作ります。既存AVDのデータを残したい場合は、`.avd` フォルダを直接削除しないでください。

## `no devices/emulators found` → ADBを再接続する

エミュレーターが見えているのにADBが端末を表示しない場合は、ADBサーバーを起動し直してから確認します。

```bash
/opt/homebrew/bin/adb kill-server
/opt/homebrew/bin/adb start-server
/opt/homebrew/bin/adb devices -l
```

`offline` と表示される場合はAndroidの起動途中です。しばらく待っても `device` へ変わらなければ、エミュレーターを再起動します。

## `SDK location not found` → SDKの場所をGradleへ渡す

GradleがAndroid SDKを見つけられない場合は、同じターミナルで次を設定してからビルドします。

```bash
export ANDROID_HOME=/Users/shiv/Library/Android/sdk
```

代わりに、プロジェクト直下へ次の `local.properties` を置く方法もあります。

```properties
sdk.dir=/Users/shiv/Library/Android/sdk
```

`local.properties` はMac固有の絶対パスを含むため、`.gitignore` でGit管理から除外しています。

## 用語を迷ったらここを見る

| 用語 | 意味 |
| --- | --- |
| Emulator | 仮想Android端末を実行するプログラム |
| System image | 仮想端末で動くAndroid OS本体 |
| AVD | 画面サイズやユーザーデータまで含む一台分の仮想端末設定 |
| APK | Android端末へインストールするアプリファイル |
| ADB | MacからAndroid端末へインストールやコマンド送信を行うツール |
| SDK | Androidアプリのビルドと実行に必要なAPI・ツール一式 |
| JDK | GradleとKotlinコンパイラーを動かすJava開発環境 |

この手順で解決しない場合は、実行したコマンド、表示されたエラー全文、`adb devices -l` の結果をIssueやレビューコメントへ記載してください。どの層で止まっているかを切り分けやすくなります。
