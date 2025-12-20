/**
 * AST 分析 - 专门分析 insertMessageChain 函数
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(70));
console.log('insertMessageChain 函数分析');
console.log('='.repeat(70));
console.log();

// 找到 insertMessageChain 方法
traverse(ast, {
  ObjectMethod(path) {
    if (path.node.key?.name !== 'insertMessageChain') return;

    console.log('✅ 找到 insertMessageChain');
    console.log(`位置: 行 ${path.node.loc?.start?.line}`);
    console.log();

    // 打印参数
    const params = path.node.params.map((p, i) => {
      if (p.type === 'Identifier') return `参数${i+1}: ${p.name}`;
      if (p.type === 'AssignmentPattern') {
        const defaultVal = generate(p.right).code;
        return `参数${i+1}: ${p.left.name} = ${defaultVal}`;
      }
      return `参数${i+1}: ?`;
    });
    console.log('参数签名:');
    params.forEach(p => console.log(`  ${p}`));
    console.log();

    // 打印完整函数体
    const body = generate(path.node.body).code;
    console.log('函数体:');
    console.log('-'.repeat(70));

    // 格式化输出
    const formatted = body
      .replace(/;/g, ';\n')
      .replace(/\{/g, '{\n')
      .replace(/\}/g, '\n}')
      .split('\n')
      .filter(line => line.trim())
      .map(line => '  ' + line.trim())
      .join('\n');

    console.log(formatted);
    console.log('-'.repeat(70));

    path.stop();
  }
});

console.log();
console.log('='.repeat(70));
console.log('RWA 函数分析（消息路径重建）');
console.log('='.repeat(70));
console.log();

// 找到 RWA 函数（之前分析发现它使用 parentUuid）
traverse(ast, {
  FunctionDeclaration(path) {
    if (path.node.id?.name !== 'RWA') return;

    console.log('✅ 找到 RWA 函数');
    console.log(`位置: 行 ${path.node.loc?.start?.line}`);

    const body = generate(path.node.body).code;
    console.log('函数体:');
    console.log(body.substring(0, 500));

    path.stop();
  }
});

console.log();
console.log('='.repeat(70));
console.log('KH7 函数分析');
console.log('='.repeat(70));

traverse(ast, {
  FunctionDeclaration(path) {
    if (path.node.id?.name !== 'KH7') return;

    console.log('✅ 找到 KH7 函数');
    const body = generate(path.node.body).code;
    console.log('函数体:');
    console.log(body.substring(0, 500));

    path.stop();
  }
});
