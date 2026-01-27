/**
 * agent_run_to_background 补丁 v7
 *
 * 添加 agent_run_to_background 和 agents_run_all_to_background 控制命令，
 * 允许 SDK 将运行中的 Agent（子代理）移到后台。
 *
 * v7 变更（适配 CLI 2.1.3，支持批量后台）：
 * - 新增 agents_run_all_to_background 命令：批量将所有运行中的 agents 移到后台
 * - 保留 agent_run_to_background 命令：单个 agent 后台化
 * - 适配 CLI 2.1.3 的变量名变化（fG1 替代 v71）
 *
 * v6 变更（适配 CLI 2.0.77）：
 * - CLI 2.0.77 已内置 background resolver Map 管理机制
 * - 不再需要手动注册 resolver 到 Map（CLI 原生: mapVar.set(agentId, resolver)）
 * - 不再需要 finally 清理（CLI 原生: mapVar.delete(agentId)）
 * - 只需添加控制命令处理，调用内置 Map 的 resolver
 *
 * 实现原理：
 * - Step 1: 找到内置的 background resolvers Map 变量
 *   - 方法：查找函数参数解构中有 agentId 的函数，其中包含 Promise 和 Map.set 调用
 *   - CLI 2.1.3 模式: function M42({agentId:A, ...}) { ... fG1.set(A, I) ... }
 * - Step 2: 在控制请求处理中，添加控制命令处理
 *
 * 支持的控制命令：
 * 1. agent_run_to_background: { target_id?: string }
 *    - 有 target_id: 将指定 agent 移到后台
 *    - 无 target_id: 将第一个运行中的 agent 移到后台
 *
 * 2. agents_run_all_to_background: {}
 *    - 将所有运行中的 agents 批量移到后台
 *    - 返回被后台化的 agent 数量
 */

