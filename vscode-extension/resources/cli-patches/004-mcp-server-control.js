/**
 * MCP Server Control Patch (Unified)
 *
 * Merged from 004-mcp-reconnect.js and 006-mcp-disable-enable.js
 * Provides complete MCP server runtime control capabilities
 *
 * ============================================================
 * Features
 * ============================================================
 *
 * 1. mcp_reconnect - Reconnect MCP server
 * 2. mcp_disable   - Disable MCP server
 * 3. mcp_enable    - Enable MCP server
 *
 * ============================================================
 * Request Format
 * ============================================================
 *
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "mcp_reconnect" | "mcp_disable" | "mcp_enable",
 *     server_name: "server_name"
 *   }
 * }
 *
 * ============================================================
 * Response Format
 * ============================================================
 *
 * {
 *   success: boolean,
 *   server_name: string,
 *   status: "connected" | "disabled" | "failed" | "pending" | "already_enabled",
 *   tools_count: number,
 *   error: string | null
 * }
 *
 * ============================================================
 * CLI Internal Functions (Discovered via AST analysis)
 * ============================================================
 *
 * Reconnect related:
 *   - x2A(name, config): Reconnect MCP server (returns {client, tools})
 *
 * Disable/Enable related:
 *   - lPA(name):         Check if server is disabled
 *   - CY0(name, enable): Update disabled status to userSettings
 *   - gm(name, config):  Disconnect MCP server
 *
 * Variables:
 *   - J (configs):   MCP server config Map
 *   - S (clients):   MCP client status array
 *   - s (responder): Control response function
 *
 * ============================================================
 * Variable Discovery Mechanism
 * ============================================================
 *
 * Dynamically extract variable names by analyzing {configs:X, clients:Y, tools:Z}
 * in mcp_set_servers handling code, avoiding hardcoded obfuscated variable names
 */

