/**
 * skill-parent-tool-use-id 补丁
 *
 * 为 Skill 工具的内部消息添加 parent_tool_use_id 支持
 *
 * 问题：
 * 1. id2 函数只为 user 消息添加 sourceToolUseID，遗漏了 assistant 消息
 * 2. Ts5 输出函数中 parent_tool_use_id 被硬编码为 null
 *
 * 修复：
 * 1. 修改 id2 函数，同时为 user 和 assistant 消息添加 sourceToolUseID
 * 2. 修改 Ts5 函数，将 parent_tool_use_id:null 改为 Q.sourceToolUseID||null
 */

module.exports = {
  id: 'skill_parent_tool_use_id',
  description: 'Add parent_tool_use_id support for Skill internal messages',
  priority: 55,
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let id2Fixed = false;
    let ts5Fixed = false;

    // ========== 第一部分：修复 id2 函数 ==========
    // id2 函数只为 user 消息添加 sourceToolUseID，需要同时处理 assistant
    traverse(ast, {
      FunctionDeclaration(path) {
        if (id2Fixed) return;
        if (checkAndPatchId2(path, t, details)) {
          id2Fixed = true;
        }
      },
      FunctionExpression(path) {
        if (id2Fixed) return;
        if (checkAndPatchId2(path, t, details)) {
          id2Fixed = true;
        }
      }
    });

    // ========== 第二部分：修复 Ts5 输出函数 ==========
    // Ts5 函数中 parent_tool_use_id 被硬编码为 null
    traverse(ast, {
      FunctionDeclaration(path) {
        if (ts5Fixed) return;
        if (!path.node.generator) return;

        // 检查函数体是否包含关键代码模式
        let hasSwitchAType = false;
        let hasAssistantCase = false;
        let hasUserCase = false;
        let hasProgressCase = false;
        let hasParentToolUseID = false;

        path.traverse({
          SwitchStatement(innerPath) {
            const disc = innerPath.node.discriminant;
            if (t.isMemberExpression(disc) &&
                t.isIdentifier(disc.object) &&
                t.isIdentifier(disc.property) &&
                disc.property.name === 'type') {
              hasSwitchAType = true;
            }
          },
          SwitchCase(innerPath) {
            const test = innerPath.node.test;
            if (t.isStringLiteral(test)) {
              if (test.value === 'assistant') hasAssistantCase = true;
              if (test.value === 'user') hasUserCase = true;
              if (test.value === 'progress') hasProgressCase = true;
            }
          },
          MemberExpression(innerPath) {
            if (t.isIdentifier(innerPath.node.property) &&
                innerPath.node.property.name === 'parentToolUseID') {
              hasParentToolUseID = true;
            }
          }
        });

        // 确认是否 Ts5 函数
        if (!hasSwitchAType || !hasAssistantCase || !hasUserCase || 
            !hasProgressCase || !hasParentToolUseID) {
          return;
        }

        const ts5FuncName = path.node.id?.name || 'anonymous';
        details.push(`找到 Ts5 函数: ${ts5FuncName}`);

        // 查找并修改 assistant 和 user case 中的 parent_tool_use_id:null
        let modifiedCount = 0;

        path.traverse({
          SwitchCase(casePath) {
            const test = casePath.node.test;
            if (!t.isStringLiteral(test)) return;
            
            // 只处理 assistant 和 user case
            if (test.value !== 'assistant' && test.value !== 'user') return;

            casePath.traverse({
              ObjectProperty(propPath) {
                const key = propPath.node.key;
                const value = propPath.node.value;

                // 检查是否是 parent_tool_use_id: null
                if (t.isIdentifier(key) && key.name === 'parent_tool_use_id' &&
                    t.isNullLiteral(value)) {
                  
                  // 找到循环变量名
                  let loopVarName = 'Q';
                  let parent = propPath.parentPath;
                  while (parent && !t.isForStatement(parent.node) && 
                         !t.isForOfStatement(parent.node)) {
                    parent = parent.parentPath;
                  }

                  if (parent && t.isForOfStatement(parent.node)) {
                    const left = parent.node.left;
                    if (t.isVariableDeclaration(left) && left.declarations.length > 0) {
                      const decl = left.declarations[0];
                      if (t.isIdentifier(decl.id)) {
                        loopVarName = decl.id.name;
                      }
                    }
                  }

                  // 将 parent_tool_use_id:null 改为 Q.sourceToolUseID||null
                  propPath.node.value = t.logicalExpression(
                    '||',
                    t.memberExpression(
                      t.identifier(loopVarName),
                      t.identifier('sourceToolUseID')
                    ),
                    t.nullLiteral()
                  );

                  modifiedCount++;
                  details.push(`修改 ${test.value} case: ${loopVarName}.sourceToolUseID||null`);
                }
              }
            });
          }
        });

        if (modifiedCount > 0) {
          ts5Fixed = true;
          details.push(`共修改了 ${modifiedCount} 处 parent_tool_use_id`);
          
          context.foundVariables = context.foundVariables || {};
          context.foundVariables.ts5FuncName = ts5FuncName;
        }
      }
    });

    // 返回结果
    if (id2Fixed && ts5Fixed) {
      return { success: true, details };
    } else if (id2Fixed || ts5Fixed) {
      return { 
        success: true, 
        details: [...details, `部分成功: id2=${id2Fixed}, ts5=${ts5Fixed}`] 
      };
    }

    return {
      success: false,
      reason: '未找到 id2 或 Ts5 函数'
    };
  }
};

