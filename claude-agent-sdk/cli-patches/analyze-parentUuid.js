/**
 * AST 分析脚本 - 分析 CLI 的 parentUuid 处理流程
 *
 * 目标：确定 SDK 模式下用户消息中的 parentUuid 是否被读取和使用
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(70));
console.log('CLI parentUuid 处理流程分析');
console.log('='.repeat(70));
console.log();

// 1. 查找 insertMessageChain 函数定义
console.log('【1】查找 insertMessageChain 函数定义');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    if (path.node.key.name === 'insertMessageChain' ||
        (path.node.key.type === 'Identifier' && path.node.key.name === 'insertMessageChain')) {
      console.log('✅ 找到 insertMessageChain 方法');
      console.log('   参数列表:', path.node.params.map(p => {
        if (p.type === 'Identifier') return p.name;
        if (p.type === 'AssignmentPattern') {
          return `${p.left.name}=${generate(p.right).code}`;
        }
        return '?';
      }).join(', '));
      console.log('   位置: 行', path.node.loc?.start?.line);

      // 查找函数体内 parentUuid 的赋值
      path.traverse({
        AssignmentExpression(innerPath) {
          const left = generate(innerPath.node.left).code;
          if (left.includes('parentUuid')) {
            console.log('   parentUuid 赋值:', generate(innerPath.node).code.substring(0, 100));
          }
        },
        ObjectProperty(innerPath) {
          if (innerPath.node.key.name === 'parentUuid' ||
              (innerPath.node.key.type === 'Identifier' && innerPath.node.key.name === 'parentUuid')) {
            console.log('   parentUuid 属性:', generate(innerPath.node).code.substring(0, 100));
          }
        }
      });

      path.stop();
    }
  }
});

console.log();
console.log('【2】查找调用 insertMessageChain 的地方');
console.log('-'.repeat(70));

let callCount = 0;
traverse(ast, {
  CallExpression(path) {
    const callee = generate(path.node.callee).code;
    if (callee.includes('insertMessageChain')) {
      callCount++;
      console.log(`✅ 调用 #${callCount}:`);
      console.log('   表达式:', callee);
      console.log('   参数数量:', path.node.arguments.length);

      // 打印每个参数
      path.node.arguments.forEach((arg, i) => {
        const argCode = generate(arg).code;
        console.log(`   参数 ${i + 1}:`, argCode.substring(0, 80) + (argCode.length > 80 ? '...' : ''));
      });

      // 查找调用的上下文
      let current = path;
      let context = [];
      for (let i = 0; i < 3 && current; i++) {
        current = current.parentPath;
        if (current?.node?.type === 'FunctionDeclaration' ||
            current?.node?.type === 'FunctionExpression' ||
            current?.node?.type === 'ArrowFunctionExpression') {
          const funcName = current.node.id?.name || '(anonymous)';
          context.push(funcName);
          break;
        }
      }
      if (context.length > 0) {
        console.log('   所在函数:', context.join(' > '));
      }
      console.log('   位置: 行', path.node.loc?.start?.line);
      console.log();
    }
  }
});

console.log();
console.log('【3】查找 Q2A 和 U70 函数（insertMessageChain 的包装）');
console.log('-'.repeat(70));

traverse(ast, {
  FunctionDeclaration(path) {
    const name = path.node.id?.name;
    if (name === 'Q2A' || name === 'U70') {
      console.log(`✅ 函数 ${name}:`);
      console.log('   参数:', path.node.params.map(p => {
        if (p.type === 'Identifier') return p.name;
        if (p.type === 'AssignmentPattern') return `${p.left.name}=...`;
        return '?';
      }).join(', '));

      // 查找函数体内对 insertMessageChain 的调用
      path.traverse({
        CallExpression(innerPath) {
          const callee = generate(innerPath.node.callee).code;
          if (callee.includes('insertMessageChain')) {
            console.log('   调用 insertMessageChain:');
            console.log('     ', generate(innerPath.node).code.substring(0, 100));
          }
        }
      });
      console.log();
    }
  },
  VariableDeclarator(path) {
    const name = path.node.id?.name;
    if (name === 'Q2A' || name === 'U70') {
      console.log(`✅ 变量 ${name} (可能是函数):`);
      const init = generate(path.node.init).code;
      console.log('   定义:', init.substring(0, 200) + (init.length > 200 ? '...' : ''));
      console.log();
    }
  }
});

console.log();
console.log('【4】查找 SDK 模式下用户消息的解析');
console.log('-'.repeat(70));

// 搜索 stdin 相关的 JSON 解析
traverse(ast, {
  CallExpression(path) {
    const callee = generate(path.node.callee).code;
    if (callee.includes('JSON.parse') || callee.includes('.parse')) {
      const parent = path.parentPath;
      const parentCode = generate(parent.node).code;

      // 查找与 stdin 或 buffer 相关的解析
      if (parentCode.includes('stdin') || parentCode.includes('buffer') || parentCode.includes('Buffer')) {
        if (parentCode.length < 200) {
          console.log('JSON 解析 (可能与 stdin 相关):');
          console.log('  ', parentCode);
          console.log('   位置: 行', path.node.loc?.start?.line);
          console.log();
        }
      }
    }
  }
});

console.log();
console.log('【5】查找用户消息对象的属性访问');
console.log('-'.repeat(70));

// 查找对 message 对象的 parentUuid 属性访问
let parentUuidAccesses = [];
traverse(ast, {
  MemberExpression(path) {
    const code = generate(path.node).code;
    if (code.includes('.parentUuid') && code.length < 100) {
      if (!parentUuidAccesses.includes(code)) {
        parentUuidAccesses.push(code);
      }
    }
  }
});

console.log('对 .parentUuid 的属性访问:');
parentUuidAccesses.slice(0, 20).forEach((access, i) => {
  console.log(`  ${i + 1}. ${access}`);
});

console.log();
console.log('【6】查找 stream-json 模式下消息处理的入口');
console.log('-'.repeat(70));

// 查找 yield 语句（generator 函数中输出消息的地方）
let yieldWithSessionId = 0;
traverse(ast, {
  YieldExpression(path) {
    if (path.node.argument?.type === 'ObjectExpression') {
      const objCode = generate(path.node.argument).code;
      if (objCode.includes('session_id') || objCode.includes('parent_tool_use_id')) {
        yieldWithSessionId++;
        if (yieldWithSessionId <= 5) {
          console.log(`yield #${yieldWithSessionId}:`);
          console.log('  ', objCode.substring(0, 150) + (objCode.length > 150 ? '...' : ''));
          console.log('   位置: 行', path.node.loc?.start?.line);
          console.log();
        }
      }
    }
  }
});

if (yieldWithSessionId > 5) {
  console.log(`... 还有 ${yieldWithSessionId - 5} 个类似的 yield`);
}

console.log();
console.log('【7】查找 structuredInput 迭代器中用户消息的处理');
console.log('-'.repeat(70));

traverse(ast, {
  ForAwaitStatement(path) {
    const rightCode = generate(path.node.right).code;
    if (rightCode.includes('structuredInput')) {
      console.log('✅ 找到 structuredInput 迭代:');

      // 查找循环体内对迭代变量属性的访问
      const varName = path.node.left.declarations?.[0]?.id?.name;
      if (varName) {
        console.log('   迭代变量:', varName);

        path.traverse({
          MemberExpression(innerPath) {
            const code = generate(innerPath.node).code;
            if (code.startsWith(varName + '.') && code.length < 50) {
              console.log('   访问属性:', code);
            }
          }
        });
      }

      path.stop();
    }
  }
});

console.log();
console.log('='.repeat(70));
console.log('分析完成');
console.log('='.repeat(70));
console.log();
console.log('结论：检查上面的输出来确定:');
console.log('1. insertMessageChain 的第4个参数(parentUuid)是如何被传递的');
console.log('2. SDK 模式下用户消息的 parentUuid 字段是否被读取');
console.log('3. Q2A/U70 调用时是否传递了 parentUuid');