module.exports = {
  id: 'mcp_server_control',
  description: 'MCP server control (reconnect/disable/enable)',
  priority: 120,  // 在 chrome_status (110) 之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // Step 1: Find x2A function (MCP reconnect core function)
    // Pattern: async function x2A(A, Q) { ... await gm(A, Q); ... await hm(A, Q); ... }
    // ========================================
    let reconnectFnName = null;

    traverse(ast, {
      FunctionDeclaration(path) {
        if (reconnectFnName) return;
        if (!path.node.async) return;
        if (path.node.params.length !== 2) return;

        // Check if function body contains gm and hm calls
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
            // Check await hm(A, Q) call
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

        // Check if return value contains client, tools
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

        // Same check logic
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
      details.push(`Found reconnect function: ${reconnectFnName}`);
      context.foundVariables.reconnectFn = reconnectFnName;
    } else {
      details.push('Warning: Reconnect function x2A not found, using simplified logic');
    }

    // ========================================
    // Step 2: Find MCP config variable names (configs, clients)
    // Extract from mcp_set_servers handling code
    // Pattern: {configs:J,clients:S,tools:f}
    // ========================================
    let configsVarName = null;
    let clientsVarName = null;

    traverse(ast, {
      IfStatement(path) {
        if (configsVarName && clientsVarName) return;

        const test = path.node.test;
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;

        const right = test.right;
        if (!t.isStringLiteral(right) || right.value !== 'mcp_set_servers') return;

        // Found mcp_set_servers handling location
        // Search for {configs:X,clients:Y,...} object expression
        path.traverse({
          ObjectExpression(objPath) {
            if (configsVarName && clientsVarName) return;

            const props = objPath.node.properties;
            let foundConfigs = null;
            let foundClients = null;

            for (const prop of props) {
              if (!t.isObjectProperty(prop)) continue;
              if (!t.isIdentifier(prop.key)) continue;

              if (prop.key.name === 'configs' && t.isIdentifier(prop.value)) {
                foundConfigs = prop.value.name;
              }
              if (prop.key.name === 'clients' && t.isIdentifier(prop.value)) {
                foundClients = prop.value.name;
              }
            }

            // Only valid when both configs and clients are found
            if (foundConfigs && foundClients) {
              configsVarName = foundConfigs;
              clientsVarName = foundClients;
            }
          }
        });
      }
    });

    if (configsVarName) {
      details.push(`Found configs variable: ${configsVarName}`);
      context.foundVariables.mcpConfigsVar = configsVarName;
    } else {
      details.push('Warning: configs variable not found, cannot access server config');
    }

    if (clientsVarName) {
      details.push(`Found clients variable: ${clientsVarName}`);
      context.foundVariables.mcpClientsVar = clientsVarName;
    } else {
      details.push('Warning: clients variable not found, cannot update client status');
    }

    // ========================================

    // ========================================
    // Step 2b: Find lPA function (check disabled status)
    // ========================================
    let checkDisabledFnName = null;
    traverse(ast, {
      FunctionDeclaration(path) {
        if (checkDisabledFnName) return;
        if (path.node.params.length !== 1 || !path.node.id) return;
        let hasDisabledMcpServers = false, hasIncludes = false;
        path.traverse({
          Identifier(p) { if (p.node.name === 'disabledMcpServers') hasDisabledMcpServers = true; },
          CallExpression(p) {
            if (t.isMemberExpression(p.node.callee) && t.isIdentifier(p.node.callee.property) && p.node.callee.property.name === 'includes') hasIncludes = true;
          }
        });
        if (hasDisabledMcpServers && hasIncludes) checkDisabledFnName = path.node.id.name;
      }
    });
    if (checkDisabledFnName) {
      details.push(`Found check disabled function: ${checkDisabledFnName}`);
      context.foundVariables.checkDisabledFn = checkDisabledFnName;
    }

    // ========================================
    // Step 2c: Find CY0 function (update disabled config)
    // ========================================
    let updateDisabledFnName = null;
    traverse(ast, {
      FunctionDeclaration(path) {
        if (updateDisabledFnName) return;
        if (path.node.params.length !== 2 || !path.node.id) return;
        let hasDisabledMcpServers = false, hasFilter = false;
        path.traverse({
          Identifier(p) { if (p.node.name === 'disabledMcpServers') hasDisabledMcpServers = true; },
          CallExpression(p) {
            if (t.isMemberExpression(p.node.callee) && t.isIdentifier(p.node.callee.property) && p.node.callee.property.name === 'filter') hasFilter = true;
          }
        });
        if (hasDisabledMcpServers && hasFilter) updateDisabledFnName = path.node.id.name;
      }
    });
    if (updateDisabledFnName) {
      details.push(`Found update disabled config function: ${updateDisabledFnName}`);
      context.foundVariables.updateDisabledFn = updateDisabledFnName;
    }

    // ========================================
    // Step 2d: Find gm function (disconnect)
    // ========================================
    let disconnectFnName = null;
    traverse(ast, {
      FunctionDeclaration(path) {
        if (disconnectFnName) return;
        if (!path.node.async || path.node.params.length !== 2 || !path.node.id) return;
        let hasCleanup = false, hasTypeCheck = false;
        path.traverse({
          CallExpression(p) {
            if (t.isMemberExpression(p.node.callee) && t.isIdentifier(p.node.callee.property) && p.node.callee.property.name === 'cleanup') hasCleanup = true;
          },
          MemberExpression(p) {
            if (t.isIdentifier(p.node.property) && p.node.property.name === 'type' && !t.isMemberExpression(p.node.object)) hasTypeCheck = true;
          }
        });
        if (hasCleanup && hasTypeCheck) disconnectFnName = path.node.id.name;
      }
    });
    if (disconnectFnName) {
      details.push(`Found disconnect function: ${disconnectFnName}`);
      context.foundVariables.disconnectFn = disconnectFnName;
    }


    // Step 3: Find control request handling location and add mcp_reconnect branch
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

        // Found control request handling location
        const requestVar = obj.object;

        // Find responder function name
        let responderName = context.foundVariables?.responderName || 's';
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
        // Save responder function name to context for subsequent patches
        context.foundVariables.responderName = responderName;
        details.push(`Found responder function: ${responderName}`);

        // Build mcp_reconnect handling logic
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

        // Find server config - using dynamically discovered variable name
        // const _allConfigs = J || {};  (使用发现的 configsVarName)
        if (configsVarName) {
          tryBlock.push(t.variableDeclaration('const', [
            t.variableDeclarator(
              t.identifier('_allConfigs'),
              t.logicalExpression(
                '||',
                t.identifier(configsVarName),
                t.objectExpression([])
              )
            )
          ]));
        } else {
          // Fallback: try using empty object
          tryBlock.push(t.variableDeclaration('const', [
            t.variableDeclarator(
              t.identifier('_allConfigs'),
              t.objectExpression([])
            )
          ]));
          tryBlock.push(t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.memberExpression(t.identifier('_result'), t.identifier('error')),
              t.stringLiteral('MCP configs variable not found')
            )
          ));
        }

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

          // Update MCP status (using dynamically discovered clients variable name)
          if (clientsVarName) {
            // const _existingIdx = S?.findIndex(c => c.name === _serverName);
            reconnectLogic.push(t.variableDeclaration('const', [
              t.variableDeclarator(
                t.identifier('_existingIdx'),
                t.optionalCallExpression(
                  t.optionalMemberExpression(
                    t.identifier(clientsVarName),
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

            // if (_existingIdx >= 0) { S[_existingIdx] = _reconnectResult.client; }
            reconnectLogic.push(t.ifStatement(
              t.binaryExpression('>=', t.identifier('_existingIdx'), t.numericLiteral(0)),
              t.blockStatement([
                t.expressionStatement(
                  t.assignmentExpression(
                    '=',
                    t.memberExpression(
                      t.identifier(clientsVarName),
                      t.identifier('_existingIdx'),
                      true
                    ),
                    t.memberExpression(t.identifier('_reconnectResult'), t.identifier('client'))
                  )
                )
              ])
            ));
          }
        } else {
          // Simplified logic: set error
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

        // Response: s(d, { success, server_name, status, tools_count, error });
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

        // Create async IIFE
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

        // Create new if condition: *.request.subtype === "mcp_reconnect"
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

        // Build mcp_disable handling logic
        const disableStatements = buildDisableHandler(
          t, requestVar, responderName,
          updateDisabledFnName, disconnectFnName, checkDisabledFnName,
          configsVarName, clientsVarName
        );

        // Build mcp_enable handling logic
        const enableStatements = buildEnableHandler(
          t, requestVar, responderName,
          updateDisabledFnName, reconnectFnName, checkDisabledFnName,
          configsVarName
        );

        // Create mcp_disable if block
        const disableBlock = t.blockStatement([
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression([], t.blockStatement(disableStatements), true),
              []
            )
          )
        ]);

        // Create mcp_enable if block
        const enableBlock = t.blockStatement([
          t.expressionStatement(
            t.callExpression(
              t.arrowFunctionExpression([], t.blockStatement(enableStatements), true),
              []
            )
          )
        ]);

        // Create helper function for condition expressions
        const createCondition = (subtypeName) => t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
            t.identifier('subtype')
          ),
          t.stringLiteral(subtypeName)
        );

        // Create nested if-else structure:
        // if (mcp_reconnect) { ... }
        // else if (mcp_disable) { ... }
        // else if (mcp_enable) { ... }
        // else { original if }

        const enableIfStatement = t.ifStatement(
          createCondition('mcp_enable'),
          enableBlock,
          path.node  // 原来的 if 作为 else
        );

        const disableIfStatement = t.ifStatement(
          createCondition('mcp_disable'),
          disableBlock,
          enableIfStatement  // mcp_enable 作为 else
        );

        const reconnectIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          disableIfStatement  // mcp_disable 作为 else
        );

        path.replaceWith(reconnectIfStatement);
        patchApplied = true;
        details.push(`Added mcp_reconnect/mcp_disable/mcp_enable control command handling (responder: ${responderName})`);
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
      reason: 'Control request handling code not found (if *.request.subtype === "mcp_status"/"mcp_set_servers")'
    };
  }
};

