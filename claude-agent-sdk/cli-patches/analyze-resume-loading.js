/**
 * AST 分析 - CLI resume 会话时如何加载消息列表
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(70));
console.log('CLI Resume 消息加载逻辑分析');
console.log('='.repeat(70));
console.log();

// ========================================
// 1. 找到 getLastLog 函数（获取最新日志/消息）
// ========================================
console.log('【1】查找 getLastLog 函数');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    if (path.node.key?.name !== 'getLastLog') return;

    console.log('✅ 找到 getLastLog 方法');
    console.log(`位置: 行 ${path.node.loc?.start?.line}`);

    const body = generate(path.node.body).code;
    console.log('\n函数体:');
    console.log(body);
    console.log();

    path.stop();
  }
});

// ========================================
// 2. 找到 O70 函数（加载消息）
// ========================================
console.log('【2】查找消息加载函数 (O70 或类似)');
console.log('-'.repeat(70));

// 查找调用 VH7（叶节点查找）的函数
traverse(ast, {
  FunctionDeclaration(path) {
    let callsVH7 = false;
    let callsRWA = false;

    path.traverse({
      CallExpression(innerPath) {
        const callee = generate(innerPath.node.callee).code;
        if (callee === 'VH7') callsVH7 = true;
        if (callee === 'RWA') callsRWA = true;
      }
    });

    if (callsVH7 || callsRWA) {
      console.log(`✅ 函数 ${path.node.id?.name} 调用了:`);
      if (callsVH7) console.log('   - VH7 (叶节点查找)');
      if (callsRWA) console.log('   - RWA (路径回溯)');
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);

      const body = generate(path.node.body).code;
      if (body.length < 1000) {
        console.log('\n   函数体:');
        console.log('   ' + body.split('\n').join('\n   '));
      }
      console.log();
    }
  }
});

// ========================================
// 3. 找到完整的消息加载流程
// ========================================
console.log('【3】分析消息加载流程');
console.log('-'.repeat(70));

// 查找使用 messages.get 和 parentUuid 的函数
traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    // 查找可能是加载消息的方法
    if (name.includes('load') || name.includes('get') || name.includes('rebuild')) {
      let usesParentUuid = false;
      let usesMessagesMap = false;

      path.traverse({
        MemberExpression(innerPath) {
          const code = generate(innerPath.node).code;
          if (code.includes('.parentUuid')) usesParentUuid = true;
          if (code.includes('.get(') && code.length < 50) usesMessagesMap = true;
        }
      });

      if (usesParentUuid && usesMessagesMap) {
        console.log(`✅ ${name} - 使用 parentUuid 和 Map.get`);
        console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
      }
    }
  }
});

// ========================================
// 4. 直接搜索 async function 中的关键模式
// ========================================
console.log();
console.log('【4】查找异步消息加载函数');
console.log('-'.repeat(70));

traverse(ast, {
  FunctionDeclaration(path) {
    if (!path.node.async) return;

    const name = path.node.id?.name;
    const body = generate(path.node.body).code;

    // 查找包含关键加载逻辑的函数
    if (body.includes('messages') && body.includes('.get(') && body.includes('parentUuid')) {
      console.log(`✅ async function ${name}`);
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);

      // 检查是否调用了关键函数
      if (body.includes('VH7')) console.log('   调用: VH7 (叶节点)');
      if (body.includes('RWA')) console.log('   调用: RWA (回溯)');

      // 打印关键代码片段
      if (body.length < 800) {
        console.log('\n   函数体:');
        console.log('   ' + body.split('\n').join('\n   '));
      }
      console.log();
    }
  }
});

// ========================================
// 5. 分析 VH7 和 RWA 的调用关系
// ========================================
console.log();
console.log('【5】VH7 和 RWA 的调用链');
console.log('-'.repeat(70));

// 找出谁调用了 VH7
let vh7Callers = [];
traverse(ast, {
  CallExpression(path) {
    const callee = generate(path.node.callee).code;
    if (callee !== 'VH7') return;

    let funcName = '(global)';
    const funcParent = path.getFunctionParent();
    if (funcParent?.node?.id?.name) {
      funcName = funcParent.node.id.name;
    }

    if (!vh7Callers.includes(funcName)) {
      vh7Callers.push(funcName);
    }
  }
});

console.log('调用 VH7 的函数:');
vh7Callers.forEach(f => console.log(`  - ${f}`));

// 找出谁调用了 RWA
let rwaCallers = [];
traverse(ast, {
  CallExpression(path) {
    const callee = generate(path.node.callee).code;
    if (callee !== 'RWA') return;

    let funcName = '(global)';
    const funcParent = path.getFunctionParent();
    if (funcParent?.node?.id?.name) {
      funcName = funcParent.node.id.name;
    }

    if (!rwaCallers.includes(funcName)) {
      rwaCallers.push(funcName);
    }
  }
});

console.log('\n调用 RWA 的函数:');
rwaCallers.forEach(f => console.log(`  - ${f}`));

// ========================================
// 6. 分析这些调用者的完整代码
// ========================================
console.log();
console.log('【6】分析关键调用者的代码');
console.log('-'.repeat(70));

const keyCallers = [...new Set([...vh7Callers, ...rwaCallers])].filter(f => f !== '(global)');

keyCallers.forEach(callerName => {
  traverse(ast, {
    FunctionDeclaration(path) {
      if (path.node.id?.name !== callerName) return;

      console.log(`\n📦 函数: ${callerName}`);
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
      console.log(`   参数: ${path.node.params.map(p => p.name || '?').join(', ')}`);

      const body = generate(path.node.body).code;
      console.log('\n   代码:');
      // 格式化显示
      const lines = body.split('\n');
      lines.slice(0, 20).forEach((line, i) => {
        console.log(`   ${String(i+1).padStart(3)}: ${line}`);
      });
      if (lines.length > 20) {
        console.log(`   ... (还有 ${lines.length - 20} 行)`);
      }

      path.stop();
    }
  });
});

console.log();
console.log('='.repeat(70));
console.log('分析完成');
console.log('='.repeat(70));
