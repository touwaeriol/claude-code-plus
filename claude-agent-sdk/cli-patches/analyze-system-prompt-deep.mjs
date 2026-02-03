/**
 * 深度分析 Claude CLI 系统提示词
 * 提取完整的内置提示词内容
 */

import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import fs from 'fs';

const traverse = _traverse.default || _traverse;

const cliFile = process.argv[2] || 'claude-cli-2.1.27.js';
const code = fs.readFileSync(cliFile, 'utf8');

console.log('='.repeat(80));
console.log('Claude CLI 完整系统提示词提取');
console.log('='.repeat(80));

// 解析 AST
const ast = parse(code, {
    sourceType: 'module',
    errorRecovery: true,
    plugins: ['jsx']
});

// 存储找到的提示词
const prompts = {
    identity: [],        // 身份声明
    fullPrompts: [],     // 完整提示词
    templateLiterals: [] // 模板字符串提示词
};

// 查找所有长字符串（可能是提示词）
traverse(ast, {
    // 查找变量声明中的长字符串
    VariableDeclarator(path) {
        const node = path.node;
        const name = node.id?.name;
        
        if (node.init?.type === 'StringLiteral') {
            const value = node.init.value;
            // 查找包含特定关键词的字符串
            if (value.includes('You are') && value.length > 100) {
                prompts.fullPrompts.push({
                    varName: name,
                    content: value,
                    line: node.loc?.start.line,
                    length: value.length
                });
            }
        }
        
        // 查找模板字符串
        if (node.init?.type === 'TemplateLiteral') {
            const quasis = node.init.quasis;
            const fullText = quasis.map(q => q.value.raw).join('${...}');
            
            if ((fullText.includes('You are') || fullText.includes('system prompt') || 
                 fullText.includes('Claude Code')) && fullText.length > 200) {
                prompts.templateLiterals.push({
                    varName: name,
                    content: fullText,
                    line: node.loc?.start.line,
                    length: fullText.length,
                    hasInterpolation: quasis.length > 1
                });
            }
        }
    },
    
    // 查找模板字面量（直接在代码中的）
    TemplateLiteral(path) {
        const node = path.node;
        const quasis = node.quasis;
        const fullText = quasis.map(q => q.value.raw).join('${...}');
        
        // 查找核心系统提示词
        if (fullText.includes('You are an interactive CLI tool') ||
            fullText.includes('pair programming') ||
            fullText.includes('You have access to a set of tools')) {
            prompts.fullPrompts.push({
                varName: '[inline template]',
                content: fullText,
                line: node.loc?.start.line,
                length: fullText.length
            });
        }
    }
});

// 特别查找 getSystemPrompt 函数中的模板字符串
traverse(ast, {
    ObjectProperty(path) {
        const node = path.node;
        if (node.key?.name === 'getSystemPrompt') {
            if (node.value?.type === 'ArrowFunctionExpression') {
                const body = node.value.body;
                if (body?.type === 'TemplateLiteral') {
                    const fullText = body.quasis.map(q => q.value.raw).join('${...}');
                    if (fullText.length > 100) {
                        prompts.fullPrompts.push({
                            varName: 'getSystemPrompt() 返回值',
                            content: fullText,
                            line: node.loc?.start.line,
                            length: fullText.length
                        });
                    }
                }
            }
        }
    }
});

// 打印结果
console.log('\n' + '='.repeat(80));
console.log('找到的完整系统提示词');
console.log('='.repeat(80));

// 去重并排序
const uniquePrompts = [];
const seenContent = new Set();

[...prompts.fullPrompts, ...prompts.templateLiterals].forEach(p => {
    const key = p.content.substring(0, 100);
    if (!seenContent.has(key)) {
        seenContent.add(key);
        uniquePrompts.push(p);
    }
});

uniquePrompts.sort((a, b) => b.length - a.length);

uniquePrompts.forEach((p, i) => {
    console.log(`\n${'─'.repeat(80)}`);
    console.log(`【提示词 #${i + 1}】`);
    console.log(`变量名: ${p.varName}`);
    console.log(`行号: ${p.line}`);
    console.log(`长度: ${p.length} 字符`);
    console.log(`${'─'.repeat(40)}`);
    console.log('内容:');
    console.log(p.content);
});

console.log('\n' + '='.repeat(80));
console.log(`总计找到 ${uniquePrompts.length} 个系统提示词`);
console.log('='.repeat(80));
