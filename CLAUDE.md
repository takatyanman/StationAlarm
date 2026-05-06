# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 言語ポリシー
- ユーザーへの応答は必ず日本語で行うこと
- 不確実な情報には「（推測です）」と明記すること
- コードコメントは日本語で書くこと

## プロジェクト概要

**StationAlarm** は Wear OS（スマートウォッチ）向け Android アプリ。GPS で現在地を継続追跡し、設定した駅に近づくと振動・通知で知らせる。

## ビルド・実行コマンド

```bash
# デバッグビルド
./gradlew assembleDebug

# デバイスへインストール
./gradlew installDebug

# クリーンビルド
./gradlew clean assembleDebug

# Lint チェック
./gradlew lint

# 単体テスト（現時点ではテストなし）
./gradlew test

# インストルメンテーションテスト
./gradlew connectedAndroidTest
```

## アーキテクチャ

### レイヤー構成

```
UI (Compose) → ViewModel → Repository ← Service
                                ↑
                          SharedPreferences
```

4 つのレイヤーが協調動作する：

1. **UI層** (`presentation/`) — Wear Compose による 2 画面構成  
   - `SetupScreen`: 駅名入力・距離しきい値設定・検索履歴表示  
   - `TrackingScreen`: リアルタイム距離表示（円形プログレス・距離に応じた色変化）

2. **ViewModel** (`MainViewModel`) — `StateFlow<UiState>` で UI 状態を管理  
   - Geocoder（`Locale.JAPAN`）で駅名→座標変換  
   - フォアグラウンドサービスの起動・停止を指示

3. **Repository** (`StationRepository`) — シングルトン、サービスと UI の橋渡し  
   - 永続データ: 検索履歴（直近 5 件、SharedPreferences）  
   - 揮発データ: `TrackingState`（isTracking・現在距離・メッセージ）を `StateFlow` で配信

4. **Service** (`StationAlarmService`) — フォアグラウンドサービス、バックグラウンド位置追跡  
   - `LocationManager` から `Flow<Location>` を受け取り、しきい値到達で振動・フルスクリーン通知

### データフロー（追跡中）

```
FusedLocationProvider
  → LocationManager (Flow<Location>)
  → StationAlarmService (距離計算・振動トリガー)
  → StationRepository.updateTrackingState()
  → MainViewModel (StateFlow 購読)
  → TrackingScreen (UI 再描画)
```

### 重要な設計上の決定

- **Service ↔ UI 通信は Repository 経由**。Service が直接 ViewModel を参照しない。  
- `StationRepository` はスレッドセーフな `companion object` シングルトンで実装。Context を引数に取る `getInstance()` を使う。  
- 位置更新間隔: 3 秒（最小 1 秒）。`LocationManager.kt` の `LocationRequest` で変更可能。  
- 振動パターン・しきい値到達判定は `StationAlarmService` 内に実装。

## SDK・主要依存関係

| 項目 | バージョン |
|------|-----------|
| compileSdk / targetSdk | 34 |
| minSdk | 30 (Android 11) |
| Kotlin | 1.9.0 |
| AGP | 8.2.0 |
| Compose BOM | wear-compose-material |
| Play Services Location | FusedLocationProvider |

## Wear OS 固有の考慮事項

- **ロータリー入力**: `SetupScreen` は Wear OS の回転ベゼル/デジタルクラウンでスクロール対応。`Modifier.onRotaryScrollEvent` を使用。  
- **テーマ**: 距離に応じて色が変化（遠: 青 → 中: 緑 → 近: オレンジ → 至近: 赤）。OLED 画面向けダークテーマ。  
- **フルスクリーン通知**: `CATEGORY_ALARM` + `fullScreenIntent` で到着時に画面全体に表示。  
- 文字列リソースはすべて日本語（`res/values/strings.xml`）。

## 必要なパーミッション

`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`（実行時リクエスト）、`FOREGROUND_SERVICE_LOCATION`、`VIBRATE`、`POST_NOTIFICATIONS`。  
`MainActivity.onCreate()` でパーミッションチェック・リクエストを行う。
