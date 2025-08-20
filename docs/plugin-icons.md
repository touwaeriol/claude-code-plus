# Claude Code Plus 插件图标设计

## 图标概览

为 Claude Code Plus 插件设计了多种风格的图标，您可以根据喜好选择使用。

## 图标设计方案

### 1. 主设计 - 渐变科技风（默认）
**文件**: `claudeCodePlus.svg`

**设计理念**：
- 蓝色渐变背景，体现科技感
- `< >` 代码符号，表示编程
- 中心 "C" 字母，代表 Claude
- 金色闪电，象征 AI 智能
- 适合所有主题

**特点**：
- 清晰的符号识别度
- 现代渐变设计
- 动态闪电元素

### 2. 暗色主题版本
**文件**: `claudeCodePlus_dark.svg`

**设计理念**：
- 针对暗色主题优化的配色
- 更柔和的对比度
- 保持与主设计一致的元素

### 3. 代码编辑器风格
**文件**: `claudeCodePlus_alt.svg`

**设计理念**：
- 模拟代码编辑器界面
- 彩色代码高亮效果
- 闪烁的光标动画
- 极简主义设计

**特点**：
- 独特的代码行视觉效果
- 动态光标闪烁
- 深色背景适合长时间使用

### 4. 科技神经网络风格
**文件**: `claudeCodePlus_tech.svg`

**设计理念**：
- 六边形科技感外框
- 神经网络节点连接
- 中心脉冲动画
- 体现 AI 深度学习

**特点**：
- 独特的六边形设计
- 神经网络可视化
- 动态脉冲效果
- 强烈的科技感

### 5. Material Design 风格
**文件**: `claudeCodePlus_material.svg`

**设计理念**：
- 遵循 Material Design 规范
- 简洁的几何形状
- 醒目的 Plus 角标
- 中心金色 AI 核心

**特点**：
- 极简设计
- 清晰的品牌标识
- Plus 版本标识
- 适合现代 UI

### 6. 插件市场图标（大尺寸）
**文件**: `pluginIcon.svg`

**设计理念**：
- 40x40 高清版本
- 增强的视觉效果
- 动态星光动画
- 适合插件市场展示

**特点**：
- 高分辨率
- 丰富的细节
- 动画效果
- 专业展示

## 如何更换图标

### 更换工具窗口图标

编辑 `plugin.xml`：

```xml
<toolWindow id="ClaudeCodePlus" 
            displayName="Claude Code Plus"
            anchor="right"
            icon="/icons/claudeCodePlus.svg"  <!-- 更换这里的文件名 -->
            factoryClass="..."/>
```

可选图标：
- `claudeCodePlus.svg` - 默认渐变风格
- `claudeCodePlus_alt.svg` - 代码编辑器风格
- `claudeCodePlus_tech.svg` - 科技神经网络风格
- `claudeCodePlus_material.svg` - Material Design 风格

### 更换插件主图标

```xml
<idea-plugin>
    <icon>/icons/pluginIcon.svg</icon>  <!-- 插件主图标 -->
    ...
</idea-plugin>
```

## 图标使用场景

| 场景 | 推荐图标 | 原因 |
|------|---------|------|
| 工具窗口 | `claudeCodePlus.svg` | 清晰度高，易识别 |
| 插件市场 | `pluginIcon.svg` | 大尺寸，细节丰富 |
| 暗色主题 | `claudeCodePlus_dark.svg` | 优化的暗色配色 |
| 极简风格 | `claudeCodePlus_material.svg` | 简洁现代 |
| 科技项目 | `claudeCodePlus_tech.svg` | 强烈科技感 |

## 图标技术规格

### SVG 优势
- **矢量格式**：任意缩放不失真
- **小文件**：每个图标 < 2KB
- **动画支持**：SVG 原生动画
- **主题适配**：易于调整颜色

### 尺寸规范
- **工具窗口**：16x16 像素
- **插件图标**：40x40 像素
- **视网膜屏**：自动 2x 缩放

### 颜色方案
```
主色调：#4C9AFF (Claude 蓝)
次色调：#0066CC (深蓝)
强调色：#FFD700 (金色)
暗色主题：#5A9FFF (浅蓝)
```

## 自定义图标

如果您想创建自己的图标：

1. **尺寸要求**：
   - 工具窗口：16x16px
   - 插件图标：40x40px

2. **格式要求**：
   - SVG 格式（推荐）
   - PNG 格式（备选）

3. **设计建议**：
   - 保持简洁清晰
   - 考虑暗色/亮色主题
   - 避免过多细节
   - 使用品牌色调

4. **命名规范**：
   - 主图标：`claudeCodePlus.svg`
   - 暗色版：`claudeCodePlus_dark.svg`
   - 插件图标：`pluginIcon.svg`

## 图标预览

由于 Markdown 限制，无法直接显示 SVG。请在以下位置查看图标：

```
jetbrains-plugin/
└── src/main/resources/icons/
    ├── claudeCodePlus.svg         # 主设计
    ├── claudeCodePlus_dark.svg    # 暗色版本
    ├── claudeCodePlus_alt.svg     # 代码风格
    ├── claudeCodePlus_tech.svg    # 科技风格
    ├── claudeCodePlus_material.svg # Material风格
    └── pluginIcon.svg             # 插件市场图标
```

## 反馈和建议

如果您对图标设计有任何建议或想法，欢迎提出！我们可以：
- 调整颜色方案
- 修改设计元素
- 创建新的变体
- 添加更多动画效果