/**
 * parent-uuid 补丁
 *
 * 让 CLI 在 SDK 模式下读取用户消息中的 parentUuid 字段，
 * 并将其传递给 insertMessageChain 函数，实现编辑重发功能。
 *
 * 实现原理：
 * - 找到调用 insertMessageChain 的函数（Q2A 或类似函数）
 * - 在调用前提取用户消息中的 parentUuid
 * - 将 parentUuid 传递给 insertMessageChain 的第4个参数
 *
 * 原始代码：
 *   async function Q2A(A, Q) {
 *     let B = f02(A);
 *     return await dR().insertMessageChain(B, !1, void 0, void 0, Q);
 *   }
 *
 * 修改后：
 *   async function Q2A(A, Q) {
 *     let B = f02(A);
 *     let __parentUuid = A[0]?.parentUuid || A[0]?.parent_uuid || void 0;
 *     return await dR().insertMessageChain(B, !1, void 0, __parentUuid, Q);
 *   }
 */

module.exports = {
  id: 'parent_uuid',
  description: 'Enable parentUuid support in SDK mode for edit/resend functionality',
  priority: 50,  // 在其他补丁之前执行
  required: true,
  disabled: false,

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

        // 检查第4个参数是否是 void 0（即 parentUuid 未被使用）
        const fourthArg = args[3];
        if (!t.isUnaryExpression(fourthArg) || fourthArg.operator !== 'void') return;

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

        // 在函数体开头插入 parentUuid 提取代码
        // let __parentUuid = A[0]?.parentUuid || A[0]?.parent_uuid || void 0;
        const parentUuidDecl = t.variableDeclaration('let', [
          t.variableDeclarator(
            t.identifier('__parentUuid'),
            t.logicalExpression(
              '||',
              t.logicalExpression(
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
              ),
              t.unaryExpression('void', t.numericLiteral(0))
            )
          )
        ]);

        // 插入到函数体开头
        bodyPath.unshiftContainer('body', parentUuidDecl);

        // 替换 insertMessageChain 的第4个参数
        args[3] = t.identifier('__parentUuid');

        patchApplied = true;
        details.push(`在函数中添加 parentUuid 提取: ${messagesParamName}[0]?.parentUuid`);
        details.push(`将 insertMessageChain 的第4个参数从 void 0 改为 __parentUuid`);

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
      reason: '未找到 insertMessageChain 调用或其第4个参数已不是 void 0'
    };
  }
};
