/**
 * 增强 CLI 语法验证工具
 * 检查：语法错误、未定义变量引用、补丁完整性
 */
import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const traverse = _traverse.default || _traverse;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const args = process.argv.slice(2);
const cliPath = args[0] || path.join(__dirname, '../src/main/resources/bundled/claude-cli-2.1.17-enhanced.mjs');

console.log('=== Enhanced CLI Syntax Validator ===');
console.log('File:', cliPath);

const code = fs.readFileSync(cliPath, 'utf-8');
console.log('Size:', (code.length / 1024 / 1024).toFixed(2), 'MB');

let errors = [];
let warnings = [];

// 1. Strict syntax check (Babel)
console.log('');
console.log('1. Syntax check (Babel strict mode)...');
let ast;
try {
  ast = parse(code, { 
    sourceType: 'module', 
    plugins: ['jsx'],
    errorRecovery: false
  });
  console.log('   OK: Syntax valid');
} catch (e) {
  const loc = e.loc ? 'line ' + e.loc.line : 'unknown location';
  console.log('   ERROR at', loc, ':', e.message);
  errors.push('Syntax error: ' + e.message);
}

// 2. Check for common patch errors
console.log('');
console.log('2. Common error patterns...');

// Check for undefined variable patterns that patches might introduce
const dangerousPatterns = [
  { regex: /\bundefined\.(\w+)/g, desc: 'Accessing property of undefined' },
  { regex: /\bnull\.(\w+)/g, desc: 'Accessing property of null' },
];

dangerousPatterns.forEach(function(p) {
  const matches = code.match(p.regex);
  if (matches && matches.length > 0) {
    console.log('   WARN:', p.desc, '- found', matches.length, 'instances');
    warnings.push(p.desc);
  }
});

if (warnings.length === 0) {
  console.log('   OK: No dangerous patterns found');
}

// 3. Patch validations
console.log('');
console.log('3. Patch completeness check...');
const checks = [
  ['agent_run_to_background', code.includes('"agent_run_to_background"')],
  ['run_to_background', code.includes('"run_to_background"')],
  ['skill_parent_tool_use_id (sourceToolUseID)', code.includes('sourceToolUseID||null') || code.includes('sourceToolUseID || null')],
  ['mcp_reconnect', code.includes('"mcp_reconnect"')],
  // mcp_disable/enable 已禁用（官方 2.1.19+ 内置 mcp_toggle），检查官方实现
  ['mcp_toggle (官方内置)', code.includes('"mcp_toggle"')],
  ['mcp_tools', code.includes('"mcp_tools"')],
  ['get_chrome_status', code.includes('"get_chrome_status"')],
  ['get_capabilities', code.includes('"get_capabilities"')],
  // v2 版本: 检测 H=A[0]?.parentUuid 模式 (最小侵入方案)
  ['parentUuid (v2: H=...parentUuid)', code.includes('?.parentUuid||') || code.includes('__parentUuid')],
];

let passed = 0;
let failed = 0;
checks.forEach(function(item) {
  const name = item[0];
  const ok = item[1];
  console.log('  ', ok ? 'OK' : 'FAIL', name);
  if (ok) { passed++; } else { failed++; errors.push('Missing patch: ' + name); }
});

// 4. AST-level validation for injected code
console.log('');
console.log('4. AST validation for injected code...');
if (ast) {
  let controlCommandsFound = 0;
  traverse(ast, {
    StringLiteral(p) {
      const val = p.node.value;
      if (['agent_run_to_background', 'run_to_background', 'mcp_reconnect', 'get_capabilities'].includes(val)) {
        controlCommandsFound++;
      }
    }
  });
  console.log('   Found', controlCommandsFound, 'control command strings in AST');
  if (controlCommandsFound >= 4) {
    console.log('   OK: Control commands present in AST');
  } else {
    console.log('   WARN: Expected at least 4 control commands');
    warnings.push('Low control command count: ' + controlCommandsFound);
  }
}

// Summary
console.log('');
console.log('=== Summary ===');
console.log('Patch checks:', passed, 'passed,', failed, 'failed');
console.log('Errors:', errors.length);
console.log('Warnings:', warnings.length);

if (errors.length > 0) {
  console.log('');
  console.log('Errors:');
  errors.forEach(function(e, i) { console.log('  ' + (i+1) + '.', e); });
}

if (warnings.length > 0) {
  console.log('');
  console.log('Warnings:');
  warnings.forEach(function(w, i) { console.log('  ' + (i+1) + '.', w); });
}

console.log('');
if (errors.length > 0) {
  console.log('VALIDATION FAILED');
  process.exit(1);
} else {
  console.log('VALIDATION PASSED');
}
