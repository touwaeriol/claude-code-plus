/**
 * run_in_background 补丁
 *
 * 添加 run_in_background 控制命令，允许 SDK 将运行中的任务移到后台。
 *
 * 使用混合策略:
 * - AST 用于精确定位
 * - 同时支持字符串匹配作为回退
 *
 * 支持两种后台执行机制:
 * - 子代理 (Task tool): 使用 __backgroundSignalResolver 解锁 Promise.race
 * - Bash 命令: 使用 __bashBackgroundCallback 调用 M() 设置 backgroundTaskId
 */

module.exports = {
  id: 'run_in_background',
  description: 'Add run_in_background control command for SDK background execution (supports both subagent and Bash)',
  priority: 100,
  required: true,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let step1Done = false;
    let step2Done = false;
    let step3Done = false;
    let step4Done = false;  // 新增: Bash 后台回调暴露

    // ========== Step 1: 在 Program 开头添加模块级变量 ==========
    traverse(ast, {
      Program(path) {
        if (step1Done) return;

        // 添加两个模块级变量: 一个用于子代理，一个用于 Bash
        const varDecl = t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('__backgroundSignalResolver'),
            t.nullLiteral()
          ),
          t.variableDeclarator(
            t.identifier('__bashBackgroundCallback'),
            t.nullLiteral()
          )
        ]);

        // 在第一条语句前插入
        path.node.body.unshift(varDecl);
        step1Done = true;
        details.push('添加了 __backgroundSignalResolver 和 __bashBackgroundCallback 模块级变量');
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

        // 创建处理代码块 - 同时处理子代理和 Bash 两种后台机制
        const handlerBlock = t.blockStatement([
          // 1. 调用子代理的 resolver (如果存在)
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
          // 2. 调用 Bash 的后台回调 (如果存在)
          t.expressionStatement(
            t.optionalCallExpression(
              t.identifier('__bashBackgroundCallback'),
              [],
              true
            )
          ),
          t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.identifier('__bashBackgroundCallback'),
              t.nullLiteral()
            )
          ),
          // 3. 发送响应
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
        details.push('添加了 run_in_background 控制命令处理 (支持子代理和 Bash)');
        path.stop();
      }
    });

    // ========== Step 4: 在 Bash 工具执行循环中暴露后台回调 ==========
    // 策略: 查找包含 onBackground 属性的对象表达式，其中调用了 S81 组件
    // 模式: G({jsx: createElement(S81, {onBackground: M}), shouldHidePromptInput: false, ...})
    // 然后在该调用之前插入 __bashBackgroundCallback = M
    // 注意：需要跳过子代理的回调（已通过 Step 2 暴露为 __backgroundSignalResolver）

    let bashCallbacksFound = [];

    traverse(ast, {
      CallExpression(path) {
        // 检查是否是 createElement 调用
        const callee = path.node.callee;
        if (!t.isMemberExpression(callee)) return;
        if (!t.isIdentifier(callee.property) || callee.property.name !== 'createElement') return;

        // 检查参数
        const args = path.node.arguments;
        if (args.length < 2) return;

        // 第一个参数应该是 S81 (或类似标识符)
        if (!t.isIdentifier(args[0])) return;

        // 第二个参数应该是包含 onBackground 的对象
        if (!t.isObjectExpression(args[1])) return;

        const props = args[1].properties;
        let onBackgroundValue = null;

        for (const prop of props) {
          if (t.isObjectProperty(prop) &&
              t.isIdentifier(prop.key) &&
              prop.key.name === 'onBackground') {
            onBackgroundValue = prop.value;
            break;
          }
        }

        if (!onBackgroundValue) return;
        if (!t.isIdentifier(onBackgroundValue)) return;

        const callbackName = onBackgroundValue.name;

        // 检查外层调用的参数对象是否包含 shouldHidePromptInput 或 showSpinner
        // 模式: G({jsx: createElement(...), shouldHidePromptInput: false, ...})
        let hasBashIndicator = false;

        // 向上查找，看是否在 G({jsx: ..., shouldHidePromptInput: ...}) 模式中
        let parentPath = path.parentPath;
        while (parentPath && !hasBashIndicator) {
          if (t.isObjectExpression(parentPath.node)) {
            const parentProps = parentPath.node.properties;
            hasBashIndicator = parentProps.some(prop =>
              t.isObjectProperty(prop) &&
              t.isIdentifier(prop.key) &&
              (prop.key.name === 'shouldHidePromptInput' || prop.key.name === 'showSpinner')
            );
          }
          parentPath = parentPath.parentPath;
        }

        if (!hasBashIndicator) return;

        // 找到包含这个调用的语句
        let statementPath = path;
        while (statementPath && !statementPath.isStatement()) {
          statementPath = statementPath.parentPath;
        }

        if (!statementPath) return;

        // 记录找到的回调
        bashCallbacksFound.push({
          callbackName,
          statementPath,
          path
        });
      }
    });

    // 处理找到的所有回调（可能有多个：子代理和 Bash）
    for (const item of bashCallbacksFound) {
      const { callbackName, statementPath } = item;

      // 跳过已通过 Step 2 暴露的子代理 resolver
      if (context.foundVariables?.backgroundResolver === callbackName) {
        details.push(`跳过子代理回调 '${callbackName}' (已通过 Step 2 暴露)`);
        continue;
      }

      // 创建赋值语句: __bashBackgroundCallback = M
      const assignExpr = t.expressionStatement(
        t.assignmentExpression(
          '=',
          t.identifier('__bashBackgroundCallback'),
          t.identifier(callbackName)
        )
      );

      // 在语句前插入
      statementPath.insertBefore(assignExpr);

      context.foundVariables = context.foundVariables || {};
      context.foundVariables.bashBackgroundCallback = callbackName;

      step4Done = true;
      details.push(`暴露了 Bash 后台回调 '${callbackName}'`);
    }

    if (bashCallbacksFound.length > 0 && !step4Done) {
      details.push(`(警告: 找到 ${bashCallbacksFound.length} 个 onBackground 回调，但都是子代理的)`);
    }

    // 检查结果 - 更宽松的判断
    // 只要 step1 和 step3 成功就算成功，step2 和 step4 可选
    if (step1Done && step3Done) {
      if (!step2Done) {
        details.push('(警告: 子代理 resolver 暴露可能需要手动验证)');
      }
      if (!step4Done) {
        details.push('(警告: Bash 后台回调暴露可能需要手动验证)');
      }
      return {
        success: true,
        details
      };
    }

    const missing = [];
    if (!step1Done) missing.push('模块级变量');
    if (!step2Done) missing.push('暴露子代理 resolver');
    if (!step3Done) missing.push('控制命令处理');
    if (!step4Done) missing.push('暴露 Bash 后台回调');

    return {
      success: false,
      reason: `未完成的步骤: ${missing.join(', ')}`
    };
  }
};
