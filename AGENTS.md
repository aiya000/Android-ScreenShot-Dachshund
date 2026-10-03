# AGENTS.md

このリポジトリで作業する AI エージェント向けの指示です。

## 前に見たことのある出来事

下の表は**索引**です。行だけ読んでください。
**いま扱っていることに当てはまる行があったときだけ**、その `agents/` のファイルを開きます。

| おこったこと | したこと | 読むもの |
|---|---|---|
| エミュレータのテストで、浮かぶバー（開始／停止）が画面に出ているのに「無い」と言われる。撮影がとつぜん止まる | `uiautomator dump` が他のユーザー補助サービスを unbind していた。バーは `dumpsys window` の座標で押す。`am force-stop` も有効化を外す。スワイプ中のタップは下のアプリに届く | [agents/tests/uiautomator-unbinds-the-service.md](./agents/tests/uiautomator-unbinds-the-service.md) |

### この索引に足すこと

「**これは次も起こる**」「**次も同じことをする**」と思ったものが出てきたら、頼まれるのを待たずに足してください。

- 詳細は `agents/<英単語のカテゴリ>/<出来事の名前>.md` に。中身は日本語で、
  「おこったこと」「原因」「したこと」「次に気をつけること」「関係する場所」の順
- `AGENTS.md` には**上の表に 1 行足すだけ**。本文をここに書かないでください
- 足す価値があるのは、コードや `git log` を読んでも分からないことだけです

## このアプリのこと

- LongShot の OSS 代替。目的は `README.md` の Purpose
- 画面の取得は **`AccessibilityService.takeScreenshot()`**（API 30）。MediaProjection は使いません。
  同意ダイアログが要らず、スクロール用の `dispatchGesture` と同じサービスで済むからです。
  minSdk 30 はそのためです
- 撮影の順序は `CaptureSession`（純粋 Kotlin の状態機械）が決め、`DachshundService` はその
  命令を実行するだけです。**同時に走るのはスクリーンショットかスワイプのどちらか一つ**。
  LongShot が固まる原因（停止時の撮影と周期撮影の競合）を、設計で起きなくしています
- 結合は行ハッシュだけで決めます（`Joiner` / `FixedEdges`）。ページは PNG として
  `cacheDir/captures/<capture>/` に置き、結合結果は `PngWriter` が行ごとに書くので、
  何ページでもビットマップを一枚まるごと持つことはありません
- 編集画面（`EditActivity`）が開くときに結合します。保存は `Pictures/ScreenShot-Dachshund/`

## Git

- **現在、一時的に、ユーザーの確認なしに `git push` と `gh pr merge` を許可しています**（2026-10-04 から）。
  `main` への直接 push は、ユーザーが「main に直 push でいい」と言ったものだけです
- 新しいブランチは `origin/main` から切ります:

  ```bash
  git fetch origin main
  git switch -c <ブランチ名> --no-track origin/main
  ```

  `--no-track` を忘れると素の `git push` が `main` へ飛びます。push は
  `git push -u origin <ブランチ名>` と行き先を明示します
- **ローカルの `main` は古いままでかまいません。** ビルドも PR のベースも `origin/main` です
- `git add` は明示パスで。`-A` / `.` / `-u` は使いません

## Pull Request と Issue

- Issue 1 つにつき PR 1 つ。本文は `Refs #N`（`Closes` ではなく）。閉じる判断はこちらでします
- **PR がマージされて、その Issue の中身が本当に終わっているなら、エージェントが閉じます。**
  閉じるときは、どの PR で入ったか、どのテストが緑になったか、何が残っているかを一言コメントします
- 残りがあるなら閉じません。何が残っているかをコメントして開けておきます

## 確かめかた: テストを先に書く

**「動きました」と言う前に、自動テストを書きます**（TDD）。

- JVM テスト: `app/src/test/`、`mise exec java@17.0.2 -- ./gradlew :app:testDebugUnitTest`。
  Context の要らないロジック（撮影の状態機械、固定ヘッダー検出、重なり検出、PNG 書き出し）はここ
- エミュレータ: `test-device/`（`test-device/README.md`）。画面の操作と、その順序を見ます。
  **実機には向けません**（`require_emulator`）
- **一発で緑になったテストは疑うこと。** わざとコードを壊して赤くなるのを見てから信じます。
  テストと直しを同時に書いたときも同じです
- `rg -q` にパイプで流さないこと。見つかった瞬間にパイプが閉じて、`pipefail` で失敗になります
  （`lib.sh` の `log_matches` のように、変数に受けてから探す）

## ビルドと動作確認

`.claude/skills/` の `debug-build` / `debug-install` / `release-build` / `release-install` を使います。
JDK は mise の `java@17.0.2` です。

## 実機を操作するときは、ユーザーの許可を取ること

`adb` で実機の画面を動かす前に、必ずユーザーに聞いてください。許可なしでよいのは `adb devices`、
`adb install`、ログの読み出しだけです。自動テストはエミュレータに向けます。

## 報告のしかた: 部品ではなく、できるようになったことを書く

セッションの終わりの報告は、機能・仕様の視点で書きます。「〜できるようになりました」から書き始め、
失敗したときにどうなるかも仕様として書き、**まだできないことを必ず書きます**。
クラス名やメソッド名を並べた実装の報告は歓迎されません。

## セッションを終えるとき

`/create-handoff` を実行したら、そのセッションで使った gradle デーモンを落とします:

```bash
mise exec java@17.0.2 -- ./gradlew --stop
```

エミュレータを起動していたら、それも止めます（`adb -s emulator-5554 emu kill`）。
