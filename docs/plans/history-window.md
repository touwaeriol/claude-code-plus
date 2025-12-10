## 历史会话加载与窗口化改造 - 进度记录

更新时间：2025-12-10 09:10

### 背景与现状
- 历史会话加载目前直接累加到前端数组，长会话会导致内存和渲染压力；滚动条高度随加载增长，无总数占位。
- 后端已支持 offset/limit 流式读取 JSONL，并返回 replaySeq/historyTotal/isDisplayable 等元数据，不改文件格式。
- 前端使用 `vue-virtual-scroller` 的 `DynamicScroller`，高度取决于 `items.length`，未知总数时滚动条随加载增长。
- 目标是保留全量历史在存储层，但渲染只用尾部窗口，头/尾批量插入，未读提示/回底按钮正常工作。

### 目标
- 使用双端队列（denque）封装存储，支持头/尾批量插入，尾部窗口导出，减少大数组拷贝。
- 前端渲染使用窗口（核心 600 + 上下各 200 预留，总约 1000 条），存储层保留全量。
- 滚动到顶部触发历史分页；新消息到达不在底部时显示未读计数/“回到底部”按钮。
- 继续使用 vue-virtual-scroller，滚动条随已加载数量增长（未知总数时）。

### 待办与状态
- [x] 引入 `denque@latest` 依赖并封装存储类（pushBatch/prependBatch/getWindow，去重可选）。
- [x] 将 sessionStore/useSessionTab 消息管线切到新存储，displayItems 改为窗口导出。（进展：useSessionMessages 批量插入统一走窗口存储，loadHistory 使用批量前/尾插）
- [ ] 历史分页：顶部触发 offset/limit 加载，前插到存储后刷新窗口。（进展：首次加载探测 total，自动拉取尾页；MessageList 首屏默认滚动到底部）
- [ ] 新消息：尾插；在底部则自动滚底，否则未读计数增加。
- [ ] UI：回到底部按钮在非生成时也可用；有新消息时显示计数。
- [ ] 性能验证：窗口长度稳定，滚动流畅；必要的单测/手测通过。

### 备注
- 窗口策略：核心 600 + 上下各 200 预留，总约 1000 条；可配置。单块大小由 denque 内部循环缓冲处理。
- 已有后端 offset/limit + replaySeq/total 元数据保持不变。
- 当前运行：后端 `StandaloneServerKt` 启动在 8765（PID 24260），前端 `dev` 在 5174（PID 9544）。

### 自主测试步骤
- 后端：在 IDE 启动 `com.asakii.server.StandaloneServerKt`（端口 8765），观察 `.log/server.log`、`.log/sdk.log`、`.log/ws.log`。
- 前端：在 IDE 运行 dev 任务（端口 5174），浏览器访问 5174。
- 端口被占用时停止占用的进程
- 使用mcp打开浏览器访问网页即可测试
- 测试用例：
  1. 选择历史会话 → 顶部滚动触发分页 → 窗口更新正常，无重复。
  2. 新消息流入：不在底部时出现未读计数，点击回到底部按钮滚动到底部并清零计数。
  3. 窗口长度保持在配置值附近（默认 800），滚动流畅，无明显卡顿。
  4. 在底部时新消息自动滚动；未知总数时滚动条随加载增长正常。

### 使用 MCP 启动 IDEA 运行配置
- 可用运行配置列表（MCP 查询）：
  - `StandaloneServerKt`（后端，8765，工作目录项目根）
  - `StandaloneServer`（后端，ai-agent-server 下）
  - `dev`（npm 前端 5174）
  - 其他 gradle/npm 配置如需可选。
- 在 MCP 环境下执行方式：调用 `mcp__jetbrains__execute_run_configuration`，参数 `configurationName` 设为上述名称，`projectPath` 设为项目根路径。
- 示例：启动后端
  - `configurationName`: `StandaloneServerKt`
  - `projectPath`: 项目根（例如 `C:\\Users\\...\\claude-code-plus`）
- 示例：启动前端
  - `configurationName`: `dev`
  - `projectPath`: 同项目根

### 具体落地点（前端）
- 依赖：在 `frontend/package.json` 增加 `denque`（最新稳定版）。
- 存储封装：新增 `frontend/src/utils/ChunkedMessageStore.ts`（命名可调整），API：`pushBatch`, `prependBatch`, `getWindow(windowSize)`，去重可选。（已完成）
- 集成：
  - `useSessionTab.ts`：历史回放/新消息接收写入存储，再通过 `getWindow` 导出窗口（默认 800，可配置常量）。
  - `sessionStore.ts`：resume/历史分页触发 loadHistory 后前插到存储再刷新窗口；新消息尾插。
- UI：
  - `MessageList.vue`：回到底部按钮在非生成时也可用；未读计数逻辑：不在底部且有新消息时显示数字，否则只显示按钮。
  - 滚到顶部触发分页：监听 scrollTop 阈值触发历史加载（offset/limit）。
- 窗口策略：窗口溢出时重建一次窗口数组（尾部窗口），默认大小 800；批量插入后统一刷新窗口，避免每条重建。
