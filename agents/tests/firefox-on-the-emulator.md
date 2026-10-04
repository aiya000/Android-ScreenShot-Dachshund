# エミュレータに Firefox を入れて、下 URL バーのページを撮る

## おこったこと

Firefox は URL バーを画面の下に置き、ページがスクロールすると隠す。1 ページ目の下端にだけ
バーが写り、結合すると最初の継ぎ目にバーが残った（#27）。実機でしか起きないと思われていたが、
エミュレータに Firefox を入れれば同じ形で再現できる。

## したこと

- Mozilla のアーカイブから x86_64 の APK を落として入れる（Play ストアは要らない）:

  ```sh
  curl -s https://archive.mozilla.org/pub/fenix/releases/ | rg -o 'releases/([0-9]+\.[0-9]+\.[0-9]+)/' -r '$1' | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1
  curl -o firefox.apk https://archive.mozilla.org/pub/fenix/releases/<ver>/android/fenix-<ver>-android-x86_64/fenix-<ver>.multi.android-x86_64.apk
  adb -s emulator-5554 install -r -g firefox.apk
  ```

  140MB ほど。パッケージは `org.mozilla.firefox`
- 初回起動は「Continue」→ 既定ブラウザのダイアログを「Cancel」→「Not now」で抜ける
- URL バーを下に: ⋮ → Settings → Customize → 「Address bar location」の「Bottom」。
  メニューの文字を uiautomator で探すと VPN の行に当たるので、スクリーンショットを見て座標で押す
- 初回のページ表示で「Shake your device to summarize this page」の紫の吹き出しが出る。
  これは 1 ページ目にだけ写り、バーのように下端に接していないので結合に残る。一度閉じれば出ない
- ページは `am start -a android.intent.action.VIEW -d <url> org.mozilla.firefox` で開く
  （毎回タブが増えるが、ページは先頭から表示される）

## 次に気をつけること

- 継ぎ目の判定は「前ページの下端から上に見て、次ページの対応する行と似ている模様のある行」まで
  を前ページから使う（`Joiner.seam`）。下端に接していないもの（吹き出し、トースト）は残る
- 結合結果のどこまでが 1 ページ目かは、`run-as` で `cache/captures/<capture>/page-0001.png` を取り出して
  行ごとに比べると分かる（Wikipedia の Dachshund で 2169 行目 = バーの直上だった）

## 関係する場所

- `app/src/main/kotlin/.../join/Joiner.kt`: `seam`, `agreeingEnd`
- `app/src/test/kotlin/.../join/JoinerTest.kt`: `JoinerTrailingBarTest`
- `test-device/drive/lib.sh`: `tap_bar`, `save_and_name`, `png_size`
