/**
 * 分析 CLI 的后台任务处理机制
 */

const fs = require('fs');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;

const code = fs.readFileSync('claude-cli-2.0.73.js', 'utf-8');
const ast = parser.parse(code, { sourceType: 'script', errorRecovery: true });

console.log('='.repeat(60));
console.log('后台任务机制分析');
console.log('='.repeat(60));
console.log();

// 1. 查找 useInput 或类似的键盘输入处理
console.log('【1】查找 Ink useInput / 键盘处理');
console.log('-'.repeat(60));

traverse(ast, {
  CallExpression(path) {
    const callee = path.get('callee').toString();
    if (callee === 'useInput' || callee.endsWith('.useInput')) {
      console.log('✅ 找到 useInput 调用');
      console.log('   位置: 行', path.node.loc?.start?.line);

      // 查看回调函数
      const args = path.node.arguments;
      if (args.length > 0 && args[0].type === 'ArrowFunctionExpression') {
        const body = path.get('arguments.0.body').toString().substring(0, 200);
        console.log('   回调内容:', body.substring(0, 150) + '...');
      }
    }
  }
});

console.log();
console.log('【2】查找 onBackground 回调');
console.log('-'.repeat(60));

let onBackgroundCount = 0;
traverse(ast, {
  ObjectProperty(path) {
    const keyName = path.node.key?.name || path.node.key?.value;
    if (keyName === 'onBackground') {
      onBackgroundCount++;
      if (onBackgroundCount <= 5) {
        console.log(`✅ 找到 onBackground 属性 #${onBackgroundCount}`);
        console.log('   位置: 行', path.node.loc?.start?.line);

        const value = path.get('value').toString().substring(0, 100);
        console.log('   值:', value + '...');
      }
    }
  }
});
console.log(`共找到 ${onBackgroundCount} 处 onBackground`);

console.log();
console.log('【3】查找 Ctrl+B (\\x02) 的处理');
console.log('-'.repeat(60));

traverse(ast, {
  StringLiteral(path) {
    if (path.node.value === '\x02' || path.node.value === 'b') {
      const parent = path.parentPath;
      const grandParent = parent?.parentPath;

      // 检查是否在条件判断中
      if (parent.isBinaryExpression() || grandParent?.isIfStatement()) {
        console.log('✅ 找到 Ctrl+B 检测');
        console.log('   位置: 行', path.node.loc?.start?.line);
        console.log('   上下文:', parent.toString().substring(0, 100));
      }
    }
  }
});

console.log();
console.log('【4】查找 handleReadable / readable 事件');
console.log('-'.repeat(60));

traverse(ast, {
  Identifier(path) {
    if (path.node.name === 'handleReadable' || path.node.name === 'onReadable') {
      console.log('✅ 找到:', path.node.name);
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  },
  CallExpression(path) {
    const code = path.toString();
    if (code.includes('.on(') && code.includes('readable')) {
      console.log('✅ 找到 readable 事件监听');
      console.log('   代码:', code.substring(0, 80));
    }
  }
});

console.log();
console.log('【5】structuredInput 的定义');
console.log('-'.repeat(60));

traverse(ast, {
  ClassProperty(path) {
    if (path.node.key?.name === 'structuredInput') {
      console.log('✅ 找到 structuredInput 属性');
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  },
  ObjectProperty(path) {
    if (path.node.key?.name === 'structuredInput') {
      console.log('✅ 找到 structuredInput 属性');
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  },
  MethodDefinition(path) {
    if (path.node.key?.name === 'structuredInput') {
      console.log('✅ 找到 structuredInput getter');
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  }
});

console.log();
console.log('【6】分析 processLine 函数');
console.log('-'.repeat(60));

traverse(ast, {
  FunctionDeclaration(path) {
    if (path.node.id?.name === 'processLine') {
      console.log('✅ 找到 processLine 函数');
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  },
  VariableDeclarator(path) {
    if (path.node.id?.name === 'processLine') {
      console.log('✅ 找到 processLine 变量');
      console.log('   位置: 行', path.node.loc?.start?.line);
    }
  },
  ObjectMethod(path) {
    if (path.node.key?.name === 'processLine') {
      console.log('✅ 找到 processLine 方法');
      console.log('   位置: 行', path.node.loc?.start?.line);

      // 查看方法体
      const body = path.get('body').toString().substring(0, 300);
      console.log('   内容:', body.substring(0, 200) + '...');
    }
  }
});

console.log();
console.log('='.repeat(60));
console.log('分析完成');
console.log('='.repeat(60));
