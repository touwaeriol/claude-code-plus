/**
 * 验证增强后的 CLI 代码
 */

import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import _generate from '@babel/generator';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const traverse = _traverse.default || _traverse;
const generate = _generate.default || _generate;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 支持命令行参数指定文件
const enhancedFile = process.argv[2] || '../src/main/resources/bundled/claude-cli-2.1.50-enhanced.mjs';
const enhancedPath = path.isAbsolute(enhancedFile) ? enhancedFile : path.join(__dirname, enhancedFile);
console.log('Reading enhanced CLI:', enhancedPath);
const code = fs.readFileSync(enhancedPath, 'utf-8');

console.log('Parsing AST...');
let ast;
try {
  ast = parse(code, { sourceType: 'module', plugins: ['jsx'], errorRecovery: true });
  console.log('AST ParSe SUCCESS\n');
} catch (e) {
  console.error('AST PARSE FAILED:', e.message);
  process.exit(1);
}

console.log('=== Verifying skill_parent_tool_use_id patch ===\n');

let foundTs5 = false;
let ts5FnName = null;
let sourceToolUseIDCount = 0;
let parentToolUseIdNullCount = 0;
let parentToolUseIdNonNullCount = 0;

traverse(ast, {
  FunctionDeclaration(path) {
    if (!path.node.generator) return;
    
    // 查找包含 parent_tool_use_id 和 sourceToolUseID 的 generator 函数（Ts5/Ls5/stY）
    const funcCode = generate(path.node).code;
    if (!funcCode.includes('parent_tool_use_id') || !funcCode.includes('sourceToolUseID')) return;
    
    foundTs5 = true;
    ts5FnName = path.node.id?.name || 'anonymous';
    console.log(`Found Ts5-like function "${ts5FnName}" at line`, path.node.loc?.start.line);
    
    // Check parent_tool_use_id usages
    path.traverse({
      ObjectProperty(innerPath) {
        const key = innerPath.node.key;
        if (key.type === 'Identifier' && key.name === 'parent_tool_use_id') {
          const value = generate(innerPath.node.value).code;
          if (value.includes('sourceToolUseID')) {
            sourceToolUseIDCount++;
            console.log('  [OK] parent_tool_use_id:', value);
          } else if (value === 'null') {
            parentToolUseIdNullCount++;
            console.log('  [SKIP] parent_tool_use_id: null (progress/tool_progress case)');
          } else {
            parentToolUseIdNonNullCount++;
            console.log('  [OK - non-null] parent_tool_use_id:', value);
          }
        }
      }
    });
    
    path.stop();
  }
});

console.log('\n=== Verification Results ===');
console.log('Found Ts5-like function:', foundTs5 ? `YES (${ts5FnName})` : 'NO');
console.log('sourceToolUseID usages:', sourceToolUseIDCount);
console.log('parentToolUseID usages (non-null):', parentToolUseIdNonNullCount);
console.log('null usages (progress/tool_progress cases):', parentToolUseIdNullCount);

if (foundTs5 && sourceToolUseIDCount >= 2) {
  console.log('\nVERIFICATION PASSED: skill_parent_tool_use_id patch is correctly applied');
} else {
  console.log('\nVERIFICATION FAILED');
  process.exit(1);
}
