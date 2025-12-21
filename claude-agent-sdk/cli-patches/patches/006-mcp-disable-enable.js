/**
 * mcp_disable / mcp_enable 补丁
 *
 * 添加 MCP 服务器禁用/启用控制端点。
 *
 * 请求格式 (disable):
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "mcp_disable",
 *     server_name: "jetbrains"
 *   }
 * }
 *
 * 请求格式 (enable):
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "mcp_enable",
 *     server_name: "jetbrains"
 *   }
 * }
 *
 * 响应格式:
 * {
 *   success: boolean,
 *   server_name: string,
 *   status: "disabled" | "connected" | "pending" | "failed" | ...,
 *   tools_count: number,
 *   error: string | null
 * }
 *
 * 内部调用链:
 *   mcp_disable -> CY0(serverName, false) + gm(serverName, config)
 *   mcp_enable  -> CY0(serverName, true) + x2A(serverName, config)
 *
 * CLI 内部函数:
 *   - lPA(name): 检查服务器是否被禁用 (nG().disabledMcpServers.includes(name))
 *   - CY0(name, enable): 更新禁用状态到 userSettings
 *   - gm(name, config): 断开 MCP 服务器连接
 *   - x2A(name, config): 重新连接 MCP 服务器
 */

module.exports = {
  id: 'mcp_disable_enable',
  description: 'MCP server disable/enable control endpoints',
  priority: 130,  // 在 mcp_reconnect (120) 之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // Step 1: 找到 lPA 函数 (检查禁用状态)
    // 特征: function lPA(A){return(nG().disabledMcpServers||[]).includes(A)}
    // ========================================
    let checkDisabledFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (checkDisabledFnName) return;
        if (path.node.params.length !== 1) return;
        if (!path.node.id) return;

        // 检查函数体是否包含 disabledMcpServers
        let hasDisabledMcpServers = false;
        let hasIncludes = false;

        path.traverse({
          Identifier(innerPath) {
            if (innerPath.node.name === 'disabledMcpServers') {
              hasDisabledMcpServers = true;
            }
          },
          CallExpression(innerPath) {
            if (t.isMemberExpression(innerPath.node.callee)) {
              const prop = innerPath.node.callee.property;
              if (t.isIdentifier(prop) && prop.name === 'includes') {
                hasIncludes = true;
              }
            }
          }
        });

        if (hasDisabledMcpServers && hasIncludes) {
          checkDisabledFnName = path.node.id.name;
        }
      }
    });

    if (checkDisabledFnName) {
      details.push(`找到检查禁用状态函数: ${checkDisabledFnName}`);
      context.foundVariables.checkDisabledFn = checkDisabledFnName;
    }

    // ========================================
    // Step 2: 找到 CY0 函数 (更新禁用配置)
    // 特征: function CY0(A,Q){aZ(B=>{let G=B.disabledMcpServers||[];...})}
    // ========================================
    let updateDisabledFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (updateDisabledFnName) return;
        if (path.node.params.length !== 2) return;
        if (!path.node.id) return;

        let hasDisabledMcpServers = false;
        let hasFilter = false;

        path.traverse({
          Identifier(innerPath) {
            if (innerPath.node.name === 'disabledMcpServers') {
              hasDisabledMcpServers = true;
            }
          },
          CallExpression(innerPath) {
            if (t.isMemberExpression(innerPath.node.callee)) {
              const prop = innerPath.node.callee.property;
              if (t.isIdentifier(prop) && prop.name === 'filter') {
                hasFilter = true;
              }
            }
          }
        });

        if (hasDisabledMcpServers && hasFilter) {
          updateDisabledFnName = path.node.id.name;
        }
      }
    });

    if (updateDisabledFnName) {
      details.push(`找到更新禁用配置函数: ${updateDisabledFnName}`);
      context.foundVariables.updateDisabledFn = updateDisabledFnName;
    }

    // ========================================
    // Step 3: 找到 gm 函数 (断开连接)
    // 特征: async function gm(A,Q){...await G.cleanup()...}
    // ========================================
    let disconnectFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (disconnectFnName) return;
        if (!path.node.async) return;
        if (path.node.params.length !== 2) return;
        if (!path.node.id) return;

        let hasCleanup = false;
        let hasHmCall = false;

        path.traverse({
          CallExpression(innerPath) {
            if (t.isMemberExpression(innerPath.node.callee)) {
              const prop = innerPath.node.callee.property;
              if (t.isIdentifier(prop) && prop.name === 'cleanup') {
                hasCleanup = true;
              }
            }
          }
        });

        // 检查是否调用了另一个函数并检查其返回值的 type
        path.traverse({
          MemberExpression(innerPath) {
            if (t.isIdentifier(innerPath.node.property) &&
                innerPath.node.property.name === 'type' &&
                !t.isMemberExpression(innerPath.node.object)) {
              hasHmCall = true;
            }
          }
        });

        if (hasCleanup && hasHmCall) {
          disconnectFnName = path.node.id.name;
        }
      }
    });

    if (disconnectFnName) {
      details.push(`找到断开连接函数: ${disconnectFnName}`);
      context.foundVariables.disconnectFn = disconnectFnName;
    }

    // 获取已经找到的重连函数（从 004-mcp-reconnect.js）
    const reconnectFnName = context.foundVariables?.reconnectFn || null;
    if (reconnectFnName) {
      details.push(`使用已找到的重连函数: ${reconnectFnName}`);
    }

    // ========================================
    // Step 4: 找到控制请求处理位置并添加 mcp_disable/mcp_enable 分支
    // ========================================
    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "mcp_reconnect" (在 004 补丁之后)
        // 或者 "mcp_status" / "mcp_set_servers"
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
        // 优先在 mcp_reconnect 之后添加，否则在 mcp_status/mcp_set_servers 附近
        if (subtype !== 'mcp_reconnect' && subtype !== 'mcp_set_servers' && subtype !== 'mcp_status') return;

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

        // ========================================
        // 构建 mcp_disable 处理逻辑
        // ========================================
        const disableStatements = buildDisableHandler(
          t, requestVar, responderName,
          updateDisabledFnName, disconnectFnName, checkDisabledFnName
        );

        // ========================================
        // 构建 mcp_enable 处理逻辑
        // ========================================
        const enableStatements = buildEnableHandler(
          t, requestVar, responderName,
          updateDisabledFnName, reconnectFnName, checkDisabledFnName
        );

        // 创建 mcp_disable 的 if 块
        const disableBlock = t.blockStatement([
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression([], t.blockStatement(disableStatements), true),
              []
            )
          )
        ]);

        // 创建 mcp_enable 的 if 块
        const enableBlock = t.blockStatement([
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression([], t.blockStatement(enableStatements), true),
              []
            )
          )
        ]);

        // 创建嵌套的 if-else 结构
        // if (subtype === "mcp_disable") { ... }
        // else if (subtype === "mcp_enable") { ... }
        // else { 原来的 if }

        const disableCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
            t.identifier('subtype')
          ),
          t.stringLiteral('mcp_disable')
        );

        const enableCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
            t.identifier('subtype')
          ),
          t.stringLiteral('mcp_enable')
        );

        const enableIfStatement = t.ifStatement(
          enableCondition,
          enableBlock,
          path.node  // 原来的 if 作为 else
        );

        const disableIfStatement = t.ifStatement(
          disableCondition,
          disableBlock,
          enableIfStatement  // mcp_enable 作为 else
        );

        path.replaceWith(disableIfStatement);
        patchApplied = true;
        details.push(`添加了 mcp_disable/mcp_enable 控制命令处理 (responder: ${responderName})`);
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
      reason: '未找到控制请求处理代码'
    };
  }
};

