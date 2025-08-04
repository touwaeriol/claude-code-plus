import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TestWatchService {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 文件监听功能测试 ===");
        
        // 测试会话
        String sessionId = UUID.randomUUID().toString();
        String sessionDir = "C:\\Users\\16790\\.claude\\projects\\C--Users-16790-IdeaProjects-claude-code-plus-desktop";
        String sessionFile = sessionDir + "\\" + sessionId + ".jsonl";
        
        System.out.println("会话ID: " + sessionId);
        System.out.println("会话文件: " + sessionFile);
        
        // 确保目录存在
        new File(sessionDir).mkdirs();
        
        // 创建监听服务
        Path watchPath = Paths.get(sessionDir);
        WatchService watchService = FileSystems.getDefault().newWatchService();
        watchPath.register(watchService, 
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY);
        
        System.out.println("\n✅ 已启动文件监听服务");
        
        // 启动监听线程
        Thread watchThread = new Thread(() -> {
            System.out.println("🎧 监听线程已启动...");
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.poll(100, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            String fileName = event.context().toString();
                            if (fileName.equals(sessionId + ".jsonl")) {
                                System.out.println("\n📨 检测到文件变化: " + event.kind());
                                // 读取文件内容
                                try {
                                    System.out.println("文件内容:");
                                    Files.lines(Paths.get(sessionFile))
                                        .forEach(line -> System.out.println("  > " + line.substring(0, Math.min(line.length(), 100)) + "..."));
                                } catch (Exception e) {
                                    System.out.println("读取文件失败: " + e.getMessage());
                                }
                            }
                        }
                        key.reset();
                    }
                }
            } catch (Exception e) {
                System.out.println("监听错误: " + e.getMessage());
            }
        });
        watchThread.start();
        
        // 等待监听启动
        Thread.sleep(1000);
        
        // 创建并写入初始内容
        System.out.println("\n📝 创建会话文件...");
        try (FileWriter writer = new FileWriter(sessionFile)) {
            writer.write("{\"type\":\"user\",\"message\":{\"content\":\"初始消息\"},\"timestamp\":\"" + System.currentTimeMillis() + "\"}\n");
        }
        
        Thread.sleep(2000);
        
        // 模拟实时更新
        System.out.println("\n📝 模拟实时消息更新...");
        for (int i = 1; i <= 3; i++) {
            Thread.sleep(2000);
            
            System.out.println("\n✍️ 追加消息 " + i + "...");
            try (FileWriter writer = new FileWriter(sessionFile, true)) {
                String message = String.format(
                    "{\"type\":\"%s\",\"message\":{\"content\":\"测试消息 %d\"},\"timestamp\":\"%d\"}\n",
                    i % 2 == 0 ? "assistant" : "user", i, System.currentTimeMillis()
                );
                writer.write(message);
            }
        }
        
        // 等待最后的处理
        Thread.sleep(2000);
        
        System.out.println("\n🏁 测试完成！");
        
        // 清理
        watchThread.interrupt();
        watchService.close();
        new File(sessionFile).delete();
        System.out.println("✅ 已清理测试文件");
    }
}