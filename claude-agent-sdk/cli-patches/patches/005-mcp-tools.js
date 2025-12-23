/**
 * mcp_tools 补丁
 *
 * 添加 MCP 工具列表查询控制端点。
 * 与 CLI 内部 /mcp 命令逻辑完全一致：直接使用 getAppState().mcp.tools
 *
 * 请求格式:
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "mcp_tools",
 *     server_name: "jetbrains"  // 可选，不传则返回所有工具
 *   }
 * }
 *
 * 响应格式:
 * {
 *   server_name: string | null,
 *   tools: [
 *     {
 *       name: string,           // 原始工具名 (如 "FileIndex")
 *       description: string,
 *       inputSchema: object     // JSON Schema
 *     }
 *   ],
 *   count: number
 * }
 *
 * 内部实现: 直接调用 getAppState().mcp.tools (与 CLI 内部 /mcp 命令一致)
 */

module.exports = {
  id: 'mcp_tools',
  description: 'MCP tools list query control endpoint',
  priority: 130,  // 在 mcp_reconnect (120) 之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // 找到控制请求处理位置并添加 mcp_tools 分支
    // ========================================
    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "mcp_status" 或 "mcp_set_servers" 或 "mcp_reconnect"
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
        // 在 mcp 相关控制命令附近添加
        if (subtype !== 'mcp_set_servers' && subtype !== 'mcp_status' && subtype !== 'mcp_reconnect') return;

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
            // 检查 IIFE 内部
            if (t.isExpressionStatement(stmt)) {
              const expr = stmt.expression;
              if (t.isCallExpression(expr) && t.isArrowFunctionExpression(expr.callee)) {
                const body = expr.callee.body;
                if (t.isBlockStatement(body)) {
                  for (const innerStmt of body.body) {
                    if (t.isExpressionStatement(innerStmt) && t.isCallExpression(innerStmt.expression)) {
                      const innerCallee = innerStmt.expression.callee;
                      if (t.isIdentifier(innerCallee)) {
                        responderName = innerCallee.name;
                        break;
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 构建 mcp_tools 处理逻辑 - 使用 IIFE 因为需要 await
        // (async () => {
        //   let _state = await X();
        //   let _serverName = d.request.server_name || null;
        //   let _allTools = _state.mcp.tools;
        //   let _filteredTools = _serverName ? _allTools.filter(...) : _allTools;
        //   let _toolsResult = _filteredTools.map(...);
        //   s(d, { server_name, tools, count });
        // })()

        const iifeBody = [];

        // let _state = await X();
        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_state'),
            t.awaitExpression(t.callExpression(t.identifier('X'), []))
          )
        ]));

        // let _serverName = d.request.server_name || null;
        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_serverName'),
            t.logicalExpression(
              '||',
              t.memberExpression(
                t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
                t.identifier('server_name')
              ),
              t.nullLiteral()
            )
          )
        ]));

        // let _allTools = _state.mcp.tools || [];
        // 与 CLI 内部 /mcp 命令逻辑完全一致：直接使用 getAppState().mcp.tools
        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_allTools'),
            t.logicalExpression(
              '||',
              t.memberExpression(
                t.memberExpression(t.identifier('_state'), t.identifier('mcp')),
                t.identifier('tools')
              ),
              t.arrayExpression([])
            )
          )
        ]));

        // let _prefix = _serverName ? `mcp__${_serverName}__` : null;
        // let _filteredTools = _prefix ? _allTools.filter(_t => _t.name?.startsWith(_prefix)) : _allTools;
        // 与 CLI 内部 VbA 函数逻辑一致：按工具名前缀 mcp__serverName__ 过滤
        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_prefix'),
            t.conditionalExpression(
              t.identifier('_serverName'),
              t.templateLiteral(
                [
                  t.templateElement({ raw: 'mcp__', cooked: 'mcp__' }, false),
                  t.templateElement({ raw: '__', cooked: '__' }, true)
                ],
                [t.identifier('_serverName')]
              ),
              t.nullLiteral()
            )
          )
        ]));

        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_filteredTools'),
            t.conditionalExpression(
              t.identifier('_prefix'),
              t.callExpression(
                t.memberExpression(t.identifier('_allTools'), t.identifier('filter')),
                [
                  t.arrowFunctionExpression(
                    [t.identifier('_t')],
                    t.optionalCallExpression(
                      t.optionalMemberExpression(
                        t.memberExpression(t.identifier('_t'), t.identifier('name')),
                        t.identifier('startsWith'),
                        false,
                        true
                      ),
                      [t.identifier('_prefix')],
                      false
                    )
                  )
                ]
              ),
              t.identifier('_allTools')
            )
          )
        ]));

        // let _toolsResult = _filteredTools.map(_t => ({
        //   name: _t.originalMcpToolName || _t.name,
        //   description: _t.description || "",
        //   inputSchema: _t.inputJSONSchema || _t.inputSchema || {}
        // }));
        iifeBody.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_toolsResult'),
            t.callExpression(
              t.memberExpression(t.identifier('_filteredTools'), t.identifier('map')),
              [
                t.arrowFunctionExpression(
                  [t.identifier('_t')],
                  t.objectExpression([
                    t.objectProperty(
                      t.identifier('name'),
                      t.logicalExpression(
                        '||',
                        t.memberExpression(t.identifier('_t'), t.identifier('originalMcpToolName')),
                        t.memberExpression(t.identifier('_t'), t.identifier('name'))
                      )
                    ),
                    t.objectProperty(
                      t.identifier('description'),
                      t.logicalExpression(
                        '||',
                        t.memberExpression(t.identifier('_t'), t.identifier('description')),
                        t.stringLiteral('')
                      )
                    ),
                    t.objectProperty(
                      t.identifier('inputSchema'),
                      t.logicalExpression(
                        '||',
                        t.logicalExpression(
                          '||',
                          t.memberExpression(t.identifier('_t'), t.identifier('inputJSONSchema')),
                          t.memberExpression(t.identifier('_t'), t.identifier('inputSchema'))
                        ),
                        t.objectExpression([])
                      )
                    )
                  ])
                )
              ]
            )
          )
        ]));

        // s(d, { server_name: _serverName, tools: _toolsResult, count: _toolsResult.length });
        iifeBody.push(t.expressionStatement(
          t.callExpression(
            t.identifier(responderName),
            [
              t.cloneNode(requestVar),
              t.objectExpression([
                t.objectProperty(t.identifier('server_name'), t.identifier('_serverName')),
                t.objectProperty(t.identifier('tools'), t.identifier('_toolsResult')),
                t.objectProperty(
                  t.identifier('count'),
                  t.memberExpression(t.identifier('_toolsResult'), t.identifier('length'))
                )
              ])
            ]
          )
        ));

        // 创建 IIFE: (async () => { ... })()
        const iife = t.callExpression(
          t.arrowFunctionExpression(
            [],
            t.blockStatement(iifeBody),
            true  // async
          ),
          []
        );

        // 创建处理块
        const handlerBlock = t.blockStatement([
          t.expressionStatement(iife)
        ]);

        // 创建新的 if 条件: *.request.subtype === "mcp_tools"
        const newCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('mcp_tools')
        );

        // 创建新的 if-else
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node  // 原来的 if 作为 else
        );

        path.replaceWith(newIfStatement);
        patchApplied = true;
        details.push(`添加了 mcp_tools 控制命令处理，使用 getAppState().mcp.tools (responder: ${responderName})`);
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
      reason: '未找到控制请求处理代码 (if *.request.subtype === "mcp_status"/"mcp_set_servers"/"mcp_reconnect")'
    };
  }
};
