# Claude Code Plus

<p align="center">
  <img src="jetbrains-plugin/src/main/resources/META-INF/pluginIcon.svg" width="80" alt="Claude Code Plus Logo">
</p>

<p align="center">
  <strong>JetBrains IDE向け高度なAIプログラミングアシスタント</strong>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/28343-claude-code-plus">
    <img src="https://img.shields.io/jetbrains/plugin/v/26972-claude-code-plus.svg" alt="JetBrains Plugin">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/releases">
    <img src="https://img.shields.io/github/v/release/touwaeriol/claude-code-plus" alt="GitHub Release">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/touwaeriol/claude-code-plus" alt="License">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> |
  <a href="README_zh-CN.md">简体中文</a> |
  <a href="README_ja.md">日本語</a> |
  <a href="README_ko.md">한국어</a>
</p>

---

Claude Code Plusは、Claude AIを開発環境に直接統合するIntelliJ IDEAプラグインで、自然言語によるインタラクションを通じてインテリジェントなコードアシスタンスを提供します。

## ✨ 機能

- **AI駆動の会話** - IDE内で直接Claude AIとチャット
- **スマートコンテキスト管理** - @メンションでファイルやコードスニペットを参照
- **マルチセッション対応** - 複数のチャットセッションを同時に管理
- **豊富なツール統合** - Claudeのツール使用を表示・操作（ファイル読み書き、bashコマンドなど）
- **IDE統合** - クリックでファイルを開き、差分を表示、特定の行に移動
- **ダークテーマ対応** - IntelliJのダークテーマと完全互換
- **エクスポート機能** - 会話履歴を複数の形式で保存

## 📦 インストール

### 方法1: JetBrains Marketplace（推奨）
1. JetBrains IDEを開く
2. **設定** → **プラグイン** → **Marketplace** に移動
3. "**Claude Code Plus**" を検索
4. **インストール** をクリックしてIDEを再起動

### 方法2: GitHub Release（手動）
1. [Releases](https://github.com/touwaeriol/claude-code-plus/releases) から最新の `jetbrains-plugin-x.x.x.zip` をダウンロード
2. IDEで: **設定** → **プラグイン** → ⚙️ → **ディスクからプラグインをインストール...**
3. ダウンロードしたzipファイルを選択してIDEを再起動

## 🔧 要件

- **JetBrains IDE**: IntelliJ IDEA 2024.2 - 2025.3.x (Build 242-253)
- **Node.js**: v18以上（[ダウンロード](https://nodejs.org/)）
- **Claude認証**: 初回セットアップが必要
  - ターミナルで実行: `npx @anthropic-ai/claude-code`
  - プロンプトに従って認証を完了
  - 詳細なセットアップガイドは[公式ドキュメント](https://docs.anthropic.com/en/docs/claude-code/getting-started)を参照

> **注**: プラグインにはClaude CLIがバンドルされています - 別途CLIのインストールは不要です！

## 🚀 クイックスタート

1. 上記のインストール手順に従ってプラグインをインストール
2. Claude CLIがインストールされ、認証されていることを確認
3. **Claude Code Plus** ツールウィンドウを開く（右サイドバー）
4. Claudeとの会話を開始！

### ヒント
- `@` を使ってファイルをメンションし、コンテキストとして追加
- ツール出力のファイルパスをクリックするとエディタで開く
- キーボードショートカット:
  - `Ctrl+J` - クイックアクション
  - `Ctrl+U` - 共通操作

## 🤝 コントリビューション

コントリビューションは大歓迎です！お気軽にPull Requestを送ってください。

## 📝 ライセンス

このプロジェクトはMITライセンスの下でライセンスされています - 詳細は [LICENSE](LICENSE) ファイルをご覧ください。

## 🔗 リンク

- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28343-claude-code-plus)
- [GitHubリポジトリ](https://github.com/touwaeriol/claude-code-plus)
- [Issue Tracker](https://github.com/touwaeriol/claude-code-plus/issues)
- [変更履歴](https://github.com/touwaeriol/claude-code-plus/releases)

---

<p align="center">
  ❤️ を込めて <a href="https://github.com/touwaeriol">touwaeriol</a> が作成
</p>
