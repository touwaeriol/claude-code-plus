/**
 * mcp_tools 补丁
 *
 * 添加 MCP 工具列表查询控制端点，从 y.mcp.tools 读取指定服务器的工具列表。
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
 * 内部读取: y.mcp.tools (MCP 工具注册表)
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
    // 在 mcp_reconnect 或 mcp_status 附近添加
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

        // 构建 mcp_tools 处理逻辑
        const statements = [];

        // const _serverName = d.request.server_name || null;
        statements.push(t.variableDeclaration('const', [
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

        // const _allTools = y?.mcp?.tools || [];
        statements.push(t.variableDeclaration('const', [
          t.variableDeclarator(
            t.identifier('_allTools'),
            t.logicalExpression(
              '||',
              t.optionalMemberExpression(
                t.optionalMemberExpression(
                  t.identifier('y'),
                  t.identifier('mcp'),
                  false,
                  true
                ),
                t.identifier('tools'),
                false,
                true
              ),
              t.arrayExpression([])
            )
          )
        ]));

        // let _filteredTools = _allTools;
        statements.push(t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('_filteredTools'),
            t.identifier('_allTools')
          )
        ]));

        // if (_serverName) { _filteredTools = _allTools.filter(t => t.serverName === _serverName); }
        statements.push(t.ifStatement(
          t.identifier('_serverName'),
          t.blockStatement([
            t.expressionStatement(
              t.assignmentExpression(
                '=',
                t.identifier('_filteredTools'),
                t.callExpression(
                  t.memberExpression(t.identifier('_allTools'), t.identifier('filter')),
                  [
                    t.arrowFunctionExpression(
                      [t.identifier('_t')],
                      t.binaryExpression(
                        '===',
                        t.memberExpression(t.identifier('_t'), t.identifier('serverName')),
                        t.identifier('_serverName')
                      )
                    )
                  ]
                )
              )
            )
          ])
        ));

        // const _toolsResult = _filteredTools.map(t => ({
        //   name: t.originalMcpToolName || t.name,
        //   description: t.description || "",
        //   inputSchema: t.inputJSONSchema || t.inputSchema || {}
        // }));
        statements.push(t.variableDeclaration('const', [
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

        // 响应: s(d, { server_name, tools, count });
        statements.push(t.expressionStatement(
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

        // 创建处理块
        const handlerBlock = t.blockStatement(statements);

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
        details.push(`添加了 mcp_tools 控制命令处理 (responder: ${responderName})`);
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
