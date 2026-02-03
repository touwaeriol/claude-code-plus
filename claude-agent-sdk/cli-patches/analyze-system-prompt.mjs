/**
 * 分析 Claude CLI 中 --system-prompt 和 --append-system-prompt 参数的功能
 * 以及内置的提示词
 */

import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import fs from 'fs';
import path from 'path';

const traverse = _traverse.default || _traverse;

const cliFile = process.argv[2] || 'claude-cli-2.1.27.js';
const code = fs.readFileSync(cliFile, 'utf8');

console.log('='.repeat(80));
console.log('Claude CLI System Prompt 分析');
console.log('='.repeat(80));
console.log(`\n分析文件: ${cliFile}\n`);

// 解析 AST
const ast = parse(code, {
    sourceType: 'module',
    errorRecovery: true,
    plugins: ['jsx']
});

const results = {
    // 命令行参数定义
    cliOptions: [],
    // 系统提示词变量
    systemPromptVars: [],
    // getSystemPrompt 函数调用
    getSystemPromptCalls: [],
    // 默认系统提示词
    defaultPrompts: [],
    // 提示词组装逻辑
    promptAssemblyLogic: []
};

traverse(ast, {
    // 查找命令行参数定义
    NewExpression(path) {
        const node = path.node;
        if (node.callee.name && node.arguments.length >= 1) {
            const firstArg = node.arguments[0];
            if (firstArg.type === 'StringLiteral' && firstArg.value.includes('system-prompt')) {
                const description = node.arguments[1]?.value || '';
                results.cliOptions.push({
                    option: firstArg.value,
                    description: description,
                    line: node.loc?.start.line
                });
            }
        }
    },
    
    // 查找变量声明中的系统提示词
    VariableDeclarator(path) {
        const node = path.node;
        const name = node.id?.name;
        
        // 查找包含 "You are Claude" 的字符串
        if (node.init?.type === 'StringLiteral') {
            const value = node.init.value;
            if (value.includes('You are Claude') || value.includes('Claude Code')) {
                results.systemPromptVars.push({
                    name: name,
                    value: value.substring(0, 200) + (value.length > 200 ? '...' : ''),
                    fullLength: value.length,
                    line: node.loc?.start.line
                });
            }
        }
        
        // 查找以 systemPrompt 或 SystemPrompt 相关的变量名
        if (name && (name.toLowerCase().includes('systemprompt') || name.toLowerCase().includes('prompt'))) {
            if (node.init?.type === 'StringLiteral' && node.init.value.length > 50) {
                results.defaultPrompts.push({
                    name: name,
                    preview: node.init.value.substring(0, 300) + (node.init.value.length > 300 ? '...' : ''),
                    fullLength: node.init.value.length,
                    line: node.loc?.start.line
                });
            }
        }
    },
    
    // 查找 getSystemPrompt 方法定义
    ObjectProperty(path) {
        const node = path.node;
        if (node.key?.name === 'getSystemPrompt' || node.key?.value === 'getSystemPrompt') {
            let promptContent = '';
            
            // 尝试提取提示词内容
            if (node.value?.type === 'ArrowFunctionExpression') {
                const body = node.value.body;
                if (body?.type === 'TemplateLiteral') {
                    promptContent = body.quasis.map(q => q.value.raw).join('${...}');
                } else if (body?.type === 'Identifier') {
                    promptContent = `[变量引用: ${body.name}]`;
                }
            }
            
            results.getSystemPromptCalls.push({
                location: `line ${node.loc?.start.line}`,
                contentPreview: promptContent.substring(0, 300) + (promptContent.length > 300 ? '...' : ''),
                fullLength: promptContent.length
            });
        }
    },
    
    // 查找系统提示词组装函数
    FunctionDeclaration(path) {
        const node = path.node;
        const name = node.id?.name;
        // 查找处理 systemPrompt 参数的函数
        if (node.params?.some(p => {
            if (p.type === 'ObjectPattern') {
                return p.properties?.some(prop => 
                    prop.key?.name?.toLowerCase().includes('systemprompt') ||
                    prop.key?.name?.toLowerCase().includes('appendsystem')
                );
            }
            return false;
        })) {
            results.promptAssemblyLogic.push({
                functionName: name,
                line: node.loc?.start.line,
                params: path.node.params.map(p => {
                    if (p.type === 'ObjectPattern') {
                        return p.properties?.map(prop => prop.key?.name).join(', ');
                    }
                    return p.name;
                }).join(', ')
            });
        }
    }
});

// 打印结果
console.log('\n' + '='.repeat(80));
console.log('1. 命令行参数定义');
console.log('='.repeat(80));
results.cliOptions.forEach(opt => {
    console.log(`\n行 ${opt.line}:`);
    console.log(`  参数: ${opt.option}`);
    console.log(`  描述: ${opt.description}`);
});

console.log('\n' + '='.repeat(80));
console.log('2. 系统提示词相关变量');
console.log('='.repeat(80));
results.systemPromptVars.forEach(v => {
    console.log(`\n行 ${v.line}: ${v.name}`);
    console.log(`  长度: ${v.fullLength} 字符`);
    console.log(`  内容预览: ${v.value}`);
});

console.log('\n' + '='.repeat(80));
console.log('3. getSystemPrompt 方法定义');
console.log('='.repeat(80));
results.getSystemPromptCalls.forEach(call => {
    console.log(`\n${call.location}:`);
    console.log(`  内容预览: ${call.contentPreview}`);
    console.log(`  完整长度: ${call.fullLength} 字符`);
});

console.log('\n' + '='.repeat(80));
console.log('4. 提示词组装逻辑');
console.log('='.repeat(80));
results.promptAssemblyLogic.forEach(fn => {
    console.log(`\n行 ${fn.line}: 函数 ${fn.functionName}`);
    console.log(`  参数: ${fn.params}`);
});

console.log('\n' + '='.repeat(80));
console.log('5. 默认提示词变量');
console.log('='.repeat(80));
results.defaultPrompts.slice(0, 10).forEach(p => {
    console.log(`\n行 ${p.line}: ${p.name}`);
    console.log(`  长度: ${p.fullLength} 字符`);
    console.log(`  预览: ${p.preview}`);
});

// 统计
console.log('\n' + '='.repeat(80));
console.log('统计');
console.log('='.repeat(80));
console.log(`命令行参数: ${results.cliOptions.length}`);
console.log(`系统提示词变量: ${results.systemPromptVars.length}`);
console.log(`getSystemPrompt 定义: ${results.getSystemPromptCalls.length}`);
console.log(`提示词组装函数: ${results.promptAssemblyLogic.length}`);
console.log(`默认提示词变量: ${results.defaultPrompts.length}`);
