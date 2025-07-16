package com.claudecodeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import com.claudecodeplus.ui.services.ProjectManager
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text

@Composable
fun ProjectListPanel(
    projectManager: ProjectManager,
    selectedProject: Project?,
    selectedSession: ProjectSession?,
    onProjectSelect: (Project) -> Unit,
    onSessionSelect: (ProjectSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by projectManager.projects.collectAsState()
    val sessions by projectManager.sessions.collectAsState()

    Column(modifier = modifier.width(250.dp).fillMaxHeight().padding(8.dp)) {
        // --- 列表 ---
        LazyColumn {
            items(projects) { project ->
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    // 项目标题
                    Text(
                        text = project.name,
                        fontWeight = FontWeight.Bold,
                        color = if (project.id == selectedProject?.id) Color.Blue else Color.Unspecified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProjectSelect(project) }
                            .padding(4.dp)
                    )

                    Spacer(Modifier.height(4.dp))

                    // 会话列表
                    val projectSessions = sessions[project.id] ?: emptyList()
                    projectSessions.forEach { session ->
                        Text(
                            text = session.name,
                            color = if (session.id == selectedSession?.id) Color.Blue else Color.Unspecified,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .clickable { onSessionSelect(session) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}