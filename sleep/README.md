# 睡眠残高

Health Connect の睡眠セッションを読み取り、直近14日間の睡眠負債を表示する Android アプリです。

## ビルド

Android Studio でこのフォルダーを開き、Gradle の同期後に `app` を実行してください。Android SDK 36 と JDK 17 以降が必要です。

Health Connect のプライバシーポリシーURLは `app/src/main/AndroidManifest.xml` の `health_permissions.policy` に設定してください。現在はサンプルURLです。

## 動作

- Health Connect の `READ_SLEEP` 許可を要求します。
- 直近14日間の日ごとに睡眠セッションの時間を集計し、1日450分を目標に負債を計算します。
- 目標未満の睡眠は不足分だけ負債に加算し、目標を超えた睡眠は負債の返済に使います。負債は0を超えません。
- `SleepRepository.SHORTFALL_THRESHOLD_MINUTES` で負債計上の閾値（デフォルト0分）を設定できます。閾値を超えた不足分のみを負債に加算します。
- 表示時に負債を10分単位で四捨五入し、中央に赤色で表示します。負債が0の場合は「負債ゼロ」と表示します。
- Health Connect 連携ボタンから権限を要求し、許可済みの場合はデータを再同期できます。
- Android 14以降のシステム組み込みHealth Connectと、それ以前のHealth Connectアプリをサポートします。
- Health Connectの権限画面にアプリを表示するためのプライバシー利用目的とアクティビティ宣言を含みます。
