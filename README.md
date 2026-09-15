# SMS転送 (SmsForwarder)

Android 端末に届いた SMS を、指定したメールアドレスへ自動転送するアプリです。
SMTP サーバー経由で送信するため、サーバーやクラウドの用意は不要です。

## 動作の仕組み

1. `SmsReceiver` が OS の SMS 受信ブロードキャストを受け取る（アプリを終了していても動作）
2. 分割された長文 SMS を送信元ごとに連結し、`ForwardWorker` を WorkManager に登録する
3. `ForwardWorker` が SMTP でメールを送信する。失敗時は指数バックオフで最大 5 回再試行し、それでも失敗した場合は通知で知らせる
4. 設定（SMTP のパスワードなど）は EncryptedSharedPreferences に暗号化して保存する

## 必要環境

- Android Studio Ladybug 以降（JDK 17 同梱）
- Android 8.0 (API 26) 以上の端末

## ビルド手順

1. Android Studio で本フォルダを開く（`File > Open`）
2. Gradle Sync を実行する。`gradlew` が無い場合は Android Studio が自動生成するか、Gradle がインストール済みなら次を実行する

   ```bash
   gradle wrapper --gradle-version 8.9
   ```

3. 端末を USB 接続し `Run` する。または `Build > Build Bundle(s) / APK(s) > Build APK(s)` で APK を作成して端末にインストールする

## 初期設定

アプリを起動して以下を入力し、「保存」→「テスト送信」で疎通を確認してください。

| 項目 | Gmail の例 |
| --- | --- |
| SMTPホスト | `smtp.gmail.com` |
| ポート | `465` |
| 接続方式 | SSL/TLS |
| ユーザー名 | Gmail アドレス |
| パスワード | **アプリパスワード**（16 桁） |
| 送信元アドレス | Gmail アドレス |
| 転送先アドレス | 受け取りたいアドレス（カンマ区切りで複数可） |

Gmail のアプリパスワードは、Google アカウントで 2 段階認証を有効にしたうえで
<https://myaccount.google.com/apppasswords> から発行します。通常のログインパスワードでは送信できません。

その後、画面下部で次の 2 点を済ませてください。

- **SMS受信権限を許可する**: 未許可だと SMS を受け取れません
- **電池の最適化から除外する**: 端末がスリープ中でも遅延なく転送するために推奨します

最後に「SMSをメールへ自動転送する」を ON にして保存すれば完了です。

## 注意事項

- Google Play では `RECEIVE_SMS` 権限を使うアプリの公開が厳しく制限されています。本アプリは自分の端末に直接インストール（サイドロード）して使う想定です
- Xiaomi / OPPO / Huawei など独自の省電力機能を持つ端末では、別途「自動起動」の許可が必要な場合があります
- 動作ログには送信元番号と結果のみ記録し、SMS 本文は保存しません
- 転送先メールには SMS 本文がそのまま含まれます。認証コードなどが届くため、転送先メールボックスの管理には注意してください
