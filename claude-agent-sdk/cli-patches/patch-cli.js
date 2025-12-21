#!/usr/bin/env node
/**
 * Claude CLI AST Patcher
 *
 * 使用 Babel AST 转换来增强 Claude CLI，比字符串替换更可靠。
 *
 * 用法:
 *   node patch-cli.js <input-cli.js> <output-cli.js>
 *   node patch-cli.js --dry-run <input-cli.js>
 */

const fs = require('fs');
const path = require('path');
const parser = require('@babel/parser');
const traverse = require('@babel/traverse').default;
const generate = require('@babel/generator').default;
const t = require('@babel/types');

// 加载所有补丁
const patches = require('./patches');

// 命令行参数解析
const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const inputFile = args.find(a => !a.startsWith('--') && a.endsWith('.js'));
const outputFile = args.find((a, i) => !a.startsWith('--') && a.endsWith('.js') && args.indexOf(a) !== args.indexOf(inputFile));

if (!inputFile) {
  console.error('用法: node patch-cli.js <input-cli.js> [output-cli.js]');
  console.error('      node patch-cli.js --dry-run <input-cli.js>');
  process.exit(1);
}

console.log('========================================');
console.log('Claude CLI AST Patcher');
console.log('========================================');
console.log(`输入文件: ${inputFile}`);
console.log(`输出文件: ${outputFile || '(dry-run 模式)'}`);
console.log(`模式: ${dryRun ? 'dry-run (仅验证)' : '应用补丁'}`);
console.log();

// 读取源代码
console.log('📖 读取 CLI 源代码...');
const sourceCode = fs.readFileSync(inputFile, 'utf-8');
console.log(`   大小: ${(sourceCode.length / 1024 / 1024).toFixed(2)} MB`);

// 解析为 AST
console.log('🔍 解析 AST (这可能需要一些时间)...');
const startParse = Date.now();

let ast;
try {
  ast = parser.parse(sourceCode, {
    sourceType: 'script',
    plugins: [],
    errorRecovery: true,  // 容错模式
  });
  console.log(`   ✅ 解析完成 (${Date.now() - startParse}ms)`);
} catch (err) {
  console.error(`   ❌ 解析失败: ${err.message}`);
  process.exit(1);
}

// 应用补丁
console.log();
console.log('🔧 应用补丁...');

const patchResults = {
  applied: [],
  failed: [],
  skipped: []
};

// 补丁上下文 - 用于在补丁之间共享信息
const patchContext = {
  // 记录找到的关键变量名
  foundVariables: {},
  // 记录修改位置
  modifications: []
};

for (const patch of patches) {
  console.log();
  console.log(`📦 补丁: ${patch.id}`);
  console.log(`   描述: ${patch.description}`);

  if (patch.disabled) {
    console.log(`   ⏭️  已禁用，跳过`);
    patchResults.skipped.push(patch.id);
    continue;
  }

  try {
    const result = patch.apply(ast, t, traverse, patchContext);

    if (result.success) {
      console.log(`   ✅ 成功应用`);
      if (result.details) {
        result.details.forEach(d => console.log(`      - ${d}`));
      }
      patchResults.applied.push(patch.id);
    } else {
      console.log(`   ❌ 应用失败: ${result.reason}`);
      if (patch.required) {
        patchResults.failed.push(patch.id);
      } else {
        patchResults.skipped.push(patch.id);
      }
    }
  } catch (err) {
    console.log(`   ❌ 异常: ${err.message}`);
    if (patch.required) {
      patchResults.failed.push(patch.id);
    } else {
      patchResults.skipped.push(patch.id);
    }
  }
}

// 生成代码
console.log();
console.log('📝 生成增强版代码...');
const startGenerate = Date.now();

const output = generate(ast, {
  compact: true,  // 保持压缩格式
  comments: false,
  minified: true
}, sourceCode);

console.log(`   ✅ 生成完成 (${Date.now() - startGenerate}ms)`);
console.log(`   大小: ${(output.code.length / 1024 / 1024).toFixed(2)} MB`);
console.log(`   变化: ${output.code.length - sourceCode.length > 0 ? '+' : ''}${output.code.length - sourceCode.length} bytes`);

// 验证
console.log();
console.log('🔍 验证补丁结果...');

const verifications = [
  { pattern: 'agent_run_to_background', desc: 'Agent 后台控制命令 (v5)' },
  { pattern: '__sdkBackgroundResolver', desc: '子代理 background resolver (兼容)' },
  { pattern: '__sdkBackgroundResolvers', desc: '多任务 resolver Map (v4+)' },
  { pattern: 'get_chrome_status', desc: 'Chrome 状态控制命令' },
  { pattern: '__parentUuid', desc: 'SDK parentUuid 支持 (编辑重发)' },
  { pattern: 'mcp_reconnect', desc: 'MCP 重连控制命令', optional: true },
  { pattern: 'mcp_tools', desc: 'MCP 工具列表控制命令', optional: true },
];

let verifyPassed = 0;
let verifyFailed = 0;

for (const v of verifications) {
  if (output.code.includes(v.pattern)) {
    console.log(`   ✅ ${v.desc}: 已找到 '${v.pattern}'`);
    verifyPassed++;
  } else if (v.optional) {
    console.log(`   ⏭️  ${v.desc}: 未找到 '${v.pattern}' (可选，跳过)`);
    // 可选验证不计入失败
  } else {
    console.log(`   ❌ ${v.desc}: 未找到 '${v.pattern}'`);
    verifyFailed++;
  }
}

// 输出结果
console.log();
console.log('========================================');
console.log('结果汇总');
console.log('========================================');
console.log(`补丁应用: ${patchResults.applied.length} 成功, ${patchResults.failed.length} 失败, ${patchResults.skipped.length} 跳过`);
console.log(`验证结果: ${verifyPassed} 通过, ${verifyFailed} 失败`);

if (patchResults.failed.length > 0) {
  console.log();
  console.log('❌ 以下必需补丁应用失败:');
  patchResults.failed.forEach(p => console.log(`   - ${p}`));
  process.exit(1);
}

if (verifyFailed > 0) {
  console.log();
  console.log('❌ 验证失败，补丁可能未正确应用');
  process.exit(1);
}

// 写入输出文件
if (!dryRun && outputFile) {
  console.log();
  console.log(`💾 写入文件: ${outputFile}`);
  fs.writeFileSync(outputFile, output.code, 'utf-8');
  console.log('   ✅ 完成');
}

console.log();
console.log('========================================');
console.log('✅ 所有操作完成!');
console.log('========================================');
