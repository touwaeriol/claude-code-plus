/**
 * run_to_background 补丁 v4 (简化版)
 *
 * 直接调用官方内部函数实现后台化，无需维护自己的 Map。
 *
 * 官方内部函数：
 * - iV1(getState, setState): 批量后台化所有任务
 * - Me5(taskId, getState, setState): 后台化单个 Bash
 * - R42(taskId, getState, setState): 后台化单个 Agent
 * - wt(task): 判断任务是否是 Bash
 * - Jr(task): 判断任务是否是 Agent
 *
 * 控制请求上下文变量：
 * - X = getAppState
 * - I = setAppState
 *
 * 支持的控制命令：
 * - run_to_background: { task_id?: string }
 *   - task_id 不传: 批量后台化所有任务
 *   - task_id 传入: 后台化指定任务（自动判断类型）
 */

module.exports = {
  id: 'run_to_background',
  description: 'Add run_to_background control command using official internal functions',
  priority: 101,
  required: false,
  disabled: false,

  apply(ast, t, traverse, context) {
    const details = [];
    let step1Done = false;  // 找到关键函数
    let step2Done = false;  // 添加控制命令

    // 函数名变量
    let iV1FuncName = null;   // 批量后台化
    let Me5FuncName = null;   // 单个 Bash 后台化
    let R42FuncName = null;   // 单个 Agent 后台化
    let wtFuncName = null;    // 判断是否是 Bash
    let JrFuncName = null;    // 判断是否是 Agent

    // ========================================
    // Step 1: 找到关键函数名
    // ========================================

    traverse(ast, {
      FunctionDeclaration(path) {
        const funcName = path.node.id?.name;
        if (!funcName) return;

        // 找 iV1: function iV1(A, Q) { let B = A(), G = Object.keys(B.tasks).filter(...) }
        // 特征：2 参数，内部有 Object.keys(xxx.tasks).filter
        if (!iV1FuncName && path.node.params.length === 2) {
          let hasTasksFilter = false;
          let callsMe5 = false;
          let callsR42 = false;
          
          path.traverse({
            CallExpression(callPath) {
              const callee = callPath.node.callee;
              
              // Object.keys(xxx.tasks).filter
              if (t.isMemberExpression(callee) && 
                  t.isIdentifier(callee.property) && 
                  callee.property.name === 'filter') {
                const obj = callee.object;
                if (t.isCallExpression(obj) &&
                    t.isMemberExpression(obj.callee) &&
                    t.isIdentifier(obj.callee.object) &&
                    obj.callee.object.name === 'Object' &&
                    t.isIdentifier(obj.callee.property) &&
                    obj.callee.property.name === 'keys') {
                  const keysArg = obj.arguments[0];
                  if (t.isMemberExpression(keysArg) &&
                      t.isIdentifier(keysArg.property) &&
                      keysArg.property.name === 'tasks') {
                    hasTasksFilter = true;
                  }
                }
              }
              
              // 内部调用 Me5 和 R42
              if (t.isIdentifier(callee) && callee.name.match(/^[A-Z][a-z0-9]*[0-9]$/)) {
                if (!Me5FuncName) {
                  // 先假设第一个匹配的是 Me5，稍后验证
                }
              }
            }
          });
          
          if (hasTasksFilter) {
            // 再检查是否调用了两个不同的函数
            const calledFuncs = new Set();
            path.traverse({
              CallExpression(callPath) {
                const callee = callPath.node.callee;
                if (t.isIdentifier(callee) && callPath.node.arguments.length === 3) {
                  calledFuncs.add(callee.name);
                }
              }
            });
            
            if (calledFuncs.size >= 2) {
              iV1FuncName = funcName;
              // 提取两个被调用的函数名
              const funcArr = Array.from(calledFuncs);
              Me5FuncName = funcArr[0];
              R42FuncName = funcArr[1];
            }
          }
        }

        // 找 wt: 判断 Bash 类型的函数
        // 特征：返回 task.type === "bash" 或类似
        if (!wtFuncName && path.node.params.length === 1) {
          let returnsBashCheck = false;
          path.traverse({
            BinaryExpression(binPath) {
              if (binPath.node.operator === '===' || binPath.node.operator === '==') {
                const left = binPath.node.left;
                const right = binPath.node.right;
                
                if ((t.isStringLiteral(left) && left.value === 'bash') ||
                    (t.isStringLiteral(right) && right.value === 'bash')) {
                  returnsBashCheck = true;
                }
              }
            }
          });
          if (returnsBashCheck) {
            wtFuncName = funcName;
          }
        }

        // 找 Jr: 判断 Agent 类型的函数
        // 特征：返回 task.type === "agent" 或类似
        if (!JrFuncName && path.node.params.length === 1) {
          let returnsAgentCheck = false;
          path.traverse({
            BinaryExpression(binPath) {
              if (binPath.node.operator === '===' || binPath.node.operator === '==') {
                const left = binPath.node.left;
                const right = binPath.node.right;
                
                if ((t.isStringLiteral(left) && left.value === 'agent') ||
                    (t.isStringLiteral(right) && right.value === 'agent')) {
                  returnsAgentCheck = true;
                }
              }
            }
          });
          if (returnsAgentCheck) {
            JrFuncName = funcName;
          }
        }
      }
    });

    // 如果没有通过特征找到函数，尝试更宽松的方式
    if (!iV1FuncName) {
      // 备用方案：找包含 isBackgrounded 检查的函数
      traverse(ast, {
        FunctionDeclaration(path) {
          if (iV1FuncName) return;
          if (path.node.params.length !== 2) return;
          
          let hasIsBackgrounded = false;
          let hasForOf = false;
          
          path.traverse({
            MemberExpression(memPath) {
              if (t.isIdentifier(memPath.node.property) && 
                  memPath.node.property.name === 'isBackgrounded') {
                hasIsBackgrounded = true;
              }
            },
            ForOfStatement() {
              hasForOf = true;
            }
          });
          
          if (hasIsBackgrounded && hasForOf) {
            iV1FuncName = path.node.id?.name;
          }
        }
      });
    }

    if (iV1FuncName) {
      details.push(`找到 iV1 函数: "${iV1FuncName}"`);
      step1Done = true;
    } else {
      details.push('警告: 未找到 iV1 函数，将使用简化实现');
    }
    
    if (Me5FuncName) details.push(`找到 Me5 函数: "${Me5FuncName}"`);
    if (R42FuncName) details.push(`找到 R42 函数: "${R42FuncName}"`);
    if (wtFuncName) details.push(`找到 wt 函数: "${wtFuncName}"`);
    if (JrFuncName) details.push(`找到 Jr 函数: "${JrFuncName}"`);

    // ========================================
    // Step 2: 添加控制命令处理
    // ========================================
    const parser = require('@babel/parser');
    
    traverse(ast, {
      IfStatement(path) {
        if (step2Done) return;
        
        const test = path.node.test;
        if (!t.isBinaryExpression(test) || test.operator !== '===') return;
        
        const left = test.left;
        if (!t.isMemberExpression(left)) return;
        if (!t.isIdentifier(left.property) || left.property.name !== 'subtype') return;
        
        const obj = left.object;
        if (!t.isMemberExpression(obj)) return;
        if (!t.isIdentifier(obj.property) || obj.property.name !== 'request') return;
        
        if (!t.isStringLiteral(test.right) || test.right.value !== 'interrupt') return;
        
        const requestVar = obj.object;
        if (!t.isIdentifier(requestVar)) return;
        
        // 找响应函数名（通常是 s）
        let responderName = 's';
        if (t.isBlockStatement(path.node.consequent)) {
          for (const stmt of path.node.consequent.body) {
            if (t.isExpressionStatement(stmt) && t.isCallExpression(stmt.expression)) {
              if (t.isIdentifier(stmt.expression.callee)) {
                responderName = stmt.expression.callee.name;
                break;
              }
            }
          }
        }

        // 找控制请求处理所在函数的上下文变量
        // 需要找 X (getAppState) 和 I (setAppState)
        // 它们通常是当前函数的参数或在作用域中
        let getStateVar = 'X';
        let setStateVar = 'I';
        
        // 尝试从父函数的参数中找到
        let parentFunc = path.findParent(p => 
          t.isFunctionDeclaration(p.node) || 
          t.isFunctionExpression(p.node) ||
          t.isArrowFunctionExpression(p.node)
        );
        
        if (parentFunc && parentFunc.node.params) {
          // 检查是否有解构参数包含 getAppState 和 setAppState
          for (const param of parentFunc.node.params) {
            if (t.isObjectPattern(param)) {
              for (const prop of param.properties) {
                if (t.isObjectProperty(prop) && t.isIdentifier(prop.key)) {
                  if (prop.key.name === 'getAppState') {
                    getStateVar = t.isIdentifier(prop.value) ? prop.value.name : 'getAppState';
                  }
                  if (prop.key.name === 'setAppState') {
                    setStateVar = t.isIdentifier(prop.value) ? prop.value.name : 'setAppState';
                  }
                }
              }
            }
          }
        }

        // 构建处理代码
        let handlerCode;
        
        if (iV1FuncName && Me5FuncName && R42FuncName && wtFuncName && JrFuncName) {
          // 使用官方内部函数的完整实现
          handlerCode = `
            var __taskId = ${requestVar.name}.request.task_id;
            var __result = { success: false, error: "Unknown error" };
            try {
              if (!__taskId) {
                // 批量模式：后台化所有任务
                ${iV1FuncName}(${getStateVar}, ${setStateVar});
                __result = { success: true, mode: "all" };
              } else {
                // 单任务模式
                var __state = ${getStateVar}();
                var __task = __state.tasks[__taskId];
                if (!__task) {
                  __result = { success: false, error: "Task not found: " + __taskId };
                } else if (${wtFuncName}(__task)) {
                  ${Me5FuncName}(__taskId, ${getStateVar}, ${setStateVar});
                  __result = { success: true, type: "bash", task_id: __taskId };
                } else if (${JrFuncName}(__task)) {
                  ${R42FuncName}(__taskId, ${getStateVar}, ${setStateVar});
                  __result = { success: true, type: "agent", task_id: __taskId };
                } else {
                  __result = { success: false, error: "Unknown task type" };
                }
              }
            } catch (e) {
              __result = { success: false, error: e.message || String(e) };
            }
            ${responderName}({
              ...${requestVar.name},
              response: {
                subtype: __result.success ? "success" : "error",
                request_id: ${requestVar.name}.request_id,
                response: __result.success ? __result : undefined,
                error: __result.success ? undefined : __result.error
              }
            });
          `;
        } else if (iV1FuncName) {
          // 只找到 iV1 的简化实现
          handlerCode = `
            var __taskId = ${requestVar.name}.request.task_id;
            var __result = { success: false, error: "Unknown error" };
            try {
              if (!__taskId) {
                // 批量模式
                ${iV1FuncName}(${getStateVar}, ${setStateVar});
                __result = { success: true, mode: "all" };
              } else {
                __result = { success: false, error: "Single task mode not available - required functions not found" };
              }
            } catch (e) {
              __result = { success: false, error: e.message || String(e) };
            }
            ${responderName}({
              ...${requestVar.name},
              response: {
                subtype: __result.success ? "success" : "error",
                request_id: ${requestVar.name}.request_id,
                response: __result.success ? __result : undefined,
                error: __result.success ? undefined : __result.error
              }
            });
          `;
        } else {
          // 备用：简单的状态操作
          handlerCode = `
            var __result = { success: false, error: "Background functions not found in CLI" };
            ${responderName}({
              ...${requestVar.name},
              response: {
                subtype: "error",
                request_id: ${requestVar.name}.request_id,
                error: __result.error
              }
            });
          `;
        }

        const handlerAst = parser.parse(handlerCode, { sourceType: 'script' });
        const handlerBlock = t.blockStatement(handlerAst.program.body);

        const condition = t.binaryExpression(
          '===',
          t.memberExpression(
            t.memberExpression(t.cloneNode(requestVar), t.identifier('request')),
            t.identifier('subtype')
          ),
          t.stringLiteral('run_to_background')
        );

        const newIfStatement = t.ifStatement(
          condition,
          handlerBlock,
          path.node  // 原有的 interrupt 等处理
        );

        path.replaceWith(newIfStatement);
        step2Done = true;
        details.push(`添加 run_to_background 控制命令 (responder: ${responderName}, getState: ${getStateVar}, setState: ${setStateVar})`);
        path.stop();
      }
    });

    // ========================================
    // 返回结果
    // ========================================
    if (step2Done) {
      return { success: true, details };
    }

    return {
      success: false,
      reason: '未能添加 run_to_background 控制命令',
      details
    };
  }
};
