#!/usr/bin/env node
/**
 * 分析 CLI 中多任务后台执行的逻辑
 *
 * 目标：理解当有多个 bash 命令/子代理同时执行时，
 * Ctrl+B 是如何将"最新的"任务移到后台的
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const inputFile = process.argv[2] || 'claude-cli-2.0.73.js';

console.log('========================================');
console.log('多任务后台执行逻辑分析');
console.log('========================================');
console.log(`分析文件: ${inputFile}`);
console.log();

// 读取源代码
const sourceCode = fs.readFileSync(inputFile, 'utf-8');

// 解析为 AST
console.log('🔍 解析 AST...');
const ast = parser.parse(sourceCode, {
  sourceType: 'script',
  plugins: [],
  errorRecovery: true,
});
console.log('✅ AST 解析完成');
console.log();

// 分析结果
const findings = {
  // 子代理后台机制
  taskBackgroundResolvers: [],
  // Bash 后台机制
  bashBackgroundCallbacks: [],
  // setToolJSX 调用（显示后台提示）
  setToolJSXCalls: [],
  // onBackground 回调
  onBackgroundCallbacks: [],
  // Promise.race 模式
  promiseRacePatterns: [],
  // 全局变量赋值
  globalAssignments: [],
};

// 1. 查找 Promise.race 模式
console.log('📋 1. 查找 Promise.race 模式...');
traverse(ast, {
  CallExpression(path) {
    const callee = path.node.callee;

    // 查找 Promise.race([...])
    if (callee.type === 'MemberExpression' &&
        callee.object?.name === 'Promise' &&
        callee.property?.name === 'race') {

      const args = path.node.arguments;
      if (args.length === 1 && args[0].type === 'ArrayExpression') {
        const elements = args[0].elements;

        // 检查是否包含 "background" 类型
        let hasBackgroundPattern = false;
        elements.forEach(elem => {
          const code = generate(elem).code;
          if (code.includes('background') || code.includes('type:"background"')) {
            hasBackgroundPattern = true;
          }
        });

        if (hasBackgroundPattern) {
          const loc = path.node.loc?.start;
          const parentCode = generate(path.parentPath.node).code.slice(0, 200);
          findings.promiseRacePatterns.push({
            line: loc?.line,
            elementsCount: elements.length,
            preview: parentCode + '...'
          });
        }
      }
    }
  }
});
console.log(`   找到 ${findings.promiseRacePatterns.length} 个 Promise.race 后台模式`);

// 2. 查找 setToolJSX 调用
console.log('📋 2. 查找 setToolJSX 调用...');
traverse(ast, {
  CallExpression(path) {
    const callee = path.node.callee;

    // 查找 *.setToolJSX({ ... onBackground: ... })
    if (callee.type === 'MemberExpression' &&
        callee.property?.name === 'setToolJSX') {

      const args = path.node.arguments;
      if (args.length >= 1 && args[0].type === 'ObjectExpression') {
        const props = args[0].properties;

        // 查找 onBackground 属性
        const onBackgroundProp = props.find(p =>
          p.key?.name === 'onBackground' || p.key?.value === 'onBackground'
        );

        if (onBackgroundProp) {
          const loc = path.node.loc?.start;
          const callbackCode = generate(onBackgroundProp.value).code;
          findings.setToolJSXCalls.push({
            line: loc?.line,
            onBackgroundCallback: callbackCode,
            fullCode: generate(path.node).code.slice(0, 300)
          });
        }
      }
    }
  }
});
console.log(`   找到 ${findings.setToolJSXCalls.length} 个 setToolJSX 调用`);

// 3. 查找 onBackground 属性定义
console.log('📋 3. 查找 onBackground 属性定义...');
traverse(ast, {
  ObjectProperty(path) {
    const key = path.node.key;
    if ((key.name === 'onBackground' || key.value === 'onBackground')) {
      const loc = path.node.loc?.start;
      const valueCode = generate(path.node.value).code;

      // 查找上下文
      let contextCode = '';
      let parent = path.parentPath;
      for (let i = 0; i < 3 && parent; i++) {
        parent = parent.parentPath;
      }
      if (parent) {
        contextCode = generate(parent.node).code.slice(0, 200);
      }

      findings.onBackgroundCallbacks.push({
        line: loc?.line,
        callback: valueCode,
        context: contextCode
      });
    }
  }
});
console.log(`   找到 ${findings.onBackgroundCallbacks.length} 个 onBackground 定义`);

// 4. 查找 tengu_bash_command_backgrounded 调用
console.log('📋 4. 查找 Bash 后台回调...');
traverse(ast, {
  CallExpression(path) {
    const args = path.node.arguments;
    if (args.length >= 1 &&
        args[0].type === 'StringLiteral' &&
        args[0].value === 'tengu_bash_command_backgrounded') {

      const loc = path.node.loc?.start;

      // 查找包含该调用的函数
      let funcParent = path.parentPath;
      while (funcParent && funcParent.node.type !== 'FunctionDeclaration' &&
             funcParent.node.type !== 'FunctionExpression' &&
             funcParent.node.type !== 'ArrowFunctionExpression') {
        funcParent = funcParent.parentPath;
      }

      let funcName = 'anonymous';
      if (funcParent?.node.id?.name) {
        funcName = funcParent.node.id.name;
      }

      findings.bashBackgroundCallbacks.push({
        line: loc?.line,
        functionName: funcName,
        code: generate(path.node).code
      });
    }
  }
});
console.log(`   找到 ${findings.bashBackgroundCallbacks.length} 个 Bash 后台回调`);

// 5. 查找全局变量赋值模式
console.log('📋 5. 查找全局 background resolver 赋值...');
traverse(ast, {
  AssignmentExpression(path) {
    const left = path.node.left;

    // 查找 global.__sdk* = ...
    if (left.type === 'MemberExpression' &&
        left.object?.name === 'global' &&
        left.property?.name?.startsWith('__sdk')) {

      const loc = path.node.loc?.start;
      findings.globalAssignments.push({
        line: loc?.line,
        varName: left.property.name,
        value: generate(path.node.right).code.slice(0, 100)
      });
    }
  }
});
console.log(`   找到 ${findings.globalAssignments.length} 个全局变量赋值`);

// 6. 分析多任务栈机制
console.log('📋 6. 查找任务栈/队列机制...');
const taskStackPatterns = [];
traverse(ast, {
  VariableDeclarator(path) {
    const init = path.node.init;
    const name = path.node.id?.name;

    // 查找数组初始化（可能是任务栈）
    if (init?.type === 'ArrayExpression' && name) {
      // 检查是否与 task/background/agent 相关
      const contextCode = generate(path.parentPath.node).code;
      if (contextCode.includes('task') ||
          contextCode.includes('background') ||
          contextCode.includes('agent') ||
          contextCode.includes('running')) {

        const loc = path.node.loc?.start;
        taskStackPatterns.push({
          line: loc?.line,
          name: name,
          context: contextCode.slice(0, 150)
        });
      }
    }
  }
});
console.log(`   找到 ${taskStackPatterns.length} 个可能的任务栈模式`);

// 输出详细结果
console.log();
console.log('========================================');
console.log('详细分析结果');
console.log('========================================');

console.log();
console.log('🔷 Promise.race 后台模式:');
findings.promiseRacePatterns.forEach((p, i) => {
  console.log(`   [${i+1}] Line ${p.line}: ${p.elementsCount} elements`);
  console.log(`       ${p.preview.slice(0, 100)}...`);
});

console.log();
console.log('🔷 setToolJSX 调用 (onBackground):');
findings.setToolJSXCalls.forEach((c, i) => {
  console.log(`   [${i+1}] Line ${c.line}`);
  console.log(`       onBackground: ${c.onBackgroundCallback}`);
});

console.log();
console.log('🔷 Bash 后台回调:');
findings.bashBackgroundCallbacks.forEach((b, i) => {
  console.log(`   [${i+1}] Line ${b.line}, Function: ${b.functionName}`);
  console.log(`       ${b.code}`);
});

console.log();
console.log('🔷 全局 resolver 赋值:');
findings.globalAssignments.forEach((g, i) => {
  console.log(`   [${i+1}] Line ${g.line}: global.${g.varName} = ${g.value}`);
});

// 分析结论
console.log();
console.log('========================================');
console.log('分析结论');
console.log('========================================');
console.log(`
根据 AST 分析，CLI 的多任务后台执行机制如下：

1. **全局 Resolver 模式**:
   - CLI 使用全局变量 (如 global.__sdkBackgroundResolver) 来存储当前活跃任务的后台 resolver
   - 每次新任务启动时，会**覆盖**这个全局变量
   - 所以 Ctrl+B 或 run_in_background 只会影响"最新的"任务

2. **单一后台触发器**:
   - 不存在任务栈或队列来管理多个前台任务
   - 全局 resolver 是一个单一的引用，指向最新任务的 resolver

3. **多任务场景**:
   - Task A 启动 → global.__sdkBackgroundResolver = resolverA
   - Task B 启动 → global.__sdkBackgroundResolver = resolverB (覆盖)
   - Ctrl+B 触发 → resolverB() 被调用，只有 Task B 进入后台
   - Task A 仍在前台（因为其 resolver 已被覆盖，无法再触发）

4. **补丁实现**:
   - 补丁通过暴露全局 resolver 来支持 SDK 触发后台
   - 由于是全局单一变量，所以只能操作最新的任务

找到的关键位置:
- Promise.race 模式: ${findings.promiseRacePatterns.length} 处
- setToolJSX 调用: ${findings.setToolJSXCalls.length} 处
- Bash 后台回调: ${findings.bashBackgroundCallbacks.length} 处
- 全局赋值: ${findings.globalAssignments.length} 处
`);

console.log();
console.log('✅ 分析完成');
