/**
 * run_in_background 补丁
 *
 * 添加 run_in_background 控制命令，允许 SDK 将运行中的任务移到后台。
 *
 * 实现原理：
 * - 当收到 run_in_background 控制请求时，模拟发送 Ctrl+B 按键事件
 * - CLI 内部的 Ink 框架会捕获这个按键，触发当前活跃组件的 onBackground 回调
 * - 这样无论是 Bash 命令还是子代理，都能正确处理后台执行
 *
 * 优点：
 * - 不需要暴露内部变量或回调
 * - 不需要区分是哪个任务（CLI 自己知道当前活跃的是哪个）
 * - 代码简洁，与 CLI 原生 Ctrl+B 行为一致
 */

module.exports = {
  id: 'run_in_background',
  description: 'Add run_in_background control command that simulates Ctrl+B keypress',
  priority: 100,
  required: true,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let stepDone = false;

    // 在控制请求处理中添加 run_in_background
    // 查找 if (*.request.subtype === "interrupt") 模式
    traverse(ast, {
      IfStatement(path) {
        if (stepDone) return;

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

        // 找到了! 获取变量名 (如 k)
        const requestVar = obj.object;

        // 找出响应函数名 - 查找 consequent 中的函数调用
        let responderName = 'g';  // 默认值
        if (t.isBlockStatement(path.node.consequent)) {
          for (const stmt of path.node.consequent.body) {
            if (t.isExpressionStatement(stmt) && t.isCallExpression(stmt.expression)) {
              const callee = stmt.expression.callee;
              if (t.isIdentifier(callee)) {
                responderName = callee.name;
                break;
              }
            }
          }
        }

        // 创建新的 if 条件: *.request.subtype === "run_in_background"
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

        // 创建处理代码块 - 模拟 Ctrl+B 按键
        const handlerBlock = t.blockStatement([
          // 1. 模拟 Ctrl+B 按键
          //    \x02 是 Ctrl+B 的 ASCII 码
          //    CLI 2.0.71 中 Ink 框架使用 readable 事件和 stdin.read() 来处理输入
          //    所以需要使用 stdin.unshift() 将数据放入缓冲区，然后触发 readable 事件
          //    这样 handleReadable 回调会调用 stdin.read() 读取到我们注入的数据
          t.expressionStatement(
            t.callExpression(
              t.memberExpression(
                t.memberExpression(
                  t.identifier('process'),
                  t.identifier('stdin')
                ),
                t.identifier('unshift')
              ),
              [
                t.stringLiteral('\x02')  // Ctrl+B
              ]
            )
          ),
          // 2. 触发 readable 事件，让 Ink 的 handleReadable 回调处理输入
          t.expressionStatement(
            t.callExpression(
              t.memberExpression(
                t.memberExpression(
                  t.identifier('process'),
                  t.identifier('stdin')
                ),
                t.identifier('emit')
              ),
              [
                t.stringLiteral('readable')
              ]
            )
          ),
          // 3. 发送成功响应
          t.expressionStatement(
            t.callExpression(
              t.identifier(responderName),
              [t.cloneNode(requestVar)]
            )
          )
        ]);

        // 创建新的 if-else: 将新的 if 放在原来的 if 前面
        const newIfStatement = t.ifStatement(
          newCondition,
          handlerBlock,
          path.node  // 原来的 if 作为 else
        );

        path.replaceWith(newIfStatement);
        stepDone = true;
        details.push('添加了 run_in_background 控制命令处理 (模拟 Ctrl+B)');
        path.stop();
      }
    });

    if (stepDone) {
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
