# uiautomator を使うと、サービスが一度切れる

## おこったこと

エミュレータのテストで、浮かぶバー（開始／停止）がたしかに画面に出ているのに、台本が
「バーが無い」と言って落ちた。`uiautomator dump` でバーの文字を探していた。

## 原因

`uiautomator dump` は UiAutomation を登録する。そのあいだ、システムは**ほかのユーザー補助
サービスをすべて unbind する**（`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` が無いので）。
Dachshund のサービスは unbind されると `abandon()` でバーと進行中の撮影を捨てる。dump が
終わると 1 秒ほどで bind し直されるが、バーは戻らない。

ログには `service unbound` → `service destroyed` → `service connected` と並ぶ。

## したこと

- バーは `dumpsys window windows` の `Window{... u0 ScreenShot Dachshund}` の `frame=` から
  位置を読んで、`input tap` で押す（`test-device/drive/lib.sh` の `overlay_frame` /
  `tap_bar`）。撮影中は uiautomator を一切使わない
- 撮影が終わったあと（編集画面）なら uiautomator を使ってよい。サービスが切れても困らない
- サービスが要るタップ（ホームの「Start a capture」）は、dump のあと 2 秒待ってから押す
  （`ui_tap_text` の中の `sleep 2`）

## 次に気をつけること

- **`dispatchGesture` でスワイプしている最中のタップは、バーではなく下のアプリに届く**（スワイプと
  同じタッチストリームに合流する）。テストでは `swipe done` のログを待ってから停止を押す。
  本体側はスワイプを短く（400 ms + 120 ms）して、当たる時間を減らしている

- 実機でも同じ。ユーザーが別のユーザー補助ツール（スイッチアクセスなど）を切り替えると、
  撮影中のセッションは失われる。これは仕様として受け入れている
- `am force-stop` も別の落とし穴: force-stop されたアプリのユーザー補助サービスは
  「有効」の一覧から外される。テストでは**止めてから有効化する**（`install_app` の順番）

## 関係する場所

- `test-device/drive/lib.sh`: `overlay_frame`, `tap_bar`, `ui_tap_text`, `install_app`
- `app/src/main/kotlin/.../service/DachshundService.kt`: `onUnbind` → `abandon()`
