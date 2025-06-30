# Jewel 组件迁移指南

本文档详细说明了如何将项目中的原生 Compose 组件替换为 IntelliJ Jewel 组件，以获得更好的 IDE 集成和一致的用户体验。

## 为什么要使用 Jewel 组件？

1. **原生 IntelliJ 外观**: Jewel 组件完全匹配 IntelliJ IDEA 的设计语言
2. **主题一致性**: 自动跟随 IDE 的明暗主题切换
3. **平台适配**: 针对不同操作系统（macOS、Windows、Linux）的原生行为
4. **性能优化**: 专为 IDE 环境优化的组件实现
5. **无缝集成**: 与 IntelliJ 插件系统完美配合

## 组件替换对照表

### 1. 文本组件

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `androidx.compose.material.Text` | `org.jetbrains.jewel.ui.component.Text` | 自动主题集成，更好的颜色管理 |
| `androidx.compose.foundation.text.BasicText` | `org.jetbrains.jewel.ui.component.Text` | 统一的文本样式系统 |

**迁移示例**:
```kotlin
// 原生 Compose
Text(
    "Hello World",
    style = TextStyle(
        color = Color.White,
        fontSize = 14.sp
    )
)

// Jewel 组件
Text(
    "Hello World",
    color = JewelTheme.globalColors.text.normal,
    fontSize = 14.sp
)
```

### 2. 滚动容器

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `LazyColumn` | `VerticallyScrollableContainer` | 原生滚动条样式，平台特定行为 |
| `LazyRow` | `HorizontallyScrollableContainer` | 自动隐藏/显示逻辑 |

**迁移示例**:
```kotlin
// 原生 Compose
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(16.dp)
) {
    items(messages) { message ->
        MessageItem(message)
    }
}

// Jewel 组件
VerticallyScrollableContainer(
    scrollState = rememberScrollState(),
    modifier = Modifier.fillMaxSize()
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        messages.forEach { message ->
            MessageItem(message)
        }
    }
}
```

### 3. 按钮组件

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `Button` | `DefaultButton` | IntelliJ 按钮样式，交互状态 |
| `OutlinedButton` | `OutlinedButton` | 原生轮廓按钮样式 |
| `IconButton` | `IconButton` | 完整的图标按钮实现 |

**迁移示例**:
```kotlin
// 原生 Compose
Button(
    onClick = { onSend() },
    modifier = Modifier.size(32.dp)
) {
    Text("发送")
}

// Jewel 组件
DefaultButton(
    onClick = { onSend() },
    modifier = Modifier.size(32.dp)
) {
    Text("发送")
}
```

### 4. 输入框组件

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `BasicTextField` | `TextField` | 原生输入框样式，焦点管理 |
| `OutlinedTextField` | `TextField` | 统一的输入框组件 |
| `TextArea` | `TextArea` | 多行文本输入组件 |

**迁移示例**:
```kotlin
// 原生 Compose
BasicTextField(
    value = text,
    onValueChange = onTextChange,
    textStyle = TextStyle(color = Color.White),
    modifier = Modifier.fillMaxWidth()
)

// Jewel 组件
TextField(
    value = text,
    onValueChange = onTextChange,
    modifier = Modifier.fillMaxWidth()
)
```

### 5. 分隔线组件

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `Divider` | `org.jetbrains.jewel.ui.component.Divider` | 原生分隔线样式，方向支持 |

**迁移示例**:
```kotlin
// 原生 Compose
Divider(
    color = Color.Gray,
    thickness = 1.dp
)

// Jewel 组件
Divider(
    orientation = Orientation.Horizontal,
    modifier = Modifier.height(1.dp)
)
```

### 6. 其他高级组件

| 原生组件 | Jewel 组件 | 主要改进 |
|---------|-----------|---------|
| `DropdownMenu` | `Menu` | IntelliJ 风格菜单 |
| `Card` | 使用 `Box` + `Jewel` 样式 | 更好的主题集成 |
| `Switch` | `Checkbox` (切换模式) | 原生切换控件 |
| `RadioButton` | `RadioButton` | 原生单选按钮 |

## 完整迁移示例

### 迁移前（原生 Compose）:
```kotlin
@Composable
fun OriginalChatView() {
    Column {
        LazyColumn {
            items(messages) { message ->
                Card {
                    Text(message.content, color = Color.White)
                }
            }
        }
        Divider(color = Color.Gray)
        Row {
            BasicTextField(value = text, onValueChange = {})
            Button(onClick = {}) {
                Text("发送")
            }
        }
    }
}
```

### 迁移后（Jewel）:
```kotlin
@Composable
fun JewelConversationView() {
    Column {
        VerticallyScrollableContainer(rememberScrollState()) {
            Column {
                messages.forEach { message ->
                    Box(
                        modifier = Modifier.background(
                            JewelTheme.globalColors.panelBackground,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text(
                            message.content,
                            color = JewelTheme.globalColors.text.normal
                        )
                    }
                }
            }
        }
        Divider(orientation = Orientation.Horizontal)
        Row {
            TextField(value = text, onValueChange = {})
            DefaultButton(onClick = {}) {
                Text("发送")
            }
        }
    }
}
```

## 注意事项

### 1. 导入更改
确保更新导入语句：
```kotlin
// 移除
import androidx.compose.material.*
import androidx.compose.foundation.lazy.*

// 添加
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
```

### 2. 主题系统
使用 Jewel 的主题系统而不是硬编码颜色：
```kotlin
// 避免
color = Color.White

// 推荐
color = JewelTheme.globalColors.text.normal
```

### 3. 样式简化
Jewel 组件自动处理样式，无需复杂的 `TextStyle`：
```kotlin
// 原生 Compose
Text(
    "标题",
    style = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
)

// Jewel
Text(
    "标题",
    fontSize = 16.sp,
    fontWeight = FontWeight.Bold,
    color = JewelTheme.globalColors.text.normal
)
```

### 4. 渐进式迁移
可以逐步迁移，新旧组件可以共存：
1. 先迁移核心组件（Text、Button）
2. 然后迁移布局容器（ScrollableContainer）
3. 最后迁移高级组件（Menu、Dialog）

## 性能影响

使用 Jewel 组件的性能影响：
- **正面**: 更好的内存管理，专为 IDE 优化
- **负面**: 略微增加的初始化开销
- **整体**: 对用户体验有显著提升

## 兼容性

- **IntelliJ 版本**: 2023.1+
- **Compose 版本**: 与项目当前版本兼容
- **平台支持**: Windows、macOS、Linux

## 下一步

1. 按照优先级迁移组件（Text → Button → Container → Advanced）
2. 测试主题切换功能
3. 验证不同平台的行为一致性
4. 更新相关文档和示例代码 