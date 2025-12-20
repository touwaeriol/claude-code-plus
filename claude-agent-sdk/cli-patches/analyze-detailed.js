/**
 * 详细分析 CLI 的输入处理架构
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(60));
console.log('CLI 输入架构详细分析');
console.log('='.repeat(60));
console.log();

// 1. 查找 structuredInput 的 getter/generator 定义
console.log('【1】structuredInput 生成器分析');
console.log('-'.repeat(60));

// 搜索包含 structuredInput 的代码
const codeLines = code.split('\n');
for (let i = 0; i < codeLines.length; i++) {
  const line = codeLines[i];
  if (line.includes('structuredInput') && (line.includes('get ') || line.includes('async *') || line.includes('yield'))) {
    console.log(`行 ${i + 1}: ${line.substring(0, 100)}...`);
  }
}

console.log();
console.log('【2】查找 JSON.parse 调用（输入解析）');
console.log('-'.repeat(60));

let jsonParseCount = 0;
traverse(ast, {
  CallExpression(path) {
    const callee = path.get('callee').toString();
    if (callee === 'JSON.parse') {
      jsonParseCount++;
      if (jsonParseCount <= 5) {
        console.log(`#${jsonParseCount} 位置: 行 ${path.node.loc?.start?.line}`);
        const parent = path.parentPath;
        if (parent) {
          const parentCode = parent.toString().substring(0, 80);
          console.log(`   上下文: ${parentCode}...`);
        }
      }
    }
  }
});
console.log(`共找到 ${jsonParseCount} 处 JSON.parse`);

console.log();
console.log('【3】分析控制请求处理的完整 if-else 链');
console.log('-'.repeat(60));

// 找到 d.type === "control_request" 的 if 语句
traverse(ast, {
  IfStatement(path) {
    const testCode = path.get('test').toString();
    if (testCode === 'd.type === "control_request"' || testCode === 'd.type==="control_request"') {
      console.log('✅ 找到 control_request 处理');
      console.log('   位置: 行', path.node.loc?.start?.line);

      // 分析嵌套的 if-else 链
      const conseq = path.node.consequent;
      if (conseq.type === 'BlockStatement') {
        console.log('   处理块包含', conseq.body.length, '个语句');

        // 遍历子 if 语句
        function analyzeIfChain(node, depth = 0) {
          if (node.type !== 'IfStatement') return;

          const indent = '   ' + '  '.repeat(depth);
          const test = node.test;

          // 获取条件字符串
          let condStr = '';
          if (test.type === 'BinaryExpression' && test.right.type === 'StringLiteral') {
            condStr = test.right.value;
          }

          console.log(`${indent}├─ subtype === "${condStr}"`);

          // 检查 consequent
          if (node.consequent.type === 'BlockStatement') {
            const body = node.consequent.body;
            body.forEach(stmt => {
              if (stmt.type === 'ExpressionStatement') {
                // 简化显示
              }
            });
          }

          // 递归分析 alternate
          if (node.alternate) {
            if (node.alternate.type === 'IfStatement') {
              analyzeIfChain(node.alternate, depth);
            }
          }
        }

        // 找到第一个嵌套的 if
        conseq.body.forEach(stmt => {
          if (stmt.type === 'IfStatement') {
            analyzeIfChain(stmt, 1);
          }
        });
      }

      path.stop();
    }
  }
});

console.log();
console.log('【4】检查是否有现成的后台执行 API');
console.log('-'.repeat(60));

// 搜索可能的后台执行相关函数
const bgKeywords = ['moveToBackground', 'runBackground', 'background', 'detach'];
bgKeywords.forEach(keyword => {
  const regex = new RegExp(`\\b${keyword}\\b`, 'gi');
  let count = 0;
  let match;
  while ((match = regex.exec(code)) !== null) {
    count++;
    if (count <= 3) {
      const start = Math.max(0, match.index - 20);
      const end = Math.min(code.length, match.index + 50);
      const context = code.substring(start, end).replace(/\n/g, ' ');
      console.log(`"${keyword}" #${count}: ...${context}...`);
    }
  }
  if (count > 0) {
    console.log(`共 ${count} 处 "${keyword}"`);
    console.log();
  }
});

console.log();
console.log('【5】分析 Ink 的 useInput 输入处理');
console.log('-'.repeat(60));

// 查找包含 useInput 的代码片段
const useInputRegex = /useInput\s*\([^)]+\)/g;
let useInputMatch;
let useInputCount = 0;
while ((useInputMatch = useInputRegex.exec(code)) !== null) {
  useInputCount++;
  if (useInputCount <= 3) {
    const line = code.substring(0, useInputMatch.index).split('\n').length;
    console.log(`#${useInputCount} 行 ${line}: ${useInputMatch[0].substring(0, 80)}...`);
  }
}
console.log(`共 ${useInputCount} 处 useInput`);

console.log();
console.log('='.repeat(60));
console.log('结论');
console.log('='.repeat(60));
console.log(`
问题分析:
1. CLI 通过 structuredInput 异步迭代器读取 stdin JSON 消息
2. 控制请求通过 JSON 消息传递，格式: {type:"control_request", request:{subtype:"xxx"}}
3. 当前支持的 subtype: interrupt, initialize, set_permission_mode, set_model,
   set_max_thinking_tokens, mcp_status, mcp_message, rewind_files, mcp_set_servers
4. Ink 的 useInput/onBackground 是用于交互模式的键盘输入，不适用于 SDK 模式

解决方案选项:
A. 在 control_request 处理中直接调用后台执行逻辑（需要找到该逻辑）
B. 找到并暴露后台执行的内部 API
C. 放弃通过 stdin 模拟按键的方式
`);
