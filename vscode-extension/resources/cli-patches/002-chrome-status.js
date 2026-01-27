/**
 * get_chrome_status 补丁
 *
 * 实现与官方 /chrome 命令相同的状态查询逻辑：
 * - installed: 扩展是否已安装（调用 a4A()）
 * - enabled: 配置中是否默认启用（调用 k1().claudeInChromeDefaultEnabled）
 * - connected: MCP 服务器是否已连接
 * - mcpServerStatus: MCP 服务器状态字符串
 * - serverInfo: MCP 服务器信息对象
 *
 * 返回格式:
 * {
 *   installed: boolean,
 *   enabled: boolean,
 *   connected: boolean,
 *   mcpServerStatus: string | null,  // "connected", "pending", "failed", "disabled", etc.
 *   serverInfo: object | null
 * }
 */

module.exports = {
  id: 'get_chrome_status',
  description: 'Chrome status query using internal CLI functions',
  priority: 110,  // 在 run_in_background (100) 之后
  required: false,  // 可选补丁
  disabled: false,  // 启用补丁

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // Step 1: 找到 isExtensionInstalled 函数 (a4A)
    // 特征: async 函数，检查 Chrome Extensions 目录
    // ========================================
    let extensionCheckFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (extensionCheckFnName) return;
        if (!path.node.async) return;

        // 查找包含 "Extensions" 字符串的函数
        let hasExtensionsString = false;
        path.traverse({
          StringLiteral(innerPath) {
            if (innerPath.node.value === 'Extensions') {
              hasExtensionsString = true;
            }
          }
        });

        if (hasExtensionsString && path.node.id) {
          extensionCheckFnName = path.node.id.name;
        }
      },
      VariableDeclarator(path) {
        if (extensionCheckFnName) return;
        if (!t.isArrowFunctionExpression(path.node.init) && !t.isFunctionExpression(path.node.init)) return;
        if (!path.node.init.async) return;

        let hasExtensionsString = false;
        path.traverse({
          StringLiteral(innerPath) {
            if (innerPath.node.value === 'Extensions') {
              hasExtensionsString = true;
            }
          }
        });

        if (hasExtensionsString && t.isIdentifier(path.node.id)) {
          extensionCheckFnName = path.node.id.name;
        }
      }
    });

    if (extensionCheckFnName) {
      details.push(`找到扩展检测函数: ${extensionCheckFnName}`);
    } else {
      details.push('警告: 未找到扩展检测函数，将使用 false 作为默认值');
    }

    // ========================================
    // Step 2: 找到配置读取函数 (k1)
    // 特征: 返回包含 claudeInChromeDefaultEnabled 的对象
    // ========================================
    let configFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (configFnName) return;

        let hasClaudeInChrome = false;
        path.traverse({
          StringLiteral(innerPath) {
            if (innerPath.node.value === 'claudeInChromeDefaultEnabled') {
              hasClaudeInChrome = true;
            }
          },
          Identifier(innerPath) {
            if (innerPath.node.name === 'claudeInChromeDefaultEnabled') {
              hasClaudeInChrome = true;
            }
          }
        });

        if (hasClaudeInChrome && path.node.id) {
          configFnName = path.node.id.name;
        }
      },
      VariableDeclarator(path) {
        if (configFnName) return;
        if (!t.isArrowFunctionExpression(path.node.init) && !t.isFunctionExpression(path.node.init)) return;

        let hasClaudeInChrome = false;
        path.traverse({
          StringLiteral(innerPath) {
            if (innerPath.node.value === 'claudeInChromeDefaultEnabled') {
              hasClaudeInChrome = true;
            }
          },
          Identifier(innerPath) {
            if (innerPath.node.name === 'claudeInChromeDefaultEnabled') {
              hasClaudeInChrome = true;
            }
          }
        });

        if (hasClaudeInChrome && t.isIdentifier(path.node.id)) {
          configFnName = path.node.id.name;
        }
      }
    });

    if (configFnName) {
      details.push(`找到配置函数: ${configFnName}`);
    } else {
      details.push('警告: 未找到配置函数，将使用 false 作为默认值');
    }

    // ========================================
    // Step 3: 找到 MCP 服务器名称常量 (gk = "claude-in-chrome")
    // ========================================
    let mcpServerNameVar = null;

    traverse(ast, {
      VariableDeclarator(path) {
        if (mcpServerNameVar) return;
        if (!t.isStringLiteral(path.node.init)) return;
        if (path.node.init.value === 'claude-in-chrome' && t.isIdentifier(path.node.id)) {
          mcpServerNameVar = path.node.id.name;
        }
      }
    });

    if (mcpServerNameVar) {
      details.push(`找到 MCP 服务器名称变量: ${mcpServerNameVar}`);
    } else {
      details.push('警告: 未找到 MCP 服务器名称变量，将使用字符串常量');
    }

    // ========================================
    // Step 4: 找到控制请求处理位置并添加 get_chrome_status 分支
    // ========================================
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

        const subtypeValue = test.right;
        if (!t.isStringLiteral(subtypeValue)) return;

        const subtype = subtypeValue.value;
        if (subtype !== 'interrupt' && subtype !== 'run_in_background') return;

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

        // 构建处理逻辑
        // (async () => {
        //   let _installed = false;
        //   let _enabled = false;
        //   let _connected = false;
        //   let _mcpStatus = null;
        //   let _serverInfo = null;
        //
        //   // 获取扩展安装状态
        //   try { _installed = await extensionCheckFn(); } catch(e) {}
        //
        //   // 获取配置
        //   try { _enabled = configFn()?.claudeInChromeDefaultEnabled || false; } catch(e) {}
        //
        //   // 获取 MCP 状态 - 需要访问 mcp.clients
        //   // 这部分需要找到 mcp 客户端列表的引用
        //
        //   s(d, { installed, enabled, connected, mcpServerStatus, serverInfo });
        // })();

        const statements = [];

        // let _installed = false;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(t.identifier('_installed'), t.booleanLiteral(false))
        ]));

        // let _enabled = false;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(t.identifier('_enabled'), t.booleanLiteral(false))
        ]));

        // let _connected = false;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(t.identifier('_connected'), t.booleanLiteral(false))
        ]));

        // let _mcpStatus = null;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(t.identifier('_mcpStatus'), t.nullLiteral())
        ]));

        // let _extensionVersion = null;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(t.identifier('_extensionVersion'), t.nullLiteral())
        ]));

        // try { _installed = await extensionCheckFn(); } catch(e) {}
        if (extensionCheckFnName) {
          statements.push(t.tryStatement(
            t.blockStatement([
              t.expressionStatement(
                t.assignmentExpression(
                  '=',
                  t.identifier('_installed'),
                  t.awaitExpression(t.callExpression(t.identifier(extensionCheckFnName), []))
                )
              )
            ]),
            t.catchClause(t.identifier('_e'), t.blockStatement([]))
          ));
        }

        // try { _enabled = configFn()?.claudeInChromeDefaultEnabled || false; } catch(e) {}
        if (configFnName) {
          statements.push(t.tryStatement(
            t.blockStatement([
              t.expressionStatement(
                t.assignmentExpression(
                  '=',
                  t.identifier('_enabled'),
                  t.logicalExpression(
                    '||',
                    t.optionalMemberExpression(
                      t.callExpression(t.identifier(configFnName), []),
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
          ));
        }

        // 获取 MCP 状态
        // try {
        //   const _mcpName = "claude-in-chrome";  // or mcpServerNameVar
        //   const _client = this.mcp?.clients?.find(c => c.name === _mcpName);
        //   if (_client) {
        //     _mcpStatus = _client.type;
        //     _connected = _client.type === "connected";
        //     _serverInfo = _client.serverInfo || null;
        //   }
        // } catch(e) {}
        const mcpNameExpr = mcpServerNameVar
          ? t.identifier(mcpServerNameVar)
          : t.stringLiteral('claude-in-chrome');

        statements.push(t.tryStatement(
          t.blockStatement([
            // const _mcpClients = this.mcp?.clients;
            t.variableDeclaration('const', [
              t.variableDeclarator(
                t.identifier('_mcpClients'),
                t.optionalMemberExpression(
                  t.optionalMemberExpression(
                    t.thisExpression(),
                    t.identifier('mcp'),
                    false,
                    true
                  ),
                  t.identifier('clients'),
                  false,
                  true
                )
              )
            ]),
            // if (_mcpClients) {
            //   const _client = _mcpClients.find(c => c.name === mcpName);
            //   if (_client) { ... }
            // }
            t.ifStatement(
              t.identifier('_mcpClients'),
              t.blockStatement([
                t.variableDeclaration('const', [
                  t.variableDeclarator(
                    t.identifier('_client'),
                    t.callExpression(
                      t.memberExpression(t.identifier('_mcpClients'), t.identifier('find')),
                      [
                        t.arrowFunctionExpression(
                          [t.identifier('_c')],
                          t.binaryExpression(
                            '===',
                            t.memberExpression(t.identifier('_c'), t.identifier('name')),
                            mcpNameExpr
                          )
                        )
                      ]
                    )
                  )
                ]),
                t.ifStatement(
                  t.identifier('_client'),
                  t.blockStatement([
                    // _mcpStatus = _client.type;
                    t.expressionStatement(
                      t.assignmentExpression(
                        '=',
                        t.identifier('_mcpStatus'),
                        t.memberExpression(t.identifier('_client'), t.identifier('type'))
                      )
                    ),
                    // _connected = _client.type === "connected";
                    t.expressionStatement(
                      t.assignmentExpression(
                        '=',
                        t.identifier('_connected'),
                        t.binaryExpression(
                          '===',
                          t.memberExpression(t.identifier('_client'), t.identifier('type')),
                          t.stringLiteral('connected')
                        )
                      )
                    ),
                    // _extensionVersion = _client.serverInfo?.version || null;
                    t.expressionStatement(
                      t.assignmentExpression(
                        '=',
                        t.identifier('_extensionVersion'),
                        t.logicalExpression(
                          '||',
                          t.optionalMemberExpression(
                            t.memberExpression(t.identifier('_client'), t.identifier('serverInfo')),
                            t.identifier('version'),
                            false,
                            true  // optional
                          ),
                          t.nullLiteral()
                        )
                      )
                    )
                  ])
                )
              ])
            )
          ]),
          t.catchClause(t.identifier('_e'), t.blockStatement([]))
        ));

        // 响应: s(d, { installed, enabled, connected, mcpServerStatus, extensionVersion });
        statements.push(t.expressionStatement(
          t.callExpression(
            t.identifier(responderName),
            [
              t.cloneNode(requestVar),
              t.objectExpression([
                t.objectProperty(t.identifier('installed'), t.identifier('_installed')),
                t.objectProperty(t.identifier('enabled'), t.identifier('_enabled')),
                t.objectProperty(t.identifier('connected'), t.identifier('_connected')),
                t.objectProperty(t.identifier('mcpServerStatus'), t.identifier('_mcpStatus')),
                t.objectProperty(t.identifier('extensionVersion'), t.identifier('_extensionVersion'))
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

        // 创建新的 if 条件: *.request.subtype === "get_chrome_status"
        const newCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('get_chrome_status')
        );

        // 创建新的 if-else
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node  // 原来的 if 作为 else
        );

        path.replaceWith(newIfStatement);
        patchApplied = true;
        details.push(`添加了 get_chrome_status 控制命令处理 (responder: ${responderName})`);
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
