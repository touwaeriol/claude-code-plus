package com.claudecodeplus.ui.jewel.components.context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Web URL上下文选择器
 * 第二步：输入和验证Web URL
 */
@Composable
fun WebContextSelector(
    url: String,
    config: ContextSelectorConfig,
    searchService: ContextSearchService,
    onUrlChange: (String) -> Unit,
    onWebSelected: (WebContextItem) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isValidUrl by remember { mutableStateOf(false) }
    var webInfo by remember { mutableStateOf<WebContextItem?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    
    // URL验证
    LaunchedEffect(url) {
        if (url.isNotEmpty()) {
            isValidUrl = searchService.validateUrl(url)
            
            // 如果URL有效，尝试获取页面信息
            if (isValidUrl) {
                isLoadingInfo = true
                try {
                    val info = searchService.getWebInfo(url)
                    webInfo = info ?: WebContextItem(url = url)
                } catch (e: Exception) {
                    webInfo = WebContextItem(url = url)
                } finally {
                    isLoadingInfo = false
                }
            } else {
                webInfo = null
            }
        } else {
            isValidUrl = false
            webInfo = null
        }
    }
    
    // 自动聚焦到输入框
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 头部：返回按钮和标题
        WebUrlHeader(
            onBack = onBack,
            onCancel = onCancel,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // URL输入框
        WebUrlInput(
            url = url,
            isValid = isValidUrl,
            onUrlChange = onUrlChange,
            onSubmit = {
                if (isValidUrl && webInfo != null) {
                    onWebSelected(webInfo!!)
                }
            },
            focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // URL预览和验证状态
        WebUrlPreview(
            url = url,
            isValid = isValidUrl,
            webInfo = webInfo,
            isLoading = isLoadingInfo,
            onConfirm = {
                if (isValidUrl && webInfo != null) {
                    onWebSelected(webInfo!!)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        // 示例和帮助
        WebUrlExamples(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Web URL头部组件
 */
@Composable
private fun WebUrlHeader(
    onBack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // 返回按钮
        Text(
            text = "←",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 14.sp,
                color = JewelTheme.globalColors.borders.focused
            ),
            modifier = Modifier
                .clickable { onBack() }
                .padding(end = 8.dp)
        )
        
        // 标题
        Text(
            text = "输入网页链接",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.normal
            )
        )
    }
}

/**
 * Web URL输入组件
 */
@Composable
private fun WebUrlInput(
    url: String,
    isValid: Boolean,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = url,
        onValueChange = onUrlChange,
        singleLine = true,
        textStyle = JewelTheme.defaultTextStyle.copy(
            fontSize = 11.sp,
            color = JewelTheme.globalColors.text.normal
        ),
        cursorBrush = SolidColor(JewelTheme.globalColors.text.normal),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .background(
                        JewelTheme.globalColors.panelBackground,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(6.dp)
            ) {
                if (url.isEmpty()) {
                    Text(
                        "https://example.com",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.disabled
                        )
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter -> {
                            if (isValid) {
                                onSubmit()
                            }
                            true
                        }
                        Key.Escape -> {
                            onUrlChange("")
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    )
}

/**
 * Web URL预览组件
 */
@Composable
private fun WebUrlPreview(
    url: String,
    isValid: Boolean,
    webInfo: WebContextItem?,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 验证状态
        if (url.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // 状态图标
                Text(
                    text = when {
                        isLoading -> "⏳"
                        isValid -> "✅"
                        else -> "❌"
                    },
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp),
                    modifier = Modifier.padding(end = 6.dp)
                )
                
                // 状态文本
                Text(
                    text = when {
                        isLoading -> "获取页面信息中..."
                        isValid -> "有效的URL"
                        else -> "无效的URL格式"
                    },
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = when {
                            isLoading -> JewelTheme.globalColors.text.disabled
                            isValid -> Color(0xFF4CAF50)
                            else -> Color(0xFFF44336)
                        }
                    )
                )
            }
        }
        
        // 页面信息预览
        if (webInfo != null && !isLoading) {
            WebInfoCard(
                webInfo = webInfo,
                onConfirm = onConfirm,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Web信息卡片组件
 */
@Composable
private fun WebInfoCard(
    webInfo: WebContextItem,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onConfirm() }
            .background(
                JewelTheme.globalColors.borders.normal.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 网站图标
            Text(
                text = webInfo.getIcon(),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp),
                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
            )
            
            // 页面信息
            Column(modifier = Modifier.weight(1f)) {
                // 标题
                Text(
                    text = webInfo.getDisplayName(),
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal
                    ),
                    maxLines = 2
                )
                
                // URL
                Text(
                    text = webInfo.url,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 9.sp,
                        color = JewelTheme.globalColors.text.disabled
                    ),
                    maxLines = 1
                )
                
                // 描述
                if (!webInfo.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = webInfo.description,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 9.sp,
                            color = JewelTheme.globalColors.text.disabled
                        ),
                        maxLines = 2
                    )
                }
            }
        }
        
        // 确认按钮提示
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = "点击确认 或 按Enter",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 9.sp,
                    color = JewelTheme.globalColors.borders.focused
                )
            )
        }
    }
}

/**
 * Web URL示例组件
 */
@Composable
private fun WebUrlExamples(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 16.dp)) {
        Text(
            text = "支持的URL格式：",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 10.sp,
                color = JewelTheme.globalColors.text.disabled
            ),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        val examples = listOf(
            "https://docs.oracle.com/javase/8/docs/",
            "https://kotlinlang.org/docs/",
            "https://github.com/JetBrains/compose-jb",
            "file:///path/to/local/file.html"
        )
        
        examples.forEach { example ->
            Text(
                text = "• $example",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 9.sp,
                    color = JewelTheme.globalColors.text.disabled
                ),
                modifier = Modifier.padding(vertical = 1.dp, horizontal = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "提示：Enter确认，Esc清空，← 返回",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 9.sp,
                color = JewelTheme.globalColors.text.disabled
            )
        )
    }
}

 