// 检查并修复 id2 函数
function checkAndPatchId2(path, t, details) {
  const node = path.node;
  
  // 检查参数数量
  if (!node.params || node.params.length !== 2) return false;
  
  const paramA = node.params[0];
  const paramQ = node.params[1];
  if (!t.isIdentifier(paramA) || !t.isIdentifier(paramQ)) return false;
  
  const paramAName = paramA.name;
  const paramQName = paramQ.name;

  // 检查关键特征
  let hasReturnIfNotQ = false;
  let hasMapCall = false;
  let hasUserTypeCheck = false;
  let userTypeCheckPath = null;

  path.traverse({
    IfStatement(innerPath) {
      const test = innerPath.node.test;
      if (t.isUnaryExpression(test) && test.operator === '!' &&
          t.isIdentifier(test.argument) && test.argument.name === paramQName) {
        hasReturnIfNotQ = true;
      }
    },
    CallExpression(innerPath) {
      const callee = innerPath.node.callee;
      if (t.isMemberExpression(callee) &&
          t.isIdentifier(callee.object) && callee.object.name === paramAName &&
          t.isIdentifier(callee.property) && callee.property.name === 'map') {
        hasMapCall = true;
      }
    },
    BinaryExpression(innerPath) {
      const node = innerPath.node;
      if (node.operator !== '===') return;
      
      const left = node.left;
      const right = node.right;
      
      if (t.isMemberExpression(left) &&
          t.isIdentifier(left.property) && left.property.name === 'type' &&
          t.isStringLiteral(right) && right.value === 'user') {
        
        // 检查是否已修改
        const parent = innerPath.parentPath;
        if (t.isLogicalExpression(parent.node) && parent.node.operator === '||') {
          const logicalRight = parent.node.right;
          if (t.isBinaryExpression(logicalRight) &&
              t.isStringLiteral(logicalRight.right) && 
              logicalRight.right.value === 'assistant') {
            return; // 已修改
          }
        }
        
        hasUserTypeCheck = true;
        userTypeCheckPath = innerPath;
      }
    }
  });

  if (!hasReturnIfNotQ || !hasMapCall || !hasUserTypeCheck || !userTypeCheckPath) {
    return false;
  }

  const funcName = node.id?.name || 'anonymous';
  details.push(`找到 id2 函数: ${funcName}`);

  const loopVarName = userTypeCheckPath.node.left.object.name;
  
  // 创建 B.type === "assistant"
  const assistantCheck = t.binaryExpression(
    '===',
    t.memberExpression(
      t.identifier(loopVarName),
      t.identifier('type')
    ),
    t.stringLiteral('assistant')
  );

  // 替换为 (B.type === "user" || B.type === "assistant")
  const newExpr = t.logicalExpression('||', userTypeCheckPath.node, assistantCheck);
  userTypeCheckPath.replaceWith(newExpr);
  
  details.push(`修改 id2: ${loopVarName}.type==="user"||${loopVarName}.type==="assistant"`);
  
  return true;
}
