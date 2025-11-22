# 排除 temp_idea_source 目录

## 问题描述

项目根目录下的 `temp_idea_source` 目录包含 IDEA 的源代码/测试数据，导致索引错误：
```
Error while indexing C:\Users\16790\IdeaProjects\claude-code-plus\temp_idea_source\java\java-tests\testData\psi\cls\mirror\src\pkg\GrTrait.groovy
```

## 解决方案

### 方法 1: 在 IDEA 中排除目录（推荐）

1. 打开 **File** → **Settings** (或 `Ctrl+Alt+S`)
2. 导航到 **Project Structure** → **Modules**
3. 选择你的项目模块
4. 在 **Sources** 标签页中，找到 `temp_idea_source` 目录
5. 右键点击该目录，选择 **Mark Directory as** → **Excluded**
6. 或者在 **Excluded Folders** 中添加 `temp_idea_source`

### 方法 2: 在项目设置中排除

1. 打开 **File** → **Settings** → **Project Structure**
2. 在左侧选择 **Modules**
3. 展开你的项目模块
4. 在 **Excluded Folders** 部分，点击 `+` 按钮
5. 选择 `temp_idea_source` 目录

### 方法 3: 删除目录（如果不需要）

如果这个目录不是必需的，可以删除它：

```powershell
# Windows PowerShell
Remove-Item -Path temp_idea_source -Recurse -Force
```

```bash
# Linux/Mac
rm -rf temp_idea_source
```

## 重新索引

排除目录后，需要重新索引项目：

1. 打开 **File** → **Invalidate Caches...**
2. 选择 **Invalidate and Restart**
3. 等待 IDEA 重启并重新索引

## 注意事项

- `temp_idea_source` 目录已添加到 `.gitignore`，不会被提交到 Git
- 如果这个目录是必需的，请确保它被正确排除，避免索引错误
- 目录大小约 488 MB，排除后可以显著提升索引速度


