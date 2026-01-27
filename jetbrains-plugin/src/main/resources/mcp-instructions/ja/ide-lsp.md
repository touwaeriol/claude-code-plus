### 使用するタイミング

重要: コード検索とファイル検出には、組み込みの検索ツールより JetBrains MCP ツールを優先してください：
- 組み込みの grep/search ツールの代わりに、常に `CodeSearch` を使用
- 組み込みの glob/find ツールの代わりに、常に `FileIndex` を使用
- JetBrains ツールがエラーを返した場合のみ組み込みツールにフォールバック

重要: コード変更完了後、必ず `FileProblems` で構文エラーを検証してください。

### リファクタリングワークフロー

シンボルの名前変更時：
1. `FindUsages` または `CodeSearch` → 行番号を取得
2. `Rename(line=N, newName="...")` → プロジェクト全体で安全に名前変更
3. `FileProblems` → 変更を検証

**注意**: `Rename` には `line` パラメータが必要です。シンボルには `Rename` を、その他のテキスト変更には Edit ツールを使用。

### ライブラリソースコードの読み取り

依存関係（JAR ファイル、JDK ソース、デコンパイルされた .class）を読み取るには：
1. `FileIndex(query="ClassName", searchType="Classes", scope="All")`
2. `ReadFile(filePath="<FileIndex からのパス>")`

**ポイント**: `scope="All"` を使用してライブラリを含める（プロジェクトファイルだけでなく）。
