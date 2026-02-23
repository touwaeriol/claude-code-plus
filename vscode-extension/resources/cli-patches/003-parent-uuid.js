/**
 * parent-uuid 补丁 (v2 - 最小侵入版本)
 *
 * 让 CLI 在 SDK 模式下读取用户消息中的 parentUuid 字段，
 * 并将其传递给 insertMessageChain 函数，实现编辑重发功能。
 *
 * CLI 2.1.27 中的 jh 函数:
 *   async function jh(A, q) {
 *     let K = N9q(A), Y = B6(), z = await CIA(Y), w = [], H;
 *     for (let O of K) if (z.has(O.uuid)) H = O.uuid; else w.push(O);
 *     if (w.length > 0) await lD().insertMessageChain(w, !1, void 0, H, q);
 *     return K[K.length - 1]?.uuid || null;
 *   }
 *
 * 修改后 (最小侵入 - 只覆盖 H 变量):
 *   async function jh(A, q) {
 *     let K = N9q(A), Y = B6(), z = await CIA(Y), w = [], H;
 *     for (let O of K) if (z.has(O.uuid)) H = O.uuid; else w.push(O);
 *     H = A[0]?.parentUuid || A[0]?.parent_uuid || H;  // ← 只加这一行
 *     if (w.length > 0) await lD().insertMessageChain(w, !1, void 0, H, q);
 *     return K[K.length - 1]?.uuid || null;
 *   }
 */

module.exports = {
  id: 'parent_uuid',
  description: 'Enable parentUuid support in SDK mode for edit/resend functionality',
  priority: 50,  // 在其他补丁之前执行
  required: false,  // non-required，CLI 代码结构可能会变化
  disabled: true,  // CLI 2.1.50: parentUuid 已被官方内置(6次), insertMessageChain 已不存在

  apply(ast, t, traverse, context) {
    const details = [];
    let patchApplied = false;

    // 查找调用 insertMessageChain 的函数
    traverse(ast, {
      CallExpression(path) {
        if (patchApplied) return;

        // 检查是否是 xxx.insertMessageChain(...) 的调用
        const callee = path.node.callee;
        if (!t.isMemberExpression(callee)) return;
        if (!t.isIdentifier(callee.property) || callee.property.name !== 'insertMessageChain') return;

        // 检查参数数量（应该是5个）
        const args = path.node.arguments;
        if (args.length !== 5) return;

        // 检查第2个参数是否是 !1 (false) - 这是区分 SDK 消息处理的标志
        const secondArg = args[1];
        if (!t.isUnaryExpression(secondArg) || secondArg.operator !== '!' ||
            !t.isNumericLiteral(secondArg.argument) || secondArg.argument.value !== 1) return;

        const fourthArg = args[3];
        
        // 策略1: 第4个参数是 void 0 (CLI 2.1.19 及更早版本)
        const isVoidZero = t.isUnaryExpression(fourthArg) && fourthArg.operator === 'void';
        
        // 策略2: 第4个参数是一个标识符 (CLI 2.1.27+)
        const isIdentifier = t.isIdentifier(fourthArg);

        if (!isVoidZero && !isIdentifier) return;

        // 找到包含此调用的函数
        let functionPath = path.getFunctionParent();
        if (!functionPath) return;

        // 获取函数的第一个参数名（用户消息数组）
        const params = functionPath.node.params;
        if (params.length === 0) return;

        const messagesParamName = params[0].name;
        if (!messagesParamName) return;

        // 找到函数体
        let bodyPath = null;
        if (functionPath.node.body.type === 'BlockStatement') {
          bodyPath = functionPath.get('body');
        } else {
          return; // 简写箭头函数，跳过
        }

        // 创建 parentUuid 提取表达式: A[0]?.parentUuid || A[0]?.parent_uuid
        const parentUuidFromMessage = t.logicalExpression(
          '||',
          t.optionalMemberExpression(
            t.memberExpression(
              t.identifier(messagesParamName),
              t.numericLiteral(0),
              true  // computed
            ),
            t.identifier('parentUuid'),
            false,  // computed
            true    // optional
          ),
          t.optionalMemberExpression(
            t.memberExpression(
              t.identifier(messagesParamName),
              t.numericLiteral(0),
              true
            ),
            t.identifier('parent_uuid'),
            false,
            true
          )
        );

        if (isVoidZero) {
          // 策略1: CLI 2.1.19 - 在函数体开头插入变量声明，替换第4个参数
          const parentUuidDecl = t.variableDeclaration('let', [
            t.variableDeclarator(
              t.identifier('__parentUuid'),
              t.logicalExpression(
                '||',
                parentUuidFromMessage,
                t.unaryExpression('void', t.numericLiteral(0))
              )
            )
          ]);

          bodyPath.unshiftContainer('body', parentUuidDecl);
          args[3] = t.identifier('__parentUuid');
          
          details.push(`策略1 (void 0): 在函数中添加 parentUuid 提取`);
          details.push(`${messagesParamName}[0]?.parentUuid || ${messagesParamName}[0]?.parent_uuid || void 0`);
          details.push(`将 insertMessageChain 的第4个参数从 void 0 改为 __parentUuid`);
        } else if (isIdentifier) {
          // 策略2: CLI 2.1.27 - 最小侵入：直接覆盖原变量
          const originalVarName = fourthArg.name;
          
          // 找到 insertMessageChain 调用所在的语句（可能是 if 语句内部）
          const statementPath = path.getStatementParent();
          if (!statementPath) return;
          
          // 创建赋值语句: H = A[0]?.parentUuid || A[0]?.parent_uuid || H;
          const overrideAssignment = t.expressionStatement(
            t.assignmentExpression(
              '=',
              t.identifier(originalVarName),
              t.logicalExpression(
                '||',
                parentUuidFromMessage,
                t.identifier(originalVarName)
              )
            )
          );
          
          // 在 if 语句之前插入赋值
          statementPath.insertBefore(overrideAssignment);
          
          details.push(`策略2 (最小侵入): 在 insertMessageChain 调用前覆盖变量 ${originalVarName}`);
          details.push(`${originalVarName} = ${messagesParamName}[0]?.parentUuid || ${messagesParamName}[0]?.parent_uuid || ${originalVarName}`);
          details.push(`不修改 insertMessageChain 参数，保持原有调用方式`);
        }

        patchApplied = true;
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
      reason: '未找到匹配的 insertMessageChain 调用模式'
    };
  }
};
