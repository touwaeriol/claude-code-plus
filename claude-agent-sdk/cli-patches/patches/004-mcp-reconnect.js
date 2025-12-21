/**
 * mcp_reconnect 补丁
 *
 * 添加 MCP 服务器重连控制端点，直接调用 CLI 内部的重连逻辑。
 *
 * 请求格式:
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "mcp_reconnect",
 *     server_name: "jetbrains"
 *   }
 * }
 *
 * 响应格式:
 * {
 *   success: boolean,
 *   server_name: string,
 *   status: "connected" | "failed" | "needs-auth" | ...,
 *   tools_count: number,
 *   error: string | null
 * }
 *
 * 内部调用链:
 *   mcp_reconnect -> x2A(serverName, config) -> gm() + hm()
 */

module.exports = {
  id: 'mcp_reconnect',
  description: 'MCP server reconnect control endpoint',
  priority: 120,  // 在 chrome_status (110) 之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // Step 1: 找到 x2A 函数 (MCP 重连核心函数)
    // 特征: async function x2A(A, Q) { ... await gm(A, Q); ... await hm(A, Q); ... }
    // ========================================
    let reconnectFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (reconnectFnName) return;
        if (!path.node.async) return;
        if (path.node.params.length !== 2) return;

        // 检查函数体是否包含 gm 和 hm 调用
        let hasGmCall = false;
        let hasHmCall = false;
        let hasToolsProperty = false;

        path.traverse({
          CallExpression(innerPath) {
            const callee = innerPath.node.callee;
            if (t.isIdentifier(callee)) {
              // 记录调用的函数名（后面会用到）
              if (innerPath.node.arguments.length === 2) {
                // 可能是 gm(A, Q) 或 hm(A, Q) 调用
                hasGmCall = true;
              }
            }
            // 检查 await hm(A, Q) 调用
            if (t.isAwaitExpression(innerPath.parent)) {
              hasHmCall = true;
            }
          },
          ObjectProperty(innerPath) {
            if (t.isIdentifier(innerPath.node.key) && innerPath.node.key.name === 'tools') {
              hasToolsProperty = true;
            }
          }
        });

        // 检查返回值是否包含 client, tools
        let hasClientReturn = false;
        path.traverse({
          ReturnStatement(returnPath) {
            if (t.isObjectExpression(returnPath.node.argument)) {
              const props = returnPath.node.argument.properties;
              for (const prop of props) {
                if (t.isObjectProperty(prop) && t.isIdentifier(prop.key)) {
                  if (prop.key.name === 'client' || prop.key.name === 'tools') {
                    hasClientReturn = true;
                  }
                }
              }
            }
          }
        });

        if (hasClientReturn && hasToolsProperty && path.node.id) {
          reconnectFnName = path.node.id.name;
        }
      },
      VariableDeclarator(path) {
        if (reconnectFnName) return;
        const init = path.node.init;
        if (!t.isArrowFunctionExpression(init) && !t.isFunctionExpression(init)) return;
        if (!init.async) return;
        if (init.params.length !== 2) return;

        // 同样的检查逻辑
        let hasClientReturn = false;
        let hasToolsProperty = false;

        path.traverse({
          ObjectProperty(innerPath) {
            if (t.isIdentifier(innerPath.node.key) && innerPath.node.key.name === 'tools') {
              hasToolsProperty = true;
            }
          },
          ReturnStatement(returnPath) {
            if (t.isObjectExpression(returnPath.node.argument)) {
              const props = returnPath.node.argument.properties;
              for (const prop of props) {
                if (t.isObjectProperty(prop) && t.isIdentifier(prop.key)) {
                  if (prop.key.name === 'client' || prop.key.name === 'tools') {
                    hasClientReturn = true;
                  }
                }
              }
            }
          }
        });

        if (hasClientReturn && hasToolsProperty && t.isIdentifier(path.node.id)) {
          reconnectFnName = path.node.id.name;
        }
      }
    });

    if (reconnectFnName) {
      details.push(`找到重连函数: ${reconnectFnName}`);
      context.foundVariables.reconnectFn = reconnectFnName;
    } else {
      details.push('警告: 未找到重连函数 x2A，将使用简化逻辑');
    }

    // ========================================
    // Step 2: 找到 MCP 配置变量 (y.configs 或类似)
    // 从 mcp_set_servers 处理中学习配置结构
    // ========================================

    // ========================================
    // Step 3: 找到控制请求处理位置并添加 mcp_reconnect 分支
    // ========================================
    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "mcp_status" 或 "mcp_set_servers"
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;

        const left = test.left;
        if (!t.isMemberExpression(left)) return;
        if (!t.isIdentifier(left.property) || left.property.name !== 'subtype') return;

        const obj = left.object;
        if (!t.isMemberExpression(obj)) return;
        if (!t.isIdentifier(obj.property) || obj.property.name !== 'request') return;

        const subtypeValue = test.right;
        if (!t.isStringLiteral(subtypeValue)) return;

        const subtype = subtypeValue.value;
        // 在 mcp_set_servers 附近添加
        if (subtype !== 'mcp_set_servers' && subtype !== 'mcp_status') return;

        // 找到了控制请求处理位置
        const requestVar = obj.object;

        // 找出响应函数名
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

        // 构建 mcp_reconnect 处理逻辑
        const statements = [];

        // const _serverName = d.request.server_name;
        statements.push(t.variableDeclaration('const', [
          t.variableDeclarator(
            t.identifier('_serverName'),
            t.memberExpression(
              t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
              t.identifier('server_name')
            )
          )
        ]));

        // let _result = { success: false, status: null, tools_count: 0, error: null };
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_result'),
            t.objectExpression([
              t.objectProperty(t.identifier('success'), t.booleanLiteral(false)),
              t.objectProperty(t.identifier('status'), t.nullLiteral()),
              t.objectProperty(t.identifier('tools_count'), t.numericLiteral(0)),
              t.objectProperty(t.identifier('error'), t.nullLiteral())
            ])
          )
        ]));

        // try { ... } catch(e) { _result.error = e.message; }
        const tryBlock = [];

        // 查找服务器配置
        // const _allConfigs = y?.configs || {};
        tryBlock.push(t.variableDeclaration('const', [
          t.variableDeclarator(
            t.identifier('_allConfigs'),
            t.logicalExpression(
              '||',
              t.optionalMemberExpression(
                t.identifier('y'),
                t.identifier('configs'),
                false,
                true
              ),
              t.objectExpression([])
            )
          )
        ]));

        // const _serverConfig = _allConfigs[_serverName];
        tryBlock.push(t.variableDeclaration('const', [
          t.variableDeclarator(
            t.identifier('_serverConfig'),
            t.memberExpression(
              t.identifier('_allConfigs'),
              t.identifier('_serverName'),
              true  // computed
            )
          )
        ]));

        // if (!_serverConfig) { _result.error = "Server not found"; } else { ... }
        const reconnectLogic = [];

        if (reconnectFnName) {
          // const _reconnectResult = await x2A(_serverName, _serverConfig);
          reconnectLogic.push(t.variableDeclaration('const', [
            t.variableDeclarator(
              t.identifier('_reconnectResult'),
              t.awaitExpression(
                t.callExpression(
                  t.identifier(reconnectFnName),
                  [t.identifier('_serverName'), t.identifier('_serverConfig')]
                )
              )
            )
          ]));

          // _result.status = _reconnectResult.client?.type || "failed";
          reconnectLogic.push(t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(t.identifier('_result'), t.identifier('status')),
              t.logicalExpression(
                '||',
                t.optionalMemberExpression(
                  t.memberExpression(t.identifier('_reconnectResult'), t.identifier('client')),
                  t.identifier('type'),
                  false,
                  true
                ),
                t.stringLiteral('failed')
              )
            )
          ));

          // _result.success = _result.status === "connected";
          reconnectLogic.push(t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(t.identifier('_result'), t.identifier('success')),
              t.binaryExpression(
                '===',
                t.memberExpression(t.identifier('_result'), t.identifier('status')),
                t.stringLiteral('connected')
              )
            )
          ));

          // _result.tools_count = _reconnectResult.tools?.length || 0;
          reconnectLogic.push(t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(t.identifier('_result'), t.identifier('tools_count')),
              t.logicalExpression(
                '||',
                t.optionalMemberExpression(
                  t.memberExpression(t.identifier('_reconnectResult'), t.identifier('tools')),
                  t.identifier('length'),
                  false,
                  true
                ),
                t.numericLiteral(0)
              )
            )
          ));

          // 更新 MCP 状态 (更新 y.clients)
          // const _existingIdx = y.clients?.findIndex(c => c.name === _serverName);
          reconnectLogic.push(t.variableDeclaration('const', [
            t.variableDeclarator(
              t.identifier('_existingIdx'),
              t.optionalCallExpression(
                t.optionalMemberExpression(
                  t.memberExpression(t.identifier('y'), t.identifier('clients')),
                  t.identifier('findIndex'),
                  false,
                  true
                ),
                [
                  t.arrowFunctionExpression(
                    [t.identifier('_c')],
                    t.binaryExpression(
                      '===',
                      t.memberExpression(t.identifier('_c'), t.identifier('name')),
                      t.identifier('_serverName')
                    )
                  )
                ],
                false
              )
            )
          ]));

          // if (_existingIdx >= 0) { y.clients[_existingIdx] = _reconnectResult.client; }
          reconnectLogic.push(t.ifStatement(
            t.binaryExpression('>=', t.identifier('_existingIdx'), t.numericLiteral(0)),
            t.blockStatement([
              t.expressionStatement(
                t.assignmentExpression(
                  '=',
                  t.memberExpression(
                    t.memberExpression(t.identifier('y'), t.identifier('clients')),
                    t.identifier('_existingIdx'),
                    true
                  ),
                  t.memberExpression(t.identifier('_reconnectResult'), t.identifier('client'))
                )
              )
            ])
          ));
        } else {
          // 简化逻辑：设置错误
          reconnectLogic.push(t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(t.identifier('_result'), t.identifier('error')),
              t.stringLiteral('Reconnect function not found')
            )
          ));
        }

        tryBlock.push(t.ifStatement(
          t.unaryExpression('!', t.identifier('_serverConfig')),
          t.blockStatement([
            t.expressionStatement(
              t.assignmentExpression(
                '=',
                t.memberExpression(t.identifier('_result'), t.identifier('error')),
                t.stringLiteral('Server configuration not found')
              )
            )
          ]),
          t.blockStatement(reconnectLogic)
        ));

        statements.push(t.tryStatement(
          t.blockStatement(tryBlock),
          t.catchClause(
            t.identifier('_e'),
            t.blockStatement([
              t.expressionStatement(
                t.assignmentExpression(
                  '=',
                  t.memberExpression(t.identifier('_result'), t.identifier('error')),
                  t.logicalExpression(
                    '||',
                    t.optionalMemberExpression(
                      t.identifier('_e'),
                      t.identifier('message'),
                      false,
                      true
                    ),
                    t.stringLiteral('Unknown error')
                  )
                )
              )
            ])
          )
        ));

        // 响应: s(d, { success, server_name, status, tools_count, error });
        statements.push(t.expressionStatement(
          t.callExpression(
            t.identifier(responderName),
            [
              t.cloneNode(requestVar),
              t.objectExpression([
                t.objectProperty(t.identifier('success'), t.memberExpression(t.identifier('_result'), t.identifier('success'))),
                t.objectProperty(t.identifier('server_name'), t.identifier('_serverName')),
                t.objectProperty(t.identifier('status'), t.memberExpression(t.identifier('_result'), t.identifier('status'))),
                t.objectProperty(t.identifier('tools_count'), t.memberExpression(t.identifier('_result'), t.identifier('tools_count'))),
                t.objectProperty(t.identifier('error'), t.memberExpression(t.identifier('_result'), t.identifier('error')))
              ])
            ]
          )
        ));

        // 创建 async IIFE
        const handlerBlock = t.blockStatement([
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression(
                [],
                t.blockStatement(statements),
                true  // async
              ),
              []
            )
          )
        ]);

        // 创建新的 if 条件: *.request.subtype === "mcp_reconnect"
        const newCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('mcp_reconnect')
        );

        // 创建新的 if-else
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node  // 原来的 if 作为 else
        );

        path.replaceWith(newIfStatement);
        patchApplied = true;
        details.push(`添加了 mcp_reconnect 控制命令处理 (responder: ${responderName})`);
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
      reason: '未找到控制请求处理代码 (if *.request.subtype === "mcp_status"/"mcp_set_servers")'
    };
  }
};