/**
 * 构建 mcp_disable 处理器代码
 */
function buildDisableHandler(t, requestVar, responderName, updateDisabledFnName, disconnectFnName, checkDisabledFnName) {
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

  const tryBlock = [];

  // const _allConfigs = y?.configs || {};
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_allConfigs'),
      t.logicalExpression(
        '||',
        t.optionalMemberExpression(t.identifier('y'), t.identifier('configs'), false, true),
        t.objectExpression([])
      )
    )
  ]));

  // const _serverConfig = _allConfigs[_serverName];
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_serverConfig'),
      t.memberExpression(t.identifier('_allConfigs'), t.identifier('_serverName'), true)
    )
  ]));

  // const _client = y?.mcp?.clients?.find(c => c.name === _serverName);
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_client'),
      t.optionalCallExpression(
        t.optionalMemberExpression(
          t.optionalMemberExpression(
            t.optionalMemberExpression(t.identifier('y'), t.identifier('mcp'), false, true),
            t.identifier('clients'),
            false,
            true
          ),
          t.identifier('find'),
          false,
          true
        ),
        [
          t.arrowFunctionExpression(
            [t.identifier('_c')],
            t.binaryExpression('===', t.memberExpression(t.identifier('_c'), t.identifier('name')), t.identifier('_serverName'))
          )
        ],
        false
      )
    )
  ]));

  const disableLogic = [];

  // 检查是否已经禁用
  if (checkDisabledFnName) {
    disableLogic.push(t.ifStatement(
      t.callExpression(t.identifier(checkDisabledFnName), [t.identifier('_serverName')]),
      t.blockStatement([
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
        ),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('disabled'))
        ),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('error')), t.stringLiteral('Server already disabled'))
        )
      ]),
      t.blockStatement([
        // 更新禁用配置: CY0(_serverName, false)
        updateDisabledFnName
          ? t.expressionStatement(t.callExpression(t.identifier(updateDisabledFnName), [t.identifier('_serverName'), t.booleanLiteral(false)]))
          : t.emptyStatement(),
        // 如果已连接，断开连接
        disconnectFnName
          ? t.ifStatement(
              t.binaryExpression('===',
                t.optionalMemberExpression(t.identifier('_client'), t.identifier('type'), false, true),
                t.stringLiteral('connected')
              ),
              t.blockStatement([
                t.expressionStatement(
                  t.awaitExpression(
                    t.callExpression(t.identifier(disconnectFnName), [t.identifier('_serverName'), t.identifier('_serverConfig')])
                  )
                )
              ])
            )
          : t.emptyStatement(),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
        ),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('disabled'))
        )
      ])
    ));
  } else {
    // 简化逻辑
    if (updateDisabledFnName) {
      disableLogic.push(t.expressionStatement(
        t.callExpression(t.identifier(updateDisabledFnName), [t.identifier('_serverName'), t.booleanLiteral(false)])
      ));
    }
    if (disconnectFnName) {
      disableLogic.push(t.ifStatement(
        t.binaryExpression('===',
          t.optionalMemberExpression(t.identifier('_client'), t.identifier('type'), false, true),
          t.stringLiteral('connected')
        ),
        t.blockStatement([
          t.expressionStatement(
            t.awaitExpression(
              t.callExpression(t.identifier(disconnectFnName), [t.identifier('_serverName'), t.identifier('_serverConfig')])
            )
          )
        ])
      ));
    }
    disableLogic.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
    ));
    disableLogic.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('disabled'))
    ));
  }

  tryBlock.push(t.ifStatement(
    t.unaryExpression('!', t.identifier('_serverConfig')),
    t.blockStatement([
      t.expressionStatement(
        t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('error')), t.stringLiteral('Server configuration not found'))
      )
    ]),
    t.blockStatement(disableLogic)
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
            t.logicalExpression('||', t.optionalMemberExpression(t.identifier('_e'), t.identifier('message'), false, true), t.stringLiteral('Unknown error'))
          )
        )
      ])
    )
  ));

  // 响应
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

  return statements;
}

