/**
 * 分析 CLI 中 Promise.race 后台信号的代码模式
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(60));
console.log('Promise.race 后台信号模式分析');
console.log('='.repeat(60));
console.log();

// 查找 Promise.race 调用
console.log('【1】查找 Promise.race 调用');
console.log('-'.repeat(60));

let promiseRaceCount = 0;
traverse(ast, {
  CallExpression(path) {
    const callee = path.get('callee').toString();
    if (callee === 'Promise.race') {
      promiseRaceCount++;
      if (promiseRaceCount <= 10) {
        console.log(`#${promiseRaceCount} 位置: 行 ${path.node.loc?.start?.line}`);

        // 查看参数
        const args = path.node.arguments;
        if (args.length > 0 && args[0].type === 'ArrayExpression') {
          console.log(`   参数数量: ${args[0].elements.length}`);

          // 查看每个元素
          args[0].elements.forEach((elem, i) => {
            if (elem) {
              const elemCode = path.get(`arguments.0.elements.${i}`).toString().substring(0, 80);
              console.log(`   [${i}]: ${elemCode}...`);
            }
          });
        }
        console.log();
      }
    }
  }
});
console.log(`共 ${promiseRaceCount} 处 Promise.race`);

console.log();
console.log('【2】查找 "type: background" 相关的 Promise.race');
console.log('-'.repeat(60));

traverse(ast, {
  CallExpression(path) {
    const callee = path.get('callee').toString();
    if (callee !== 'Promise.race') return;

    const codeStr = path.toString();
    if (codeStr.includes('background') || codeStr.includes('message')) {
      console.log('✅ 找到候选 Promise.race');
      console.log('   位置: 行', path.node.loc?.start?.line);
      console.log('   代码片段:', codeStr.substring(0, 200) + '...');
      console.log();

      // 查看父作用域中的变量定义
      const func = path.getFunctionParent();
      if (func) {
        console.log('   所在函数: 行', func.node.loc?.start?.line);
      }
    }
  }
});

console.log();
console.log('【3】查找 Promise resolver 赋值模式');
console.log('-'.repeat(60));

// 查找 let x, m = new Promise((VA) => { x = VA }) 模式
traverse(ast, {
  VariableDeclaration(path) {
    const declarations = path.node.declarations;

    // 检查是否有 new Promise 调用
    for (const decl of declarations) {
      if (!decl.init) continue;
      if (decl.init.type !== 'NewExpression') continue;
      if (decl.init.callee?.name !== 'Promise') continue;

      // 找到了 new Promise
      const promiseArg = decl.init.arguments[0];
      if (!promiseArg) continue;

      // 检查回调函数中是否有赋值
      if (promiseArg.type === 'ArrowFunctionExpression' || promiseArg.type === 'FunctionExpression') {
        const bodyCode = path.get('declarations').map(d => {
          if (d.node.init?.type === 'NewExpression' && d.node.init.callee?.name === 'Promise') {
            return generate(d.node).code;
          }
          return null;
        }).filter(Boolean).join(', ');

        if (bodyCode.length > 0 && bodyCode.length < 200) {
          console.log('✅ 找到 Promise resolver 模式');
          console.log('   位置: 行', path.node.loc?.start?.line);
          console.log('   代码:', bodyCode.substring(0, 150));

          // 查找同一作用域中的其他变量
          const siblings = path.getAllPrevSiblings().concat(path.getAllNextSiblings());
          for (const sib of siblings.slice(0, 5)) {
            if (sib.isVariableDeclaration()) {
              const sibCode = generate(sib.node).code;
              if (sibCode.length < 100) {
                console.log('   相邻变量:', sibCode.substring(0, 80));
              }
            }
          }
          console.log();
        }
      }
    }
  }
});

console.log();
console.log('【4】查找 onBackground 回调被设置的位置');
console.log('-'.repeat(60));

traverse(ast, {
  ObjectProperty(path) {
    const keyName = path.node.key?.name || path.node.key?.value;
    if (keyName === 'onBackground') {
      const value = path.get('value');
      if (value.isIdentifier()) {
        console.log('✅ 找到 onBackground 属性');
        console.log('   位置: 行', path.node.loc?.start?.line);
        console.log('   值:', value.node.name);

        // 尝试找到这个函数的定义
        const binding = value.scope.getBinding(value.node.name);
        if (binding) {
          console.log('   定义位置: 行', binding.path.node.loc?.start?.line);
        }
        console.log();
      }
    }
  }
});

console.log();
console.log('='.repeat(60));
console.log('分析完成');
console.log('='.repeat(60));
