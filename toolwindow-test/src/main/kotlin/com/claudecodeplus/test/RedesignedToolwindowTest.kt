package com.claudecodeplus.test

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.claudecodeplus.sdk.ClaudeCliWrapper
import com.claudecodeplus.ui.models.*
import com.claudecodeplus.ui.redesign.EnhancedConversationView
import com.claudecodeplus.ui.services.ContextProvider
import com.claudecodeplus.ui.services.ProjectService
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.nio.file.Paths

fun main() = application {
    IntUiTheme {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        
        Window(
            onCloseRequest = ::exitApplication,
            title = "Claude Code Plus - Redesigned Toolwindow Test",
            state = windowState
        ) {
            RedesignedToolwindowTestContent()
        }
    }
}

@Composable
fun RedesignedToolwindowTestContent() {
    // Mock project service
    val mockProjectService = remember {
        object : ProjectService {
            override fun getProjectPath(): String = "/test/project"
            
            override fun getProjectName(): String = "Test Project"
            
            override fun openFile(filePath: String, lineNumber: Int?) {
                println("Opening file: $filePath at line $lineNumber")
            }
            
            override fun showSettings(settingsId: String?) {
                println("Showing settings: $settingsId")
            }
        }
    }
    
    // Mock context provider
    val mockContextProvider = remember { MockContextProvider() }
    
    // Mock CLI wrapper
    val mockCliWrapper = remember {
        ClaudeCliWrapper().apply {
            // In test mode, we won't actually send messages
        }
    }
    
    EnhancedConversationView(
        projectService = mockProjectService,
        contextProvider = mockContextProvider,
        cliWrapper = mockCliWrapper,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Mock 实现的 ContextProvider
 */
class MockContextProvider : ContextProvider {
    override suspend fun searchFiles(query: String): List<FileContext> = 
        listOf(
            FileContext(
                path = "src/main/kotlin/Example.kt",
                name = "Example.kt",
                extension = "kt",
                size = 1024,
                lastModified = System.currentTimeMillis(),
                preview = "fun calculateSum(a: Int, b: Int) = a + b"
            ),
            FileContext(
                path = "src/test/kotlin/ExampleTest.kt",
                name = "ExampleTest.kt",
                extension = "kt",
                size = 2048,
                lastModified = System.currentTimeMillis() - 86400000,
                preview = "@Test fun testCalculateSum() { assertEquals(5, calculateSum(2, 3)) }"
            )
        ).filter { it.name.contains(query, ignoreCase = true) }
    
    override suspend fun searchSymbols(query: String): List<SymbolContext> = 
        listOf(
            SymbolContext(
                name = "calculateSum",
                type = SymbolType.FUNCTION,
                file = "src/main/kotlin/Example.kt",
                line = 10,
                signature = "fun calculateSum(a: Int, b: Int): Int"
            ),
            SymbolContext(
                name = "Calculator",
                type = SymbolType.CLASS,
                file = "src/main/kotlin/Calculator.kt",
                line = 5
            )
        ).filter { it.name.contains(query, ignoreCase = true) }
    
    override suspend fun getRecentFiles(limit: Int): List<FileContext> = 
        searchFiles("").take(limit)
    
    override suspend fun getTerminalOutput(lines: Int) = 
        TerminalContext(
            output = """
                $ ./gradlew build
                > Task :compileKotlin UP-TO-DATE
                > Task :compileJava NO-SOURCE
                > Task :processResources UP-TO-DATE
                > Task :classes UP-TO-DATE
                > Task :jar UP-TO-DATE
                > Task :assemble UP-TO-DATE
                > Task :test UP-TO-DATE
                > Task :check UP-TO-DATE
                > Task :build UP-TO-DATE
                
                BUILD SUCCESSFUL in 1s
            """.trimIndent(),
            timestamp = System.currentTimeMillis(),
            hasErrors = false
        )
    
    override suspend fun getProblems(filter: ProblemSeverity?) = 
        listOf(
            Problem(
                file = "src/main/kotlin/Example.kt",
                line = 15,
                column = 12,
                severity = ProblemSeverity.WARNING,
                message = "Parameter 'unused' is never used"
            )
        ).filter { filter == null || it.severity == filter }
    
    override suspend fun getGitInfo(type: GitContextType) = 
        when (type) {
            GitContextType.STATUS -> GitContext(type, """
                On branch main
                Your branch is up to date with 'origin/main'.
                
                Changes not staged for commit:
                  modified:   src/main/kotlin/Example.kt
                  
                no changes added to commit
            """.trimIndent())
            GitContextType.DIFF -> GitContext(type, """
                diff --git a/src/main/kotlin/Example.kt b/src/main/kotlin/Example.kt
                index 1234567..abcdefg 100644
                --- a/src/main/kotlin/Example.kt
                +++ b/src/main/kotlin/Example.kt
                @@ -10,7 +10,7 @@
                 fun calculateSum(a: Int, b: Int): Int {
                -    return a + b
                +    return a + b  // Sum two integers
                 }
            """.trimIndent())
            GitContextType.COMMITS -> GitContext(type, """
                commit abc123 (HEAD -> main)
                Author: Test User <test@example.com>
                Date:   Mon Dec 20 10:00:00 2024 +0800
                
                    Add calculateSum function
                    
                commit def456
                Author: Test User <test@example.com>
                Date:   Sun Dec 19 15:30:00 2024 +0800
                
                    Initial commit
            """.trimIndent())
            GitContextType.STAGED -> GitContext(type, """
                Changes to be committed:
                  modified:   src/main/kotlin/Example.kt
                  new file:   src/test/kotlin/NewTest.kt
            """.trimIndent())
            GitContextType.BRANCHES -> GitContext(type, """
                * main
                  feature/new-feature
                  bugfix/fix-issue-123
            """.trimIndent())
        }
    
    override suspend fun getFolderInfo(path: String) = 
        FolderContext(
            path = path,
            fileCount = 5,
            folderCount = 2,
            totalSize = 10240,
            files = listOf(
                FileContext(
                    path = "$path/Example.kt",
                    name = "Example.kt",
                    extension = "kt",
                    size = 1024,
                    lastModified = System.currentTimeMillis()
                )
            )
        )
    
    override suspend fun readFileContent(path: String, lines: IntRange?) = 
        """
        package com.example
        
        /**
         * Example class demonstrating basic Kotlin features
         */
        class Example {
            fun calculateSum(a: Int, b: Int): Int {
                return a + b
            }
            
            fun greet(name: String) {
                println("Hello, ${'$'}name!")
            }
        }
        """.trimIndent()
    
    override suspend fun getSymbolDefinition(symbol: String): SymbolContext? = 
        searchSymbols(symbol).firstOrNull()
}