/**
 * 构建 mcp_enable 处理器代码
 */
function buildEnableHandler(t, requestVar, responderName, updateDisabledFnName, reconnectFnName, checkDisabledFnName) {
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

  const tryBlock = [];

  // const _allConfigs = y?.configs || {};
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_allConfigs'),
      t.logicalExpression(
        '||',
        t.optionalMemberExpression(t.identifier('y'), t.identifier('configs'), false, true),
        t.objectExpression([])
      )
    )
  ]));

  // const _serverConfig = _allConfigs[_serverName];
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_serverConfig'),
      t.memberExpression(t.identifier('_allConfigs'), t.identifier('_serverName'), true)
    )
  ]));

  const enableLogic = [];

  // 检查是否已经启用
  if (checkDisabledFnName) {
    enableLogic.push(t.ifStatement(
      t.unaryExpression('!', t.callExpression(t.identifier(checkDisabledFnName), [t.identifier('_serverName')])),
      t.blockStatement([
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
        ),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('already_enabled'))
        ),
        t.expressionStatement(
          t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('error')), t.stringLiteral('Server already enabled'))
        )
      ]),
      t.blockStatement(buildEnableCore(t, updateDisabledFnName, reconnectFnName))
    ));
  } else {
    enableLogic.push(...buildEnableCore(t, updateDisabledFnName, reconnectFnName));
  }

  tryBlock.push(t.ifStatement(
    t.unaryExpression('!', t.identifier('_serverConfig')),
    t.blockStatement([
      t.expressionStatement(
        t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('error')), t.stringLiteral('Server configuration not found'))
      )
    ]),
    t.blockStatement(enableLogic)
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
            t.logicalExpression('||', t.optionalMemberExpression(t.identifier('_e'), t.identifier('message'), false, true), t.stringLiteral('Unknown error'))
          )
        )
      ])
    )
  ));

  // 响应
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

  return statements;
}

/**
 * 构建启用核心逻辑
 */
function buildEnableCore(t, updateDisabledFnName, reconnectFnName) {
  const core = [];

  // 更新禁用配置: CY0(_serverName, true)
  if (updateDisabledFnName) {
    core.push(t.expressionStatement(
      t.callExpression(t.identifier(updateDisabledFnName), [t.identifier('_serverName'), t.booleanLiteral(true)])
    ));
  }

  // 重新连接
  if (reconnectFnName) {
    // const _reconnectResult = await x2A(_serverName, _serverConfig);
    core.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_reconnectResult'),
        t.awaitExpression(
          t.callExpression(t.identifier(reconnectFnName), [t.identifier('_serverName'), t.identifier('_serverConfig')])
        )
      )
    ]));

    // _result.status = _reconnectResult.client?.type || "failed";
    core.push(t.expressionStatement(
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
    core.push(t.expressionStatement(
      t.assignmentExpression(
        '=',
        t.memberExpression(t.identifier('_result'), t.identifier('success')),
        t.binaryExpression('===', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('connected'))
      )
    ));

    // _result.tools_count = _reconnectResult.tools?.length || 0;
    core.push(t.expressionStatement(
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
  } else {
    // 简化逻辑：标记为 pending
    core.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
    ));
    core.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('pending'))
    ));
  }

  return core;
}
