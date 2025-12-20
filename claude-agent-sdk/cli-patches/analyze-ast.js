/**
 * AST 分析脚本 - 分析 CLI 的控制请求处理结构
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(60));
console.log('CLI AST 结构分析');
console.log('='.repeat(60));
console.log();

// 1. 查找 structuredInput 的定义和使用
console.log('【1】查找 structuredInput 流的处理');
console.log('-'.repeat(60));

traverse(ast, {
  ForAwaitStatement(path) {
    const rightCode = path.get('right').toString();
    if (rightCode.includes('structuredInput')) {
      console.log('✅ 找到 for-await 循环:');
      console.log('   迭代变量:', path.node.left.declarations?.[0]?.id?.name || 'unknown');
      console.log('   迭代源:', rightCode.substring(0, 50) + '...');
      console.log();

      // 分析循环体内的控制流
      console.log('【2】循环体内的条件分支');
      console.log('-'.repeat(60));

      let depth = 0;
      path.traverse({
        IfStatement: {
          enter(ifPath) {
            const testCode = ifPath.get('test').toString();
            const indent = '  '.repeat(depth);

            // 只关注控制请求相关的条件
            if (testCode.includes('type') || testCode.includes('subtype') || testCode.includes('control')) {
              console.log(`${indent}IF: ${testCode.substring(0, 80)}...`);

              // 查看 consequent 的第一个语句
              const conseq = ifPath.node.consequent;
              if (conseq.type === 'BlockStatement' && conseq.body.length > 0) {
                const firstStmt = conseq.body[0];
                if (firstStmt.type === 'IfStatement') {
                  const innerTest = ifPath.get('consequent.body.0.test').toString();
                  console.log(`${indent}  └─ 嵌套IF: ${innerTest.substring(0, 80)}...`);
                } else if (firstStmt.type === 'ExpressionStatement') {
                  const expr = ifPath.get('consequent.body.0.expression').toString();
                  console.log(`${indent}  └─ 执行: ${expr.substring(0, 60)}...`);
                }
              }
            }
            depth++;
          },
          exit() {
            depth--;
          }
        }
      });

      path.stop();
    }
  }
});

console.log();
console.log('【3】查找 interrupt 处理的精确位置');
console.log('-'.repeat(60));

traverse(ast, {
  IfStatement(path) {
    const testCode = path.get('test').toString();
    if (testCode.includes('subtype') && testCode.includes('interrupt')) {
      console.log('✅ 找到 interrupt 处理:');
      console.log('   条件:', testCode);
      console.log('   位置: 行', path.node.loc?.start?.line);

      // 查看父节点
      const parent = path.parentPath;
      if (parent.isBlockStatement()) {
        const grandParent = parent.parentPath;
        if (grandParent.isIfStatement()) {
          const gpTest = grandParent.get('test').toString();
          console.log('   父级条件:', gpTest.substring(0, 80));
        }
      }

      // 查看 consequent 内容
      const conseq = path.node.consequent;
      if (conseq.type === 'BlockStatement') {
        console.log('   处理内容:');
        conseq.body.forEach((stmt, i) => {
          if (stmt.type === 'ExpressionStatement') {
            const expr = path.get(`consequent.body.${i}.expression`).toString();
            console.log(`     ${i + 1}. ${expr.substring(0, 60)}`);
          } else if (stmt.type === 'IfStatement') {
            console.log(`     ${i + 1}. [嵌套 IF]`);
          }
        });
      }

      path.stop();
    }
  }
});

console.log();
console.log('【4】分析 stdin 读取机制');
console.log('-'.repeat(60));

// 查找 stdin 相关的代码
let stdinUsages = [];
traverse(ast, {
  MemberExpression(path) {
    const code = path.toString();
    if (code.includes('process.stdin') && code.length < 100) {
      const parent = path.parentPath;
      if (parent.isCallExpression()) {
        const fullCall = parent.toString().substring(0, 100);
        if (!stdinUsages.includes(fullCall)) {
          stdinUsages.push(fullCall);
        }
      }
    }
  }
});

console.log('process.stdin 的使用方式:');
stdinUsages.slice(0, 10).forEach((usage, i) => {
  console.log(`  ${i + 1}. ${usage}`);
});

console.log();
console.log('='.repeat(60));
console.log('分析完成');
console.log('='.repeat(60));
