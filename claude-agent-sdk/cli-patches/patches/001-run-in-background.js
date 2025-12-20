/**
 * agent_run_to_background 补丁 v5
 *
 * 添加 agent_run_to_background 控制命令，允许 SDK 将运行中的 Agent（子代理）移到后台。
 *
 * v5 变更（从 v4 简化）：
 * - 移除 Bash 后台支持（将通过 JetBrains MCP 自定义 Bash 工具实现）
 * - 重命名命令从 run_in_background 到 agent_run_to_background
 * - 移除 target_type 参数（只支持 Agent）
 * - 保留 target_id 参数用于指定特定 Agent
 *
 * 实现原理：
 * - Step 1: 找到 agentId 变量（用于 Task tool 的 ID）
 * - Step 2: 找到子代理 resolver 函数，改为 Map.set(agentId, resolver)
 * - Step 3: 找到 finally 块，添加 Map.delete(agentId)
 * - Step 4: 在控制请求处理中，添加 agent_run_to_background 命令处理
 */

module.exports = {
  id: 'agent_run_to_background',
  description: 'Add agent_run_to_background control command for Task tool background execution',
  priority: 100,
  required: true,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let step1Done = false;  // agentId 查找
    let step2Done = false;  // resolver 注册到 Map
    let step3Done = false;  // finally 清理
    let step4Done = false;  // 控制命令处理

    // 记录找到的变量名
    let resolverVarName = null;
    let agentIdVarName = null;

    // ========================================
    // Step 1 & 2: 找到 resolver 和 agentId，注册到 Map
    // ========================================
    // 查找模式: s = () => { m(); }
    // 同时查找同一作用域中的 agentId 变量

    traverse(ast, {
      VariableDeclarator(path) {
        if (step2Done) return;

        const init = path.node.init;
        if (!init) return;

        // 查找箭头函数形式: varName = () => { ... }
        if (init.type !== 'ArrowFunctionExpression') return;

        // 检查是否是无参数的箭头函数
        if (init.params.length !== 0) return;

        // 检查函数体是否是块语句
        if (init.body.type !== 'BlockStatement') return;

        // 检查函数体是否只有一个语句：调用另一个函数
        const bodyStmts = init.body.body;
        if (bodyStmts.length !== 1) return;

        const stmt = bodyStmts[0];
        if (stmt.type !== 'ExpressionStatement') return;

        const expr = stmt.expression;
        if (expr.type !== 'CallExpression') return;

        // 检查是否是无参数调用: m()
        if (expr.arguments.length !== 0) return;
        if (expr.callee.type !== 'Identifier') return;

        const calleeName = expr.callee.name;
        const varName = path.node.id?.name;

        // 检查上下文：查找同一作用域中是否有 new Promise 使用这个 callee
        const scope = path.scope;
        const binding = scope.getBinding(calleeName);

        if (!binding) return;

        // 检查这个变量是否被 new Promise 的回调赋值
        let isResolverPattern = false;

        const parent = path.parentPath?.parentPath;
        if (parent && parent.isBlockStatement()) {
          parent.traverse({
            NewExpression(promisePath) {
              if (promisePath.node.callee?.name !== 'Promise') return;

              const promiseArg = promisePath.node.arguments[0];
              if (!promiseArg) return;

              if (promiseArg.type === 'ArrowFunctionExpression') {
                const promiseBody = promiseArg.body;
                if (promiseBody.type === 'BlockStatement') {
                  for (const s of promiseBody.body) {
                    if (s.type === 'ExpressionStatement' &&
                        s.expression.type === 'AssignmentExpression' &&
                        s.expression.left.type === 'Identifier' &&
                        s.expression.left.name === calleeName) {
                      isResolverPattern = true;
                    }
                  }
                }
              }
            }
          });
        }

        if (!isResolverPattern) return;

        // 验证：检查这个变量是否被用于 onBackground
        let usedAsOnBackground = false;
        if (parent) {
          parent.traverse({
            ObjectProperty(propPath) {
              const keyName = propPath.node.key?.name;
              if (keyName === 'onBackground') {
                const value = propPath.node.value;
                if (value.type === 'Identifier' && value.name === varName) {
                  usedAsOnBackground = true;
                }
              }
            }
          });
        }

        if (!usedAsOnBackground) return;

        // 找到了 resolver！记录变量名
        resolverVarName = varName;

        // 在同一作用域中查找 agentId 变量
        // 模式: let j = Z || eBA() 或 let j = Z ? W$(Z) : eBA()
        // 并且后面有 agentId: j 的使用
        if (parent) {
          parent.traverse({
            ObjectProperty(propPath) {
              if (agentIdVarName) return; // 已找到

              const keyName = propPath.node.key?.name || propPath.node.key?.value;
              if (keyName === 'agentId') {
                const value = propPath.node.value;
                if (value.type === 'Identifier') {
                  // 检查这个变量是否在当前作用域中定义
                  const agentBinding = scope.getBinding(value.name);
                  if (agentBinding) {
                    agentIdVarName = value.name;
                    step1Done = true;
                  }
                }
              }
            }
          });
        }

        // 找到了! 在这个声明之后添加 Map 注册代码
        const declarationPath = path.parentPath;
        if (declarationPath && declarationPath.isVariableDeclaration()) {
          // 创建 Map 初始化和注册代码
          // if(!global.__sdkBackgroundResolvers) global.__sdkBackgroundResolvers = new Map();
          // global.__sdkBackgroundResolvers.set(agentId, resolver);

          const initMapStmt = t.ifStatement(
            t.unaryExpression(
              '!',
              t.memberExpression(
                t.identifier('global'),
                t.identifier('__sdkBackgroundResolvers')
              )
            ),
            t.expressionStatement(
              t.assignmentExpression(
                '=',
                t.memberExpression(
                  t.identifier('global'),
                  t.identifier('__sdkBackgroundResolvers')
                ),
                t.newExpression(t.identifier('Map'), [])
              )
            )
          );

          // 如果找到了 agentId，使用它；否则使用一个默认的唯一标识
          const keyExpr = agentIdVarName
            ? t.identifier(agentIdVarName)
            : t.stringLiteral('__latest__');

          const setMapStmt = t.expressionStatement(
            t.callExpression(
              t.memberExpression(
                t.memberExpression(
                  t.identifier('global'),
                  t.identifier('__sdkBackgroundResolvers')
                ),
                t.identifier('set')
              ),
              [keyExpr, t.identifier(varName)]
            )
          );

          // 同时保留旧的全局变量用于向后兼容
          const exposeStmt = t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(
                t.identifier('global'),
                t.identifier('__sdkBackgroundResolver')
              ),
              t.identifier(varName)
            )
          );

          declarationPath.insertAfter(exposeStmt);
          declarationPath.insertAfter(setMapStmt);
          declarationPath.insertAfter(initMapStmt);

          step2Done = true;
          if (agentIdVarName) {
            details.push(`使用 Map 注册 resolver "${varName}" with agentId "${agentIdVarName}"`);
          } else {
            details.push(`使用 Map 注册 resolver "${varName}" (未找到 agentId，使用默认 key)`);
          }
        }
      }
    });

    // ========================================
    // Step 3: 在 finally 块中添加清理代码
    // ========================================
    // 查找模式: finally { ... if(J.setToolJSX)J.setToolJSX(null) ... }
    // 在 setToolJSX(null) 后添加 Map.delete(agentId)

    if (step2Done && agentIdVarName) {
      traverse(ast, {
        TryStatement(path) {
          if (step3Done) return;

          const finalizer = path.node.finalizer;
          if (!finalizer || finalizer.type !== 'BlockStatement') return;

          // 查找 setToolJSX(null) 调用
          let hasSetToolJSXNull = false;
          let setToolJSXStmtIndex = -1;

          for (let i = 0; i < finalizer.body.length; i++) {
            const stmt = finalizer.body[i];
            if (stmt.type === 'IfStatement') {
              // 检查 if(J.setToolJSX) J.setToolJSX(null)
              const consequent = stmt.consequent;
              if (consequent.type === 'ExpressionStatement') {
                const expr = consequent.expression;
                if (expr.type === 'CallExpression' &&
                    expr.callee.type === 'MemberExpression' &&
                    expr.callee.property?.name === 'setToolJSX' &&
                    expr.arguments.length === 1 &&
                    expr.arguments[0].type === 'NullLiteral') {
                  hasSetToolJSXNull = true;
                  setToolJSXStmtIndex = i;
                }
              }
            }
          }

          if (!hasSetToolJSXNull) return;

          // 检查这个 try 块是否在正确的上下文中（有 agentId 变量）
          const scope = path.scope;
          if (!scope.hasBinding(agentIdVarName)) return;

          // 在 setToolJSX(null) 之后添加 Map.delete(agentId)
          // global.__sdkBackgroundResolvers && global.__sdkBackgroundResolvers.delete(agentId);
          const deleteStmt = t.expressionStatement(
            t.logicalExpression(
              '&&',
              t.memberExpression(
                t.identifier('global'),
                t.identifier('__sdkBackgroundResolvers')
              ),
              t.callExpression(
                t.memberExpression(
                  t.memberExpression(
                    t.identifier('global'),
                    t.identifier('__sdkBackgroundResolvers')
                  ),
                  t.identifier('delete')
                ),
                [t.identifier(agentIdVarName)]
              )
            )
          );

          // 插入到 setToolJSX(null) 之后
          finalizer.body.splice(setToolJSXStmtIndex + 1, 0, deleteStmt);
          step3Done = true;
          details.push(`在 finally 块中添加 Map.delete("${agentIdVarName}") 清理代码`);
          path.stop();
        }
      });
    }

    // ========================================
    // Step 4: 添加 agent_run_to_background 控制命令处理
    // ========================================
    // 查找 if (*.request.subtype === "interrupt") 模式
    // 在它前面添加 agent_run_to_background 处理

    traverse(ast, {
      IfStatement(path) {
        if (step4Done) return;

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

        // 创建新的 if 条件: *.request.subtype === "agent_run_to_background"
        const newCondition = t.binaryExpression(
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

        // 创建处理代码块 - 只支持 Agent 后台
        // 逻辑：
        // 1. 发送响应
        // 2. 获取 target_id（可选，用于指定特定 Agent）
        // 3. 如果有 target_id，从 Map 获取对应的 resolver
        // 4. 如果没有 target_id 但 Map 非空，取第一个 resolver（支持多代理逐个后台）
        // 5. 否则回退到兼容模式

        // 生成 IIFE 内部代码字符串，因为需要复杂的逻辑
        // 直接用 AST 构建太复杂，使用 template
        const handlerCode = `
          ${responderName}(${requestVar.name});
          var __targetId = ${requestVar.name}.request.target_id;
          var __resolvers = global.__sdkBackgroundResolvers;
          var __resolver = null;
          var __resolvedId = null;

          if (__targetId && __resolvers) {
            // 有 target_id: 从 Map 获取指定 resolver
            __resolver = __resolvers.get(__targetId);
            __resolvedId = __targetId;
          } else if (__resolvers && __resolvers.size > 0) {
            // 无 target_id 但 Map 非空: 取第一个 resolver（支持多代理逐个后台）
            var __firstKey = __resolvers.keys().next().value;
            __resolver = __resolvers.get(__firstKey);
            __resolvedId = __firstKey;
          }

          if (__resolver) {
            __resolver();
            __resolvers && __resolvers.delete(__resolvedId);
          } else if (global.__sdkBackgroundResolver) {
            // 兼容模式：使用旧的单一 resolver
            global.__sdkBackgroundResolver();
          }
        `;

        // 解析代码字符串为 AST
        const handlerAst = require('@babel/parser').parse(handlerCode, {
          sourceType: 'script',
          allowReturnOutsideFunction: true
        });
        const handlerBlock = t.blockStatement(handlerAst.program.body);

        // 创建新的 if-else
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node
        );

        path.replaceWith(newIfStatement);
        step4Done = true;
        details.push('添加了 agent_run_to_background 控制命令处理 (支持 target_id 参数)');
        path.stop();
      }
    });

    // 检查结果
    if (step2Done && step4Done) {
      // Step 3 (finally 清理) 是可选的
      if (!step3Done) {
        details.push('⚠️ 未找到 finally 块（Map 不会自动清理）');
      }
      return {
        success: true,
        details
      };
    }

    const reasons = [];
    if (!step2Done) reasons.push('未找到子代理 background resolver 定义');
    if (!step4Done) reasons.push('未找到控制请求处理代码');

    return {
      success: false,
      reason: reasons.join('; ')
    };
  }
};
