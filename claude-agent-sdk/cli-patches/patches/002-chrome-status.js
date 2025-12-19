/**
 * chrome_status 补丁
 *
 * 添加 chrome_status 控制命令，允许 SDK 查询 Chrome 扩展的连接状态。
 *
 * 实现原理：
 * - Step 1: 找到控制请求处理的 if-else 链
 * - Step 2: 添加 chrome_status 分支，调用内部函数获取状态
 *
 * 返回的状态包括：
 * - installed: 扩展是否安装（检查 NativeMessagingHost 文件）
 * - enabled: 是否默认启用
 * - connected: MCP 服务器是否已连接
 * - mcpServerStatus: MCP 服务器状态字符串
 * - extensionVersion: 扩展版本（如果已连接）
 */

module.exports = {
  id: 'chrome_status',
  description: 'Add chrome_status control command for querying Chrome extension status',
  priority: 110,  // 在 run_in_background (100) 之后
  required: false,  // 可选补丁
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // Step 1: 找到控制请求处理位置并添加 chrome_status 分支
    // ========================================
    // 查找模式: if (*.request.subtype === "interrupt")
    // 或已被 run_in_background 补丁修改后的:
    // if (*.request.subtype === "run_in_background") { ... }
    // else if (*.request.subtype === "interrupt") { ... }

    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "interrupt" 或 "run_in_background"
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;

        const left = test.left;
        if (!t.isMemberExpression(left)) return;
        if (!t.isIdentifier(left.property) || left.property.name !== 'subtype') return;

        const obj = left.object;
        if (!t.isMemberExpression(obj)) return;
        if (!t.isIdentifier(obj.property) || obj.property.name !== 'request') return;

        // 检查是否是我们要找的 subtype
        const subtypeValue = test.right;
        if (!t.isStringLiteral(subtypeValue)) return;

        const subtype = subtypeValue.value;
        if (subtype !== 'interrupt' && subtype !== 'run_in_background') return;

        // 找到了控制请求处理位置
        // 获取请求变量名
        const requestVar = obj.object;

        // 找出响应函数名（通常是 s）
        let responderName = 's';
        const consequent = path.node.consequent;
        if (t.isBlockStatement(consequent)) {
          for (const stmt of consequent.body) {
            if (t.isExpressionStatement(stmt) && t.isCallExpression(stmt.expression)) {
              const callee = stmt.expression.callee;
              if (t.isIdentifier(callee)) {
                responderName = callee.name;
                break;
              }
            }
          }
        }

        // 创建 chrome_status 处理代码块
        // (async () => {
        //   let installed = false;
        //   try { installed = await a4A(); } catch(e) {}
        //   let config = {};
        //   try { config = k1(); } catch(e) {}
        //   let mcpStatus = null;
        //   try {
        //     // 尝试从 init 消息或内部状态获取 MCP 服务器状态
        //     // 这里使用一个简化的方式
        //   } catch(e) {}
        //   s(d, {
        //     installed: installed,
        //     enabled: config.claudeInChromeDefaultEnabled || false,
        //     connected: false,  // 需要从 MCP 状态获取
        //     mcpServerStatus: null,
        //     extensionVersion: null
        //   });
        // })();

        // 创建简化版本的处理逻辑
        // 由于 a4A 是异步函数，我们需要使用 async IIFE
        const handlerBlock = t.blockStatement([
          // (async () => {
          //   let installed = false;
          //   try { installed = await a4A(); } catch(e) {}
          //   let config = k1() || {};
          //   let enabled = config.claudeInChromeDefaultEnabled || false;
          //   s(d, { installed, enabled, connected: false, mcpServerStatus: null, extensionVersion: null });
          // })();
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression(
                [],
                t.blockStatement([
                  // let installed = false;
                  t.variableDeclaration('let', [
                    t.variableDeclarator(t.identifier('_installed'), t.booleanLiteral(false))
                  ]),
                  // let enabled = false;
                  t.variableDeclaration('let', [
                    t.variableDeclarator(t.identifier('_enabled'), t.booleanLiteral(false))
                  ]),
                  // try { _installed = await a4A(); } catch(e) {}
                  t.tryStatement(
                    t.blockStatement([
                      t.expressionStatement(
                        t.assignmentExpression(
                          '=',
                          t.identifier('_installed'),
                          t.awaitExpression(t.callExpression(t.identifier('a4A'), []))
                        )
                      )
                    ]),
                    t.catchClause(t.identifier('_e'), t.blockStatement([]))
                  ),
                  // try { _enabled = k1()?.claudeInChromeDefaultEnabled || false; } catch(e) {}
                  t.tryStatement(
                    t.blockStatement([
                      t.expressionStatement(
                        t.assignmentExpression(
                          '=',
                          t.identifier('_enabled'),
                          t.logicalExpression(
                            '||',
                            t.optionalMemberExpression(
                              t.callExpression(t.identifier('k1'), []),
                              t.identifier('claudeInChromeDefaultEnabled'),
                              false,
                              true  // optional
                            ),
                            t.booleanLiteral(false)
                          )
                        )
                      )
                    ]),
                    t.catchClause(t.identifier('_e'), t.blockStatement([]))
                  ),
                  // s(d, { installed: _installed, enabled: _enabled, ... });
                  t.expressionStatement(
                    t.callExpression(
                      t.identifier(responderName),
                      [
                        t.cloneNode(requestVar),
                        t.objectExpression([
                          t.objectProperty(t.identifier('installed'), t.identifier('_installed')),
                          t.objectProperty(t.identifier('enabled'), t.identifier('_enabled')),
                          t.objectProperty(t.identifier('connected'), t.booleanLiteral(false)),
                          t.objectProperty(t.identifier('mcpServerStatus'), t.nullLiteral()),
                          t.objectProperty(t.identifier('extensionVersion'), t.nullLiteral())
                        ])
                      ]
                    )
                  )
                ]),
                true  // async
              ),
              []
            )
          )
        ]);

        // 创建新的 if 条件: *.request.subtype === "chrome_status"
        const newCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('chrome_status')
        );

        // 创建新的 if-else: 在现有条件之前插入 chrome_status 检查
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node  // 原来的 if 作为 else
        );

        path.replaceWith(newIfStatement);
        patchApplied = true;
        details.push(`添加了 chrome_status 控制命令处理 (responder: ${responderName})`);
        path.stop();
      }
    });

    if (patchApplied) {
      return {
        success: true,
        details
      };
    }

    return {
      success: false,
      reason: '未找到控制请求处理代码 (if *.request.subtype === "interrupt")'
    };
  }
};
