import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class TestLiveMonitoring {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 实时监听已存在会话测试 ===");
        
        String sessionId = "f1234567-89ab-cdef-0123-456789abcdef";
        String sessionDir = "C:\\Users\\16790\\.claude\\projects\\C--Users-16790-IdeaProjects-claude-code-plus-desktop";
        String sessionFile = sessionDir + "\\" + sessionId + ".jsonl";
        
        System.out.println("监听会话: " + sessionId);
        System.out.println("会话文件: " + sessionFile);
        
        // 记录初始行数
        int initialLines = (int) Files.lines(Paths.get(sessionFile)).count();
        System.out.println("初始行数: " + initialLines);
        
        // 创建监听服务
        Path watchPath = Paths.get(sessionDir);
        WatchService watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        
        System.out.println("\n✅ 已启动文件监听服务");
        System.out.println("🎧 等待文件更新...");
        System.out.println("\n请在另一个终端运行:");
        System.out.println("cd C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop");
        System.out.println("echo \"告诉我一个编程笑话\" | claude --resume " + sessionId);
        
        // 监听30秒
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 30000) {
            WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);
            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    String fileName = event.context().toString();
                    if (fileName.equals(sessionId + ".jsonl")) {
                        System.out.println("\n📨 检测到会话更新！");
                        
                        // 读取新增内容
                        int currentLines = (int) Files.lines(Paths.get(sessionFile)).count();
                        int newLines = currentLines - initialLines;
                        
                        if (newLines > 0) {
                            System.out.println("新增 " + newLines + " 行内容:");
                            Files.lines(Paths.get(sessionFile))
                                .skip(initialLines)
                                .forEach(line -> {
                                    if (line.contains("\"type\":\"user\"")) {
                                        System.out.println("👤 用户消息");
                                    } else if (line.contains("\"type\":\"assistant\"")) {
                                        System.out.println("🤖 助手响应");
                                    } else if (line.contains("tool_use")) {
                                        System.out.println("🔧 工具调用");
                                    }
                                    System.out.println("   内容: " + line.substring(0, Math.min(line.length(), 100)) + "...");
                                });
                            
                            initialLines = currentLines; // 更新基线
                        }
                    }
                }
                key.reset();
            }
        }
        
        System.out.println("\n🏁 监听测试完成！");
        watchService.close();
    }
}