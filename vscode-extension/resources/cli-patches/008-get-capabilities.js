/**
 * get_capabilities 补丁
 *
 * 添加功能状态查询控制端点，用于查询 CLI 的运行时能力。
 *
 * 请求格式:
 * {
 *   type: "control_request",
 *   request_id: "xxx",
 *   request: {
 *     subtype: "get_capabilities"
 *   }
 * }
 *
 * 响应格式:
 * {
 *   capabilities: {
 *     background_tasks_enabled: boolean  // !CLAUDE_CODE_DISABLE_BACKGROUND_TASKS
 *   }
 * }
 *
 * 实现: 读取 process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS 环境变量
 */

module.exports = {
  id: 'get_capabilities',
  description: 'CLI capabilities query control endpoint',
  priority: 150,  // 在其他补丁之后
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // ========================================
    // 找到控制请求处理位置
    // 查找 OA.request.subtype === "mcp_status" 模式
    // ========================================
    traverse(ast, {
      IfStatement(path) {
        if (patchApplied) return;

        const test = path.node.test;

        // 查找 *.request.subtype === "xxx" 模式
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
        // 在 mcp_status 或 mcp_set_servers 附近添加
        if (subtype !== 'mcp_status' && subtype !== 'mcp_set_servers') return;

        // 找到了控制请求处理位置
        const requestVar = obj.object;
        if (!t.isIdentifier(requestVar)) return;
        const requestVarName = requestVar.name;

        // 从 context 获取 responder 函数名，或使用默认值
        let responderName = context.foundVariables?.responderName || 't';

        // 尝试从当前 if 块中找到 responder 调用
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

        // 构建 get_capabilities 处理逻辑
        // 使用 AST 构建而不是解析字符串
        const handlerStatements = [
          // var __disableBackgroundTasks = (process.env.CLAUDE_CODE_DISABLE_BACKGROUND_TASKS || '').toLowerCase();
          t.variableDeclaration('var', [
            t.variableDeclarator(
              t.identifier('__disableBackgroundTasks'),
              t.callExpression(
                t.memberExpression(
                  t.logicalExpression(
                    '||',
                    t.memberExpression(
                      t.memberExpression(t.identifier('process'), t.identifier('env')),
                      t.identifier('CLAUDE_CODE_DISABLE_BACKGROUND_TASKS')
                    ),
                    t.stringLiteral('')
                  ),
                  t.identifier('toLowerCase')
                ),
                []
              )
            )
          ]),
          // var __isDisabled = __disableBackgroundTasks === '1' || __disableBackgroundTasks === 'true' || ...
          t.variableDeclaration('var', [
            t.variableDeclarator(
              t.identifier('__isDisabled'),
              t.logicalExpression(
                '||',
                t.logicalExpression(
                  '||',
                  t.logicalExpression(
                    '||',
                    t.binaryExpression('===', t.identifier('__disableBackgroundTasks'), t.stringLiteral('1')),
                    t.binaryExpression('===', t.identifier('__disableBackgroundTasks'), t.stringLiteral('true'))
                  ),
                  t.binaryExpression('===', t.identifier('__disableBackgroundTasks'), t.stringLiteral('yes'))
                ),
                t.binaryExpression('===', t.identifier('__disableBackgroundTasks'), t.stringLiteral('on'))
              )
            )
          ]),
          // var __backgroundEnabled = !__isDisabled;
          t.variableDeclaration('var', [
            t.variableDeclarator(
              t.identifier('__backgroundEnabled'),
              t.unaryExpression('!', t.identifier('__isDisabled'))
            )
          ]),
          // responder(OA, { capabilities: { background_tasks_enabled: __backgroundEnabled } });
          t.expressionStatement(
            t.callExpression(
              t.identifier(responderName),
              [
                t.identifier(requestVarName),
                t.objectExpression([
                  t.objectProperty(
                    t.identifier('capabilities'),
                    t.objectExpression([
                      t.objectProperty(
                        t.identifier('background_tasks_enabled'),
                        t.identifier('__backgroundEnabled')
                      )
                    ])
                  )
                ])
              ]
            )
          )
        ];

        // 创建新的 if 语句: if (OA.request.subtype === "get_capabilities") { ... }
        const newIfStatement = t.ifStatement(
          t.binaryExpression(
            '===',
            t.memberExpression(
              t.memberExpression(t.identifier(requestVarName), t.identifier('request')),
              t.identifier('subtype')
            ),
            t.stringLiteral('get_capabilities')
          ),
          t.blockStatement(handlerStatements)
        );

        // 插入到 else if 链中
        // 找到最后一个 else if，在其 alternate 上添加
        let currentIf = path.node;
        while (currentIf.alternate && t.isIfStatement(currentIf.alternate)) {
          currentIf = currentIf.alternate;
        }

        // 将新的 if 语句添加为最后的 else if
        if (currentIf.alternate === null) {
          currentIf.alternate = newIfStatement;
          patchApplied = true;
          details.push(`添加 get_capabilities 作为 else if 分支 (responder: ${responderName}, requestVar: ${requestVarName})`);
        } else if (t.isBlockStatement(currentIf.alternate)) {
          // 如果有 else 块，将其移到新 if 的 else 中
          newIfStatement.alternate = currentIf.alternate;
          currentIf.alternate = newIfStatement;
          patchApplied = true;
          details.push(`添加 get_capabilities 在 else 块之前 (responder: ${responderName}, requestVar: ${requestVarName})`);
        }
      }
    });

    if (!patchApplied) {
      return { success: false, error: '未找到控制请求处理位置 (mcp_status/mcp_set_servers)' };
    }

    return { success: true, details };
  }
};