module.exports = {
  id: 'agent_run_to_background',
  description: 'Add agent_run_to_background control command for Task tool background execution (v6 for CLI 2.0.77+)',
  priority: 100,
  required: false,  // CLI 2.1.4+ changed internal structure, 007-run-to-background provides unified fallback
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let step1Done = false;  // 找到内置 Map 变量
    let step2Done = false;  // 控制命令处理

    // 记录找到的变量名
    let mapVarName = null;

    // ========================================
    // Step 1: 找到内置的 background resolvers Map 变量
    // ========================================
    // CLI 2.1.3 模式:
    // function M42({agentId:A, ...}) {
    //   let I, W = new Promise((K) => { I = K });
    //   return fG1.set(A, I), rL(X, Z), {taskId: A, backgroundSignal: W}
    // }
    //
    // 特征：
    // 1. 函数参数解构中有 agentId: localVar
    // 2. 函数体中有 new Promise 和 resolver 赋值
    // 3. 函数体中有 MapVar.set(localVar, resolver)

    traverse(ast, {
      FunctionDeclaration(path) {
        if (step1Done) return;

        // 检查参数是否有解构 {agentId: ...}
        const params = path.node.params;
        if (params.length !== 1) return;

        const param = params[0];
        if (!t.isObjectPattern(param)) return;

        // 查找 agentId: X 的属性
        let agentIdLocalName = null;
        for (const prop of param.properties) {
          if (!t.isObjectProperty(prop)) continue;
          const keyName = t.isIdentifier(prop.key) ? prop.key.name : null;
          if (keyName === 'agentId' && t.isIdentifier(prop.value)) {
            agentIdLocalName = prop.value.name;
            break;
          }
        }

        if (!agentIdLocalName) return;

        // 检查函数体中是否有 new Promise 和 Map.set
        let hasPromise = false;
        let hasResolverAssign = false;
        let foundMapVar = null;

        path.traverse({
          NewExpression(newPath) {
            if (t.isIdentifier(newPath.node.callee) && newPath.node.callee.name === 'Promise') {
              hasPromise = true;

              // 检查 Promise 回调中的 resolver 赋值
              const arg = newPath.node.arguments[0];
              if (arg && (t.isArrowFunctionExpression(arg) || t.isFunctionExpression(arg))) {
                const body = arg.body;
                if (t.isBlockStatement(body)) {
                  for (const stmt of body.body) {
                    if (t.isExpressionStatement(stmt) && t.isAssignmentExpression(stmt.expression)) {
                      hasResolverAssign = true;
                    }
                  }
                }
              }
            }
          },
          CallExpression(callPath) {
            if (foundMapVar) return;

            const callee = callPath.node.callee;
            if (!t.isMemberExpression(callee)) return;
            if (!t.isIdentifier(callee.property) || callee.property.name !== 'set') return;
            if (!t.isIdentifier(callee.object)) return;

            // 检查第一个参数是否是 agentId 的本地变量
            const args = callPath.node.arguments;
            if (args.length >= 2 && t.isIdentifier(args[0]) && args[0].name === agentIdLocalName) {
              foundMapVar = callee.object.name;
            }
          }
        });

        if (hasPromise && hasResolverAssign && foundMapVar) {
          mapVarName = foundMapVar;
          step1Done = true;
          const funcName = path.node.id?.name || '(anonymous)';
          details.push(`找到内置 background resolvers Map: "${mapVarName}" (在函数 ${funcName} 中，agentId 本地名: ${agentIdLocalName})`);
        }
      }
    });

    // 如果没找到，报错
    if (!step1Done) {
      return {
        success: false,
        reason: '未找到内置 background resolvers Map（需要包含 agentId 解构参数的函数）'
      };
    }

    // ========================================
    // Step 2: 添加控制命令处理
    // ========================================
    // 查找 if (*.request.subtype === "interrupt") 模式
    // 在它前面添加 agent_run_to_background 和 agents_run_all_to_background 处理

    traverse(ast, {
      IfStatement(path) {
        if (step2Done) return;

        const test = path.node.test;

        // 检查是否是 *.request.subtype === "interrupt"
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;

        const left = test.left;
        if (!t.isMemberExpression(left)) return;
        if (!t.isIdentifier(left.property) || left.property.name !== 'subtype') return;

        const obj = left.object;
        if (!t.isMemberExpression(obj)) return;
        if (!t.isIdentifier(obj.property) || obj.property.name !== 'request') return;

        if (!t.isStringLiteral(test.right) || test.right.value !== 'interrupt') return;

        // 找到了! 获取变量名
        const requestVar = obj.object;

        // 找出响应函数名
        let responderName = 's';
        if (t.isBlockStatement(path.node.consequent)) {
          for (const stmt of path.node.consequent.body) {
            if (t.isExpressionStatement(stmt) && t.isCallExpression(stmt.expression)) {
              const callee = stmt.expression.callee;
              if (t.isIdentifier(callee)) {
                responderName = callee.name;
                break;
              }
            }
          }
        }

        // ========================================
        // 处理代码 1: agents_run_all_to_background (批量后台)
        // ========================================
        const allToBackgroundCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('agents_run_all_to_background')
        );

        const allToBackgroundCode = `
          var __resolvers = ${mapVarName};
          var __count = 0;
          var __backgroundedIds = [];

          if (__resolvers && __resolvers.size > 0) {
            // 批量调用所有 resolver
            __resolvers.forEach(function(__resolver, __agentId) {
              try {
                __resolver();
                __backgroundedIds.push(__agentId);
                __count++;
              } catch (e) {
                console.error("Background resolver error for " + __agentId + ":", e);
              }
            });
            // 清空 Map
            __resolvers.clear();
          }

          // 响应包含后台化的数量和 ID 列表
          ${responderName}({
            ...${requestVar.name},
            response: {
              subtype: "success",
              request_id: ${requestVar.name}.request_id,
              response: {
                count: __count,
                backgrounded_ids: __backgroundedIds
              }
            }
          });
        `;

        // ========================================
        // 处理代码 2: agent_run_to_background (单个后台)
        // ========================================
        const singleToBackgroundCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('agent_run_to_background')
        );

        const singleToBackgroundCode = `
          ${responderName}(${requestVar.name});
          var __targetId = ${requestVar.name}.request.target_id;
          var __resolvers = ${mapVarName};
          var __resolver = null;
          var __backgroundedId = null;

          if (__targetId && __resolvers) {
            // 有 target_id: 从 Map 获取指定 resolver
            __resolver = __resolvers.get(__targetId);
            if (__resolver) {
              __resolver();
              __resolvers.delete(__targetId);
              __backgroundedId = __targetId;
            }
          } else if (__resolvers && __resolvers.size > 0) {
            // 无 target_id 但 Map 非空: 取第一个 resolver（支持多代理逐个后台）
            var __firstKey = __resolvers.keys().next().value;
            __resolver = __resolvers.get(__firstKey);
            if (__resolver) {
              __resolver();
              __resolvers.delete(__firstKey);
              __backgroundedId = __firstKey;
            }
          }
        `;

        // 解析代码字符串为 AST
        const parser = require('@babel/parser');
        
        const allToBackgroundAst = parser.parse(allToBackgroundCode, {
          sourceType: 'script',
          allowReturnOutsideFunction: true
        });
        const allToBackgroundBlock = t.blockStatement(allToBackgroundAst.program.body);

        const singleToBackgroundAst = parser.parse(singleToBackgroundCode, {
          sourceType: 'script',
          allowReturnOutsideFunction: true
        });
        const singleToBackgroundBlock = t.blockStatement(singleToBackgroundAst.program.body);

        // 创建嵌套的 if-else 链:
        // if (subtype === "agents_run_all_to_background") { ... }
        // else if (subtype === "agent_run_to_background") { ... }
        // else if (subtype === "interrupt") { ... } (原有代码)

        const singleToBackgroundIf = t.ifStatement(
          singleToBackgroundCondition,
          singleToBackgroundBlock,
          path.node  // 原有的 interrupt 处理
        );

        const allToBackgroundIf = t.ifStatement(
          allToBackgroundCondition,
          allToBackgroundBlock,
          singleToBackgroundIf
        );

        path.replaceWith(allToBackgroundIf);
        step2Done = true;
        details.push(`添加了 agent_run_to_background 控制命令处理 (使用内置 Map "${mapVarName}")`);
        details.push(`添加了 agents_run_all_to_background 批量后台控制命令`);
        path.stop();
      }
    });

    // 检查结果
    if (step2Done) {
      return {
        success: true,
        details
      };
    }

    return {
      success: false,
      reason: '未找到控制请求处理代码 (if ... === "interrupt")'
    };
  }
};
