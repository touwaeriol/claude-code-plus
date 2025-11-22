# Tool演示示例文件

## 简介
这是一个用于演示 Claude Code Plus Tool使用的示例文件。

## Read Tool
Read Tool用于读取文件内容，可以指定行范围。

## Write Tool
Write Tool用于创建新文件或覆盖现有文件。

## Edit Tool
Edit Tool用于精确修改文件中的特定内容。

**特点**:
- 通过指定 old_string 和 new_string 进行精确替换
- 支持 replace_all 参数进行全局替换
- 必须先使用 Read Tool读取文件内容

## MultiEdit Tool
MultiEdit Tool用于在一个文件中进行多处编辑。

---

*创建时间: 2025-11-22*
*Tool版本: Claude Code Plus*
