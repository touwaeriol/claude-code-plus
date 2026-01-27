### Git コミットポリシー

**重要**: ターミナルコマンド（git commit, git add, git push など）をバージョン管理操作に使用しないでください。
代わりに jetbrains_git MCP ツールを使用する必要があります。

### コミットワークフロー

1. `GetVcsChanges()` → 変更リストを取得
2. 変更を分析し、`SelectFiles` / `DeselectFiles` でファイル選択を調整
3. `SetCommitMessage()` → コミットメッセージを生成して入力
4. **必ず** `AskUserQuestion` でユーザーに確認を求める
5. ユーザーの確認後、`CommitChanges()` を呼び出して実行

### 使用するタイミング

IDEA の VCS/Git 統合との連携に使用：変更の読み取り、コミットメッセージの設定、ステータスの確認。

### ファイル選択ツール

- `SelectFiles(paths, mode)` → Commit パネルでファイルを選択（mode: "replace" 置換または "add" 追加）
- `DeselectFiles(paths)` → ファイルの選択を解除
- `SelectAllFiles()` → すべての変更ファイルを選択
- `DeselectAllFiles()` → すべての選択を解除

### コミットメッセージ規約 (Conventional Commits)

Conventional Commits 形式に従う：

```
<type>(<scope>): <description>

[オプションの本文]

[オプションのフッター]
```

**タイプ**:
- `feat`: 新機能
- `fix`: バグ修正
- `docs`: ドキュメントのみの変更
- `style`: コードスタイルの変更（フォーマット、セミコロンの欠落など）
- `refactor`: 機能追加やバグ修正を含まないリファクタリング
- `perf`: パフォーマンス改善
- `test`: テストの追加または修正
- `chore`: ビルドプロセス、補助ツールの変更など
- `ci`: CI 設定の変更
- `build`: ビルドシステムまたは外部依存関係の変更

**例**:
- `feat(auth): add OAuth2 login support`
- `fix(api): resolve null pointer exception in user endpoint`
- `docs: update README with installation instructions`
- `refactor(core): simplify data processing logic`

### 注意事項

- コミット前に必ずユーザーのレビューを待つ
- `CommitChanges` で `push=true` を使用すると、コミットとプッシュを一度に実行
