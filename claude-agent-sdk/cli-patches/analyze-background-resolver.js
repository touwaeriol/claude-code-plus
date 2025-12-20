/**
 * 分析 Promise.race 中 background resolver 的具体实现
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(60));
console.log('Background Resolver 详细分析');
console.log('='.repeat(60));
console.log();

// 1. 找到 type: "background" 的 Promise.race
traverse(ast, {
  CallExpression(path) {
    const callee = path.get('callee').toString();
    if (callee !== 'Promise.race') return;

    const codeStr = path.toString();
    if (!codeStr.includes('"background"')) return;

    console.log('✅ 找到 background Promise.race');
    console.log('   位置: 行', path.node.loc?.start?.line);
    console.log();

    // 获取函数作用域
    const funcParent = path.getFunctionParent();
    if (!funcParent) return;

    console.log('【所在函数分析】');
    console.log('   函数起始行:', funcParent.node.loc?.start?.line);
    console.log('   函数结束行:', funcParent.node.loc?.end?.line);

    // 分析 Promise.race 的参数
    const args = path.node.arguments;
    if (args.length > 0 && args[0].type === 'ArrayExpression') {
      args[0].elements.forEach((elem, i) => {
        if (elem) {
          const elemCode = generate(elem).code;
          console.log(`\n【Promise.race 参数 ${i}】`);
          console.log(elemCode.substring(0, 300));

          // 如果包含 "background"，分析 then 调用的对象
          if (elemCode.includes('background')) {
            // 这是 g.then(() => ({type: "background"}))
            // 我们需要找 g 的定义
            if (elem.type === 'CallExpression' && elem.callee.type === 'MemberExpression') {
              const promiseVar = elem.callee.object;
              if (promiseVar.type === 'Identifier') {
                console.log(`\n   后台信号 Promise 变量名: ${promiseVar.name}`);

                // 在函数作用域中查找这个变量的定义
                const binding = path.scope.getBinding(promiseVar.name);
                if (binding) {
                  console.log('   定义位置: 行', binding.path.node.loc?.start?.line);
                  const defCode = generate(binding.path.node).code;
                  console.log('   定义代码:', defCode.substring(0, 300));
                }
              }
            }
          }
        }
      });
    }

    // 查找同一作用域中的 setToolJSX 调用（这里设置 onBackground 回调）
    console.log('\n【查找 onBackground 回调设置】');
    funcParent.traverse({
      CallExpression(innerPath) {
        const innerCode = innerPath.toString();
        if (innerCode.includes('onBackground') && innerCode.includes('setToolJSX')) {
          console.log('   找到 setToolJSX 调用');
          console.log('   位置: 行', innerPath.node.loc?.start?.line);

          // 查找 onBackground 属性的值
          innerPath.traverse({
            ObjectProperty(propPath) {
              if (propPath.node.key?.name === 'onBackground') {
                const valueCode = generate(propPath.node.value).code;
                console.log('   onBackground 值:', valueCode);
              }
            }
          });
        }
      }
    });

    // 查找 s 或类似的 resolver 变量
    console.log('\n【查找 resolver 变量】');
    funcParent.traverse({
      VariableDeclarator(varPath) {
        const init = varPath.node.init;
        if (!init) return;

        // 查找箭头函数形式的 resolver: s = () => { ... }
        if (init.type === 'ArrowFunctionExpression') {
          const varName = varPath.node.id?.name;
          const bodyCode = generate(init.body).code;

          // 检查是否调用了某个变量（可能是 resolver）
          if (bodyCode.length < 100 && bodyCode.includes('()')) {
            console.log(`   候选 resolver: ${varName} = ${generate(init).code.substring(0, 80)}`);
          }
        }

        // 查找 new Promise 形式
        if (init.type === 'NewExpression' && init.callee?.name === 'Promise') {
          const varName = varPath.node.id?.name;
          console.log(`   Promise 变量: ${varName}`);
          console.log('   定义: 行', varPath.node.loc?.start?.line);
        }
      }
    });

    path.stop();
  }
});

console.log();
console.log('='.repeat(60));
console.log('分析完成');
console.log('='.repeat(60));
