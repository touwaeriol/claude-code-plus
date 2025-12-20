/**
 * AST 分析脚本 - 分析 CLI 的历史会话加载逻辑
 *
 * 目标：
 * 1. 找到 CLI 如何从 JSONL 加载历史消息
 * 2. 分析消息树算法（parentUuid 处理）
 * 3. 分析中断响应、编辑重发等特殊情况的处理
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(70));
console.log('CLI 历史会话加载逻辑分析');
console.log('='.repeat(70));
console.log();

// ========================================
// 1. 查找 getMessageTree 或类似的消息树构建函数
// ========================================
console.log('【1】查找消息树相关函数');
console.log('-'.repeat(70));

const messageTreeFunctions = [];

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    // 查找可能与消息树相关的函数
    const relevantNames = [
      'getMessageTree', 'buildMessageTree', 'loadMessages',
      'getMessages', 'getHistory', 'loadHistory',
      'getConversation', 'loadConversation',
      'rebuildFromMessages', 'getMessagesUpToLeaf'
    ];

    if (relevantNames.some(n => name.toLowerCase().includes(n.toLowerCase()))) {
      messageTreeFunctions.push({
        name,
        line: path.node.loc?.start?.line,
        params: path.node.params.map(p => p.name || '?').join(', ')
      });
    }
  },
  FunctionDeclaration(path) {
    const name = path.node.id?.name;
    if (!name) return;

    if (name.includes('Message') || name.includes('Tree') || name.includes('History')) {
      messageTreeFunctions.push({
        name,
        line: path.node.loc?.start?.line,
        params: path.node.params.map(p => p.name || '?').join(', ')
      });
    }
  }
});

console.log(`找到 ${messageTreeFunctions.length} 个相关函数:`);
messageTreeFunctions.slice(0, 15).forEach(f => {
  console.log(`  - ${f.name}(${f.params}) at line ${f.line}`);
});

// ========================================
// 2. 查找 parentUuid 的使用模式
// ========================================
console.log();
console.log('【2】分析 parentUuid 的使用场景');
console.log('-'.repeat(70));

const parentUuidUsages = new Map();

traverse(ast, {
  MemberExpression(path) {
    const code = generate(path.node).code;
    if (code.includes('.parentUuid') && code.length < 80) {
      // 获取上下文
      let funcName = '(global)';
      const funcParent = path.getFunctionParent();
      if (funcParent) {
        if (funcParent.node.id?.name) {
          funcName = funcParent.node.id.name;
        } else if (funcParent.node.key?.name) {
          funcName = funcParent.node.key.name;
        }
      }

      if (!parentUuidUsages.has(funcName)) {
        parentUuidUsages.set(funcName, []);
      }
      const usages = parentUuidUsages.get(funcName);
      if (!usages.includes(code)) {
        usages.push(code);
      }
    }
  }
});

console.log('parentUuid 在以下函数中使用:');
for (const [funcName, usages] of parentUuidUsages) {
  console.log(`  📦 ${funcName}:`);
  usages.slice(0, 5).forEach(u => console.log(`      - ${u}`));
}

// ========================================
// 3. 查找叶节点选择逻辑
// ========================================
console.log();
console.log('【3】查找叶节点选择逻辑');
console.log('-'.repeat(70));

// 查找 "leaf" 相关的代码
let leafPatterns = [];
traverse(ast, {
  Identifier(path) {
    if (path.node.name.toLowerCase().includes('leaf')) {
      const parent = path.parentPath;
      if (parent) {
        const code = generate(parent.node).code;
        if (code.length < 150 && !leafPatterns.includes(code)) {
          leafPatterns.push(code);
        }
      }
    }
  }
});

console.log(`找到 ${leafPatterns.length} 个 leaf 相关模式:`);
leafPatterns.slice(0, 10).forEach((p, i) => {
  console.log(`  ${i + 1}. ${p.substring(0, 100)}${p.length > 100 ? '...' : ''}`);
});

// ========================================
// 4. 查找 getMessagesUpToLeaf 或类似函数的实现
// ========================================
console.log();
console.log('【4】查找消息回溯算法');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    // 查找回溯相关的函数
    if (name.includes('Leaf') || name.includes('Path') || name.includes('Chain')) {
      console.log(`✅ 找到函数: ${name}`);
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);

      // 分析函数体中的关键操作
      path.traverse({
        WhileStatement(innerPath) {
          const test = generate(innerPath.node.test).code;
          console.log(`   while 条件: ${test.substring(0, 80)}`);
        },
        CallExpression(innerPath) {
          const callee = generate(innerPath.node.callee).code;
          if (callee.includes('unshift') || callee.includes('push') || callee.includes('get')) {
            const full = generate(innerPath.node).code;
            if (full.length < 100) {
              console.log(`   操作: ${full}`);
            }
          }
        }
      });
      console.log();
    }
  }
});

// ========================================
// 5. 查找 JSONL 解析逻辑
// ========================================
console.log();
console.log('【5】查找 JSONL 解析逻辑');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    // 查找文件读取相关的函数
    if (name.includes('load') || name.includes('read') || name.includes('parse')) {
      // 检查是否涉及 jsonl 或 history
      let hasJsonl = false;
      let hasHistory = false;

      path.traverse({
        StringLiteral(innerPath) {
          const value = innerPath.node.value;
          if (value.includes('jsonl')) hasJsonl = true;
          if (value.includes('history') || value.includes('sessions')) hasHistory = true;
        },
        Identifier(innerPath) {
          const name = innerPath.node.name;
          if (name.toLowerCase().includes('jsonl')) hasJsonl = true;
          if (name.toLowerCase().includes('history')) hasHistory = true;
        }
      });

      if (hasJsonl || hasHistory) {
        console.log(`✅ 找到: ${name}`);
        console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
        console.log(`   涉及: ${hasJsonl ? 'JSONL ' : ''}${hasHistory ? 'History' : ''}`);
        console.log();
      }
    }
  }
});

// ========================================
// 6. 查找 insertMessageChain 的 parentUuid 参数使用
// ========================================
console.log();
console.log('【6】分析 insertMessageChain 的 parentUuid 处理');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (name !== 'insertMessageChain') return;

    console.log('✅ 找到 insertMessageChain 方法');
    console.log(`   参数: ${path.node.params.map(p => p.name || '?').join(', ')}`);
    console.log(`   位置: 行 ${path.node.loc?.start?.line}`);

    // 分析函数体中如何使用 parentUuid 参数
    const parentUuidParam = path.node.params[3]?.name; // 第4个参数
    if (parentUuidParam) {
      console.log(`   parentUuid 参数名: ${parentUuidParam}`);

      path.traverse({
        AssignmentExpression(innerPath) {
          const code = generate(innerPath.node).code;
          if (code.includes(parentUuidParam)) {
            console.log(`   赋值: ${code.substring(0, 100)}`);
          }
        },
        MemberExpression(innerPath) {
          const code = generate(innerPath.node).code;
          if (code.includes('parentUuid') && code.length < 80) {
            console.log(`   访问: ${code}`);
          }
        }
      });
    }

    path.stop();
  }
});

// ========================================
// 7. 查找消息类型判断逻辑
// ========================================
console.log();
console.log('【7】查找消息类型判断（user/assistant/tool_use 等）');
console.log('-'.repeat(70));

const typeChecks = new Set();
traverse(ast, {
  BinaryExpression(path) {
    if (path.node.operator !== '===') return;

    const left = generate(path.node.left).code;
    const right = generate(path.node.right).code;

    // 检查是否是类型判断
    if (left.includes('.type') || right.includes('.type')) {
      const check = generate(path.node).code;
      if (check.length < 100) {
        typeChecks.add(check);
      }
    }
  }
});

console.log('消息类型判断模式:');
Array.from(typeChecks).slice(0, 15).forEach(c => {
  console.log(`  - ${c}`);
});

// ========================================
// 8. 查找中断响应相关逻辑
// ========================================
console.log();
console.log('【8】查找中断响应逻辑');
console.log('-'.repeat(70));

traverse(ast, {
  StringLiteral(path) {
    const value = path.node.value;
    if (value === 'interrupt' || value === 'interrupted') {
      const parent = path.parentPath?.parentPath;
      if (parent) {
        const code = generate(parent.node).code;
        if (code.length < 150) {
          console.log(`  ${code}`);
        }
      }
    }
  }
});

// ========================================
// 9. 总结关键发现
// ========================================
console.log();
console.log('='.repeat(70));
console.log('分析总结');
console.log('='.repeat(70));
console.log(`
关键发现：
1. parentUuid 使用场景: ${parentUuidUsages.size} 个函数中使用
2. 叶节点相关模式: ${leafPatterns.length} 个
3. 消息类型判断: ${typeChecks.size} 种模式

下一步验证：
- insertMessageChain 如何处理 parentUuid 参数
- 历史加载时如何选择正确的分支
- 编辑重发时的消息树更新逻辑
`);
