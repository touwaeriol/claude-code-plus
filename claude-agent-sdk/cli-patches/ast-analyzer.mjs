/**
 * CLI 源码 AST 分析工具
 */
import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const traverse = _traverse.default || _traverse;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 支持命令行参数指定 CLI 文件
const cliFile = process.argv[2] || 'claude-cli-2.1.50.js';
const cliPath = path.isAbsolute(cliFile) ? cliFile : path.join(__dirname, cliFile);
console.log('Reading CLI source:', cliPath);
const code = fs.readFileSync(cliPath, 'utf-8');

console.log('Parsing AST...');
const ast = parse(code, { sourceType: 'module', plugins: ['jsx'], errorRecovery: true });

const findings = {
  taskToolDefinitions: [],
  parentToolUseIdAssignments: [],
  parentToolUseIdReferences: [],
  skillReferences: [],
  sourceToolUseIDRefs: [],
};

function getLoc(node) {
  if (!node.loc) return 'unknown';
  return 'line ' + node.loc.start.line;
}

function getSnippet(node, maxLen = 150) {
  const start = node.start;
  const end = Math.min(node.end, start + maxLen);
  let s = code.slice(start, end);
  if (node.end > end) s += '...';
  return s.replace(/\n/g, ' ');
}

console.log('Traversing AST...');

traverse(ast, {
  VariableDeclarator(p) {
    const init = p.node.init;
    if (init && init.type === 'StringLiteral') {
      if (init.value === 'Task') {
        findings.taskToolDefinitions.push({ varName: p.node.id.name, loc: getLoc(p.node), code: getSnippet(p.node) });
      }
      if (init.value === 'Skill') {
        findings.skillReferences.push({ varName: p.node.id.name, loc: getLoc(p.node), code: getSnippet(p.node) });
      }
    }
  },
  AssignmentExpression(p) {
    const left = p.node.left;
    if (left.type === 'MemberExpression') {
      const prop = left.property;
      if ((prop.type === 'Identifier' && prop.name === 'parent_tool_use_id') ||
          (prop.type === 'StringLiteral' && prop.value === 'parent_tool_use_id')) {
        findings.parentToolUseIdAssignments.push({ loc: getLoc(p.node), code: getSnippet(p.node, 200) });
      }
    }
  },
  ObjectProperty(p) {
    const key = p.node.key;
    if ((key.type === 'Identifier' && key.name === 'parent_tool_use_id') ||
        (key.type === 'StringLiteral' && key.value === 'parent_tool_use_id')) {
      const value = p.node.value;
      const isNonNull = !(value.type === 'NullLiteral' || (value.type === 'Identifier' && value.name === 'null'));
      findings.parentToolUseIdReferences.push({ loc: getLoc(p.node), code: getSnippet(p.node, 200), value: getSnippet(value, 50), isNonNull });
    }
  },
  MemberExpression(p) {
    const prop = p.node.property;
    if (prop.type === 'Identifier' && prop.name === 'sourceToolUseID') {
      findings.sourceToolUseIDRefs.push({ loc: getLoc(p.node), code: getSnippet(p.parentPath.node, 150) });
    }
  },
});

console.log('\n=== AST Acselysis Results ===\n');

console.log('1. Task tool definitions:');
findings.taskToolDefinitions.forEach((fitem, i) => console.log('  [' + (i+1) + '] var ' + fitem.varName + ' @ ' + fitem.loc + ': ' + fitem.code));

console.log('\n2. Skill tool definitions:');
findings.skillReferences.forEach((fitem, i) => console.log('  [' + (i+1) + '] var ' + fitem.varName + ' @ ' + fitem.loc + ': ' + fitem.code));

console.log('\n3. parent_tool_use_id assignments:');
if (findings.parentToolUseIdAssignments.length === 0) console.log('  (none found)');
else findings.parentToolUseIdAssignments.forEach((fitem, i) => console.log('  [' + (i+1) + '] @ ' + fitem.loc + ': ' + fitem.code));

console.log('\n4. parent_tool_use_id object properties:');
const nonNull = findings.parentToolUseIdReferences.filter(f => f.isNonNull);
const nulls = findings.parentToolUseIdReferences.filter(f => !f.isNonNull);
console.log('  Non-null values (' + nonNull.length + '):');
if (nonNull.length === 0) console.log('    (none - CLI never sets non-null parent_tool_use_id!)');
else nonNull.forEach((fitem, i) => console.log('    [' + (i+1) + '] @ ' + fitem.loc + ', value: ' + fitem.value));
console.log('  Null values (' + nulls.length + '):');
nulls.slice(0, 5).forEach((fitem, i) => console.log('    [' + (i+1) + '] @ ' + fitem.loc + ': ' + fitem.code));
if (nulls.length > 5) console.log('    ... and ' + (nulls.length - 5) + ' more');

console.log('\n5. sourceToolUseID accesses:');
findings.sourceToolUseIDRefs.forEach((fitem, i) => console.log('  [' + (i+1) + '] @ ' + fitem.loc + ': ' + fitem.code));

const outPath = path.join(__dirname, 'ast-analysis-result.json');
fs.writeFileSync(outPath, JSON.stringify(findings, null, 2));
console.log('\nFull results saved to: ' + outPath);
