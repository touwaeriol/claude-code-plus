package com.claudecodeplus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.Project
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 项目选择器组件
 * 用于让用户选择要打开的项目
 */
@Composable
fun ProjectSelector(
    projects: List<Project>,
    onProjectSelected: (Project) -> Unit,
    onBrowseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题
        Text(
            "选择项目",
            style = JewelTheme.defaultTextStyle,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        if (projects.isEmpty()) {
            // 没有项目时显示空状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "未找到任何 Claude 项目",
                    style = JewelTheme.defaultTextStyle,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    "您可以浏览文件夹来选择一个新项目",
                    style = JewelTheme.defaultTextStyle,
                    color = JewelTheme.globalColors.text.disabled,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                DefaultButton(
                    onClick = onBrowseFolder
                ) {
                    Text("浏览文件夹")
                }
            }
        } else {
            // 项目列表
            Text(
                "选择一个现有项目或浏览文件夹创建新项目",
                style = JewelTheme.defaultTextStyle,
                color = JewelTheme.globalColors.text.disabled,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(projects) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onProjectSelected(project) }
                    )
                }
            }
            
            // 浏览文件夹按钮
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = onBrowseFolder
                ) {
                    Text("浏览文件夹")
                }
            }
        }
    }
}

/**
 * 项目卡片组件
 */
@Composable
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = project.name,
                style = JewelTheme.defaultTextStyle,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = project.path,
                style = JewelTheme.defaultTextStyle,
                color = JewelTheme.globalColors.text.disabled,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}