# 富文本输入系统功能测试

## 测试步骤

### 1. 启动应用
```bash
./gradlew :toolwindow-test:run
```

### 2. 测试输入框富文本功能
1. 在输入框中输入 `@`
2. 选择一个文件（如 `ContextSelectorTestApp.kt`）
3. 观察输入框中的显示：应该看到蓝色超链接样式的 `@ContextSelectorTestApp.kt`
4. 尝试用退格键删除引用：应该整体删除，不留残留
5. 尝试用箭头键移动光标：光标应该跳过引用内部

### 3. 测试用户消息显示一致性
1. 发送包含文件引用的消息
2. 观察发送后的用户消息显示：应该和输入框使用相同的蓝色超链接样式
3. 点击消息中的文件引用：应该有点击响应（控制台输出）

### 4. 测试数据格式一致性
1. 发送包含 `@file://` 引用的消息给 AI
2. 检查发送给 Claude 的实际格式：应该保持 `@file://path` 完整格式
3. AI 应该能正确理解文件引用并读取文件内容

## 预期效果

### 视觉效果
- ✅ 输入框中：`@file://src/main.kt` → 显示为蓝色下划线 `@main.kt`
- ✅ 用户消息中：同样显示为蓝色下划线 `@main.kt`
- ✅ 样式一致：两者使用相同的颜色 (#007ACC) 和下划线装饰

### 交互效果
- ✅ 整体删除：退格键删除整个引用
- ✅ 光标跳跃：箭头键不会停在引用内部
- ✅ 点击响应：可以点击引用查看详情

### 数据格式
- ✅ 存储格式：`@file://src/main.kt`（完整路径）
- ✅ 发送格式：`@file://src/main.kt`（AI能理解）
- ✅ 显示格式：`@main.kt`（用户友好）

## 技术验证

### Visual Transformation 工作流程
```
输入: "@file://src/components/Button.kt "  (自动添加空格)
       ↓ Visual Transformation
显示: "@Button.kt " (蓝色超链接 + 空格)
       ↓ 用户编辑/发送
输出: "@file://src/components/Button.kt "
```

### 偏移映射验证
```
原始文本: "Hello @file://src/main.kt world"   (30字符，含空格)
显示文本: "Hello @main.kt world"             (19字符，含空格)

光标位置映射:
- 显示位置 13 (@main.kt 空格的末尾) → 原始位置 24 (@file://src/main.kt 空格的末尾)
- 点击位置 7 (@符号) → 原始位置 6 (@符号)
```

### 支持的引用类型
- `@file://path/to/file.kt ` → `@file.kt ` (带空格)
- `@https://example.com/page ` → `@example.com ` (带空格)
- `@git://github.com/user/repo.git ` → `@repo ` (带空格)
- `@symbol://MyClass.method ` → `@method ` (带空格)

## 已知限制

1. **ClickableText 废弃警告**：使用了被废弃的 ClickableText API，但功能正常
2. **复杂嵌套**：暂不支持引用内嵌套其他引用
3. **多行引用**：引用路径不能包含换行符

## 最新优化

### ✅ 自动空格分隔（已实现）
- 插入引用后自动添加空格，避免后续输入干扰
- 示例：`@file://main.kt hello` 而不是 `@file://main.kthello`
- 确保 Visual Transformation 正确识别引用边界

## 后续优化

1. 迁移到新的 LinkAnnotation API 以消除废弃警告
2. 添加悬停提示显示完整路径
3. 支持更多引用类型的智能识别
4. 优化大文件路径的显示策略