/**
 * Build mcp_disable handler code
 */
function buildDisableHandler(t, requestVar, responderName, updateDisabledFnName, disconnectFnName, checkDisabledFnName, configsVarName, clientsVarName) {
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

  // Use dynamically discovered configs variable name
  // const _allConfigs = J || {};
  if (configsVarName) {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_allConfigs'),
        t.logicalExpression(
          '||',
          t.identifier(configsVarName),
          t.objectExpression([])
        )
      )
    ]));
  } else {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_allConfigs'),
        t.objectExpression([])
      )
    ]));
  }

  // const _serverConfig = _allConfigs[_serverName];
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_serverConfig'),
      t.memberExpression(t.identifier('_allConfigs'), t.identifier('_serverName'), true)
    )
  ]));

  // Use dynamically discovered clients variable name
  // const _client = S?.find(c => c.name === _serverName);
  if (clientsVarName) {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_client'),
        t.optionalCallExpression(
          t.optionalMemberExpression(
            t.identifier(clientsVarName),
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
  } else {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(t.identifier('_client'), t.nullLiteral())
    ]));
  }

  const disableLogic = [];

  // Check if already disabled
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
        // Update disabled config: CY0(_serverName, false)
        updateDisabledFnName
          ? t.expressionStatement(t.callExpression(t.identifier(updateDisabledFnName), [t.identifier('_serverName'), t.booleanLiteral(false)]))
          : t.emptyStatement(),
        // If connected, disconnect
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
    // Simplified logic
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

  // Response
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
 * Build mcp_enable handler code
 */
function buildEnableHandler(t, requestVar, responderName, updateDisabledFnName, reconnectFnName, checkDisabledFnName, configsVarName) {
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

  // Use dynamically discovered configs variable name
  // const _allConfigs = J || {};
  if (configsVarName) {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_allConfigs'),
        t.logicalExpression(
          '||',
          t.identifier(configsVarName),
          t.objectExpression([])
        )
      )
    ]));
  } else {
    tryBlock.push(t.variableDeclaration('const', [
      t.variableDeclarator(
        t.identifier('_allConfigs'),
        t.objectExpression([])
      )
    ]));
  }

  // const _serverConfig = _allConfigs[_serverName];
  tryBlock.push(t.variableDeclaration('const', [
    t.variableDeclarator(
      t.identifier('_serverConfig'),
      t.memberExpression(t.identifier('_allConfigs'), t.identifier('_serverName'), true)
    )
  ]));

  const enableLogic = [];

  // Check if already enabled
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

  // Response
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
 * Build enable core logic
 */
function buildEnableCore(t, updateDisabledFnName, reconnectFnName) {
  const core = [];

  // Update disabled config: CY0(_serverName, true)
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
    // Simplified logic: mark as pending
    core.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('success')), t.booleanLiteral(true))
    ));
    core.push(t.expressionStatement(
      t.assignmentExpression('=', t.memberExpression(t.identifier('_result'), t.identifier('status')), t.stringLiteral('pending'))
    ));
  }

  return core;
}
