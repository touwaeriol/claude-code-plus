/**
 * AST 分析脚本 - 深入分析消息树算法
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(70));
console.log('消息树算法深入分析');
console.log('='.repeat(70));
console.log();

// ========================================
// 1. 找到 VH7 函数（包含叶节点查找逻辑）
// ========================================
console.log('【1】分析 VH7 函数（叶节点查找）');
console.log('-'.repeat(70));

traverse(ast, {
  FunctionDeclaration(path) {
    if (path.node.id?.name === 'VH7') {
      console.log('✅ 找到 VH7 函数');
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
      console.log(`   参数: ${path.node.params.map(p => p.name).join(', ')}`);

      // 打印函数体
      const body = generate(path.node.body).code;
      console.log('\n   函数体:');
      console.log('   ' + body.split('\n').slice(0, 30).join('\n   '));
      if (body.split('\n').length > 30) {
        console.log('   ... (更多)');
      }

      path.stop();
    }
  }
});

// ========================================
// 2. 找到 insertMessageChain 的完整实现
// ========================================
console.log();
console.log('【2】分析 insertMessageChain 函数');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    if (path.node.key?.name !== 'insertMessageChain') return;

    console.log('✅ 找到 insertMessageChain 方法');
    console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
    console.log(`   参数: ${path.node.params.map(p => {
      if (p.type === 'Identifier') return p.name;
      if (p.type === 'AssignmentPattern') return `${p.left.name}=...`;
      return '?';
    }).join(', ')}`);

    // 打印函数体
    const body = generate(path.node.body).code;
    console.log('\n   函数体 (前50行):');
    const lines = body.split('\n').slice(0, 50);
    lines.forEach((line, i) => {
      console.log(`   ${String(i+1).padStart(3)}: ${line}`);
    });

    path.stop();
  }
});

// ========================================
// 3. 找到 getMessagesUpToLeaf 或类似函数
// ========================================
console.log();
console.log('【3】查找消息回溯函数');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    if (name.includes('Leaf') || name.includes('UpTo') || name === 'getMessages') {
      console.log(`✅ ${name}`);
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);
      console.log(`   参数: ${path.node.params.map(p => p.name || '?').join(', ')}`);

      // 检查是否有 while 循环（回溯特征）
      let hasWhile = false;
      path.traverse({
        WhileStatement() {
          hasWhile = true;
        }
      });
      console.log(`   有 while 循环: ${hasWhile}`);

      // 简短预览
      const body = generate(path.node.body).code;
      if (body.length < 500) {
        console.log(`   代码: ${body.substring(0, 200)}...`);
      }
      console.log();
    }
  }
});

// ========================================
// 4. 分析 rebuildFromMessages 或类似函数
// ========================================
console.log();
console.log('【4】查找 rebuild 相关函数');
console.log('-'.repeat(70));

traverse(ast, {
  ObjectMethod(path) {
    const name = path.node.key?.name;
    if (!name) return;

    if (name.toLowerCase().includes('rebuild') || name.toLowerCase().includes('restore')) {
      console.log(`✅ ${name}`);
      console.log(`   位置: 行 ${path.node.loc?.start?.line}`);

      const body = generate(path.node.body).code;
      console.log(`   代码长度: ${body.length} 字符`);

      // 检查关键操作
      path.traverse({
        CallExpression(innerPath) {
          const callee = generate(innerPath.node.callee).code;
          if (callee.includes('Message') || callee.includes('insert') || callee.includes('set')) {
            const full = generate(innerPath.node).code;
            if (full.length < 100) {
              console.log(`   调用: ${full}`);
            }
          }
        }
      });
      console.log();
    }
  }
});

// ========================================
// 5. 找到消息 Map 的结构
// ========================================
console.log();
console.log('【5】分析消息存储结构');
console.log('-'.repeat(70));

// 查找 new Map() 用于存储消息的模式
let mapUsages = [];
traverse(ast, {
  NewExpression(path) {
    if (path.node.callee?.name !== 'Map') return;

    const parent = path.parentPath;
    if (parent?.isVariableDeclarator()) {
      const varName = parent.node.id?.name;
      if (varName) {
        mapUsages.push({
          name: varName,
          line: path.node.loc?.start?.line
        });
      }
    }
  }
});

console.log(`找到 ${mapUsages.length} 个 Map 实例`);
mapUsages.slice(0, 10).forEach(m => {
  console.log(`  - ${m.name} at line ${m.line}`);
});

// ========================================
// 6. 分析 UUID 生成和分配
// ========================================
console.log();
console.log('【6】分析 UUID 处理');
console.log('-'.repeat(70));

// 查找 uuid 相关的赋值
let uuidAssignments = [];
traverse(ast, {
  AssignmentExpression(path) {
    const left = generate(path.node.left).code;
    if (left.includes('uuid') && left.length < 50) {
      const right = generate(path.node.right).code;
      if (right.length < 100) {
        uuidAssignments.push(`${left} = ${right}`);
      }
    }
  },
  ObjectProperty(path) {
    const key = path.node.key?.name || path.node.key?.value;
    if (key === 'uuid' || key === 'parentUuid') {
      const value = generate(path.node.value).code;
      if (value.length < 80) {
        uuidAssignments.push(`${key}: ${value}`);
      }
    }
  }
});

console.log('UUID 相关赋值:');
// 去重并显示
const uniqueUuid = [...new Set(uuidAssignments)];
uniqueUuid.slice(0, 20).forEach(u => {
  console.log(`  ${u}`);
});

// ========================================
// 7. 分析 stopReason / finishReason 处理
// ========================================
console.log();
console.log('【7】分析中断/完成原因处理');
console.log('-'.repeat(70));

traverse(ast, {
  MemberExpression(path) {
    const code = generate(path.node).code;
    if (code.includes('stopReason') || code.includes('finishReason') || code.includes('stop_reason')) {
      const parent = path.parentPath;
      if (parent?.isBinaryExpression() || parent?.isAssignmentExpression()) {
        const full = generate(parent.node).code;
        if (full.length < 100) {
          console.log(`  ${full}`);
        }
      }
    }
  }
});

console.log();
console.log('='.repeat(70));
console.log('分析完成');
console.log('='.repeat(70));
