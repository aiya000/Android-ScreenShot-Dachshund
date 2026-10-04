# NEW_TASK で開くと、古い編集画面がそのまま前に出てくる

## おこったこと

編集画面を開いたまま（保存せず、アプリも閉じず）、タイルからもう一度撮影すると、
2 回目の結果が出ず、1 回目の編集画面がそのまま前に出た（#31）。
エミュレータの台本（ホーム画面の「Start a capture」から始める）では再現しなかった。

## 原因

サービスは撮影が終わると `startActivity(EditActivity, FLAG_ACTIVITY_NEW_TASK)` で編集画面を開く。
`NEW_TASK` は「その Intent と同じ Intent（component・action・data を比べる。**extra は比べない**）を
根に持つタスクがあれば、新しいアクティビティは作らず、そのタスクをそのまま前に出す」。
撮影フォルダは extra なので、2 回目の Intent は 1 回目と「同じ」に見える。

ホーム画面から始めるとタスクの根は `MainActivity` なので比較が外れ、編集画面が上に積まれて
ふつうに動いてしまう。タイルの入口 `StartActivity` は `taskAffinity=""` と `noHistory` で
アプリのタスクに残らないので、編集画面がタスクの根になり、この道だけで起きる。

## したこと

- `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` を足し、`EditActivity.onNewIntent` で
  新しいフォルダを読み直す（`load(dir)`）。古い未保存の撮影は置き換わる（ページはキャッシュに残る）
- 台本 60 は**タイルから**撮影を始める（`CAPTURE_FROM=tile`、`add_tile`）。
  `cmd statusbar click-tile` は**パネルを開いていないと効かない**ので、先に `expand-settings` する

## 次に気をつけること

- 編集画面に関わる不具合は、ホーム画面からとタイルからの両方で確かめる。タスクの形が違う
- `am force-stop` ではなく `install_app` の順で（止めてから有効化）。索引の uiautomator の行と同じ罠

## 関係する場所

- `app/src/main/kotlin/.../service/DachshundService.kt`: `finish()` の `startActivity`
- `app/src/main/kotlin/.../ui/EditActivity.kt`: `onNewIntent`, `load`
- `test-device/drive/lib.sh`: `add_tile`, `capture_the_settings`（`CAPTURE_FROM`）
- `test-device/drive/60-a-second-capture-replaces-the-first.sh`
