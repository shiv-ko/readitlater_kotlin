# Kotlin を最初に理解する

この文書は、Kotlin を初めて読む人が、このリポジトリのコードを追えるようになるための入門です。前半は最初に通読し、後半の「コードを読む」は実際のファイルを開きながら使ってください。

## Kotlin は短く安全に書ける JVM 言語

Kotlin は JetBrains が開発したプログラミング言語です。Android 開発では標準的な選択肢で、Java のライブラリや Android API をそのまま利用できます。

Kotlin の特徴は、同じ処理を Java より短く書きやすいことと、`null` による不具合をコンパイル時に見つけやすいことです。このアプリでは画面、URL 抽出、今後追加する API 通信を Kotlin で実装します。

## 値は `val`、変更が必要なときだけ `var`

```kotlin
val appName = "Todo共有リンク保存"
var detectedCount = 0
detectedCount = 3
```

`val` は一度代入したら変更できません。`var` は後から変更できます。変更しない値を `val` にすると、どこで値が変わるかを追わずに済みます。まず `val` を使い、必要な場合だけ `var` にするのが基本です。

型は右辺から推論されるため、`val appName: String` のように毎回書く必要はありません。

## 関数は入力と出力を小さく保つ

```kotlin
fun createMessage(count: Int): String {
    return "${count}件保存しました"
}
```

`count: Int` が入力、`String` が戻り値の型です。処理が一つの式で済む場合は、次のようにも書けます。

```kotlin
fun createMessage(count: Int): String = "${count}件保存しました"
```

文字列内の `$count` や `${count}` は、値を文字列へ埋め込む構文です。

## `null` を扱う場所は型で分かる

通常の `String` には `null` を代入できません。`null` の可能性がある値は `String?` と書きます。

```kotlin
val sharedText: String? = intent.getStringExtra(Intent.EXTRA_TEXT)
val length = sharedText?.length
```

`?.` は、左側が `null` でない場合だけ右側を実行します。`sharedText` が `null` なら、`length` も `null` です。

```kotlin
val text = sharedText ?: ""
```

`?:` は、左側が `null` のときに使う既定値を指定します。この二つを使うと、`null` を無理に通常の値として扱う事故を減らせます。

## データは `data class` にまとめる

このプロジェクトでは、画面へ渡す値を次の形にまとめています。

```kotlin
data class HomeScreenState(
    val isShareIntent: Boolean = false,
    val urls: List<String> = emptyList(),
)
```

`data class` は、データを保持するためのクラスです。`Boolean` は `true` または `false`、`List<String>` は文字列の一覧を表します。`= false` と `= emptyList()` は、値を省略したときの初期値です。

## コレクション操作は処理の流れとして読める

Kotlin では、一覧に対する処理を連結して書けます。

```kotlin
val urls = texts
    .filter { it.isNotBlank() }
    .distinct()
```

`filter` は条件に合う要素だけを残します。`{ it.isNotBlank() }` の `{ ... }` は処理そのものを値として渡すラムダ式で、`it` は現在処理している要素です。`distinct` は重複を除きます。

長く連結しすぎると途中経過が分かりにくくなります。処理の意味が変わる場所では、変数や関数に分けて名前を付けます。

## 条件分岐の `if` と `when` は値を返せる

```kotlin
val message = if (urls.isEmpty()) {
    "URL がありません"
} else {
    "${urls.size}件見つかりました"
}
```

Kotlin の `if` は処理を分岐するだけでなく、結果を値として返せます。分岐が多い場合は `when` を使います。

```kotlin
val label = when (status) {
    "created" -> "新規登録"
    "existing" -> "登録済み"
    else -> "失敗"
}
```

## Android の画面は Compose の関数で組み立てる

Jetpack Compose では、画面の状態から表示内容を作る関数を `@Composable` で示します。

```kotlin
@Composable
fun Greeting(name: String) {
    Text(text = "こんにちは、$name")
}
```

状態が変わると、Compose は必要な表示を作り直します。このリポジトリの `MainActivity` は共有 Intent を読み、`HomeScreenState` を更新します。画面側は状態を受け取り、通常起動と共有起動の表示を切り替えます。

## このリポジトリのコードを読む

最初に [`ShareIntentParser.kt`](../app/src/main/java/com/koukishiba/todobookmark/ShareIntentParser.kt) を読んでください。Android 固有の処理がなく、Kotlin の文字列、正規表現、一覧操作だけで構成されています。

次に [`ShareIntentParserTest.kt`](../app/src/test/java/com/koukishiba/todobookmark/ShareIntentParserTest.kt) を開きます。入力と期待結果が並んでいるため、URL 抽出の仕様を具体例から理解できます。

最後に [`MainActivity.kt`](../app/src/main/java/com/koukishiba/todobookmark/MainActivity.kt) を読みます。ここでは Android の起動処理と Compose の画面がつながります。すべてを一度に理解しようとせず、次の順番で追うと見通しが良くなります。

1. `onCreate` で最初の Intent を読む
2. `toScreenState` で画面用の値を作る
3. `TodoBookmarkScreen` で表示先を分ける
4. `SetupContent` と `SharedLinksContent` で実際の画面を作る

## テストは仕様を実行できる例にする

```bash
./gradlew test
```

このコマンドは `app/src/test` 以下の単体テストを実行します。URL 抽出の規則を変えるときは、先に期待する例をテストへ追加し、そのテストが通るように実装を直すと安全です。

## 最初に覚える用語

| 用語 | このプロジェクトでの意味 |
| --- | --- |
| JVM | Kotlin のコードを実行する基盤。Android のビルドにも関係する |
| Gradle | コンパイル、テスト、APK 作成、依存ライブラリ取得を行うビルドツール |
| Android Gradle Plugin | Gradle に Android アプリ用のビルド機能を追加するプラグイン |
| Intent | Android アプリ間で「共有する」「画面を開く」などの要求とデータを渡す仕組み |
| Jetpack Compose | Kotlin の関数で Android の画面を作る UI ツールキット |
| 単体テスト | 小さな処理へ入力を渡し、期待した結果になるか自動確認するコード |

疑問が残ったら、分からない構文があるファイル名と行を Issue やレビューコメントに書いてください。具体的なコードを起点にすると、必要な知識だけを順番に学べます。

