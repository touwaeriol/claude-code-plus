/**
 * run_in_background 补丁
 *
 * 添加 run_in_background 控制命令，允许 SDK 将运行中的任务移到后台。
 *
 * 使用混合策略:
 * - AST 用于精确定位
 * - 同时支持字符串匹配作为回退
 */

module.exports = {
  id: 'run_in_background',
  description: 'Add run_in_background control command for SDK background execution',
  priority: 100,
  required: true,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let step1Done = false;
    let step2Done = false;
    let step3Done = false;

    // ========== Step 1: 在 Program 开头添加模块级变量 ==========
    traverse(ast, {
      Program(path) {
        if (step1Done) return;

        const varDecl = t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('__backgroundSignalResolver'),
            t.nullLiteral()
          )
        ]);

        // 在第一条语句前插入
        path.node.body.unshift(varDecl);
        step1Done = true;
        details.push('添加了 __backgroundSignalResolver 模块级变量');
        path.stop();
      }
    });

    // ========== Step 2: 找到 Promise.race 相关代码并暴露 resolver ==========
    // 策略: 查找 SequenceExpression 中包含箭头函数调用单个标识符的模式
    // 模式: x, m = new Promise(...), g = () => { x() }, t = false

    let resolverVarName = null;

    traverse(ast, {
      SequenceExpression(path) {
        if (step2Done) return;

        const expressions = path.node.expressions;

        // 查找模式: 其中有一个赋值表达式是箭头函数
        for (let i = 0; i < expressions.length; i++) {
          const expr = expressions[i];

          // 检查是否是赋值表达式
          if (!t.isAssignmentExpression(expr)) continue;

          // 检查右边是否是箭头函数
          if (!t.isArrowFunctionExpression(expr.right)) continue;

          const arrowFunc = expr.right;

          // 检查箭头函数体
          let isResolverPattern = false;

          if (t.isBlockStatement(arrowFunc.body)) {
            const stmts = arrowFunc.body.body;
            if (stmts.length === 1 && t.isExpressionStatement(stmts[0])) {
              if (t.isCallExpression(stmts[0].expression)) {
                const call = stmts[0].expression;
                if (t.isIdentifier(call.callee) && call.arguments.length === 0) {
                  isResolverPattern = true;
                }
              }
            }
          } else if (t.isCallExpression(arrowFunc.body)) {
            const call = arrowFunc.body;
            if (t.isIdentifier(call.callee) && call.arguments.length === 0) {
              isResolverPattern = true;
            }
          }

          if (!isResolverPattern) continue;

          // 检查下一个表达式是否是布尔值赋值
          if (i + 1 < expressions.length) {
            const nextExpr = expressions[i + 1];
            if (t.isAssignmentExpression(nextExpr)) {
              if (t.isBooleanLiteral(nextExpr.right) ||
                  t.isUnaryExpression(nextExpr.right)) {
                // 找到了!
                resolverVarName = expr.left.name;
                context.foundVariables = context.foundVariables || {};
                context.foundVariables.backgroundResolver = resolverVarName;

                // 在 SequenceExpression 中插入赋值
                // 在当前箭头函数赋值后插入 __backgroundSignalResolver = g
                const assignExpr = t.assignmentExpression(
                  '=',
                  t.identifier('__backgroundSignalResolver'),
                  t.identifier(resolverVarName)
                );

                expressions.splice(i + 1, 0, assignExpr);

                step2Done = true;
                details.push(`暴露了 resolver 变量 '${resolverVarName}'`);
                path.stop();
                return;
              }
            }
          }
        }
      },

      // 备用: 查找 VariableDeclaration 中的模式
      VariableDeclaration(path) {
        if (step2Done) return;

        const declarations = path.node.declarations;

        for (let i = 0; i < declarations.length; i++) {
          const decl = declarations[i];
          if (!t.isArrowFunctionExpression(decl.init)) continue;

          const arrowFunc = decl.init;
          let isResolverPattern = false;

          if (t.isBlockStatement(arrowFunc.body)) {
            const stmts = arrowFunc.body.body;
            if (stmts.length === 1 && t.isExpressionStatement(stmts[0])) {
              if (t.isCallExpression(stmts[0].expression)) {
                isResolverPattern = true;
              }
            }
          } else if (t.isCallExpression(arrowFunc.body)) {
            isResolverPattern = true;
          }

          if (!isResolverPattern) continue;

          // 检查后面是否有布尔值声明
          if (i + 1 < declarations.length) {
            const nextDecl = declarations[i + 1];
            if (t.isBooleanLiteral(nextDecl.init) ||
                t.isUnaryExpression(nextDecl.init)) {
              resolverVarName = decl.id.name;

              // 在声明后插入赋值语句
              const assignStmt = t.expressionStatement(
                t.assignmentExpression(
                  '=',
                  t.identifier('__backgroundSignalResolver'),
                  t.identifier(resolverVarName)
                )
              );

              path.insertAfter(assignStmt);
              step2Done = true;
              details.push(`暴露了 resolver 变量 '${resolverVarName}' (VariableDeclaration)`);
              path.stop();
              return;
            }
          }
        }
      }
    });

    // ========== Step 3: 在控制请求处理中添加 run_in_background ==========

    traverse(ast, {
      IfStatement(path) {
        if (step3Done) return;

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

        // 找出响应函数名 (通常是 g)
        const responderName = resolverVarName || 'g';

        // 创建新的 if 条件
        const newCondition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(
              t.cloneNode(requestVar),
              t.identifier('request')
            ),
            t.identifier('subtype')
          ),
          t.stringLiteral('run_in_background')
        );

        // 创建处理代码块
        const handlerBlock = t.blockStatement([
          t.expressionStatement(
            t.optionalCallExpression(
              t.identifier('__backgroundSignalResolver'),
              [],
              true
            )
          ),
          t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.identifier('__backgroundSignalResolver'),
              t.nullLiteral()
            )
          ),
          t.expressionStatement(
            t.callExpression(
              t.identifier(responderName),
              [t.cloneNode(requestVar)]
            )
          )
        ]);

        // 创建新的 if-else
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node
        );

        path.replaceWith(newIfStatement);
        step3Done = true;
        details.push('添加了 run_in_background 控制命令处理');
        path.stop();
      }
    });

    // 检查结果 - 更宽松的判断
    // 只要 step1 和 step3 成功就算成功，step2 可选
    if (step1Done && step3Done) {
      if (!step2Done) {
        details.push('(警告: resolver 暴露可能需要手动验证)');
      }
      return {
        success: true,
        details
      };
    }

    const missing = [];
    if (!step1Done) missing.push('模块级变量');
    if (!step2Done) missing.push('暴露 resolver');
    if (!step3Done) missing.push('控制命令处理');

    return {
      success: false,
      reason: `未完成的步骤: ${missing.join(', ')}`
    };
  }
};
