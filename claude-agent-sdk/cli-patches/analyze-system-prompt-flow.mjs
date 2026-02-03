/**
 * 深入分析 Claude CLI 中 --system-prompt 和 --append-system-prompt 的完整处理流程
 * 追踪从命令行参数到实际 API 调用的完整链路
 */

import { parse } from '@babel/parser';
import _traverse from '@babel/traverse';
import _generate from '@babel/generator';
import fs from 'fs';

const traverse = _traverse.default || _traverse;
const generate = _generate.default || _generate;

const cliFile = process.argv[2] || 'claude-cli-2.1.19.js';
const code = fs.readFileSync(cliFile, 'utf8');

console.log('='.repeat(80));
console.log('Claude CLI --system-prompt 完整流程分析');
console.log('='.repeat(80));
console.log(`\n分析文件: ${cliFile}\n`);

// 解析 AST
const ast = parse(code, {
    sourceType: 'module',
    errorRecovery: true,
    plugins: ['jsx']
});

const results = {
    // 命令行参数处理
    cliParsing: [],
    // systemPrompt 参数传递链
    paramFlow: [],
    // 系统提示词组装函数
    assemblyFunctions: [],
    // API 调用点
    apiCalls: [],
    // customSystemPrompt vs appendSystemPrompt 区分
    promptTypes: {
        custom: [],
        append: [],
        override: []
    },
    // 核心变量追踪
    coreVariables: []
};

traverse(ast, {
    // 1. 查找 commander 参数定义 (.option 调用)
    CallExpression(path) {
        const node = path.node;
        const callee = node.callee;
        
        // 检查 .option() 调用
        if (callee.type === 'MemberExpression' && 
            callee.property?.name === 'option' &&
            node.arguments.length >= 2) {
            const firstArg = node.arguments[0];
            if (firstArg.type === 'StringLiteral' && 
                firstArg.value.includes('system-prompt')) {
                results.cliParsing.push({
                    option: firstArg.value,
                    description: node.arguments[1]?.value || '',
                    line: node.loc?.start.line
                });
            }
        }
        
        // 检查 API 调用 (调用 Anthropic API)
        if (callee.type === 'MemberExpression') {
            const objectCode = generate(callee.object).code;
            const methodName = callee.property?.name || callee.property?.value;
            
            // 查找 messages.create 或类似调用
            if (methodName === 'create' && 
                (objectCode.includes('messages') || objectCode.includes('client'))) {
                
                // 检查参数中是否有 system
                if (node.arguments.length > 0) {
                    const arg = node.arguments[0];
                    if (arg.type === 'ObjectExpression') {
                        for (const prop of arg.properties) {
                            if (prop.key?.name === 'system' || prop.key?.value === 'system') {
                                results.apiCalls.push({
                                    line: node.loc?.start.line,
                                    method: `${objectCode}.${methodName}`,
                                    systemProp: generate(prop.value).code.substring(0, 100)
                                });
                            }
                        }
                    }
                }
            }
        }
    },
    
    // 2. 查找函数中 systemPrompt 相关参数
    FunctionDeclaration(path) {
        const node = path.node;
        const name = node.id?.name;
        
        // 检查参数中的 systemPrompt 相关
        const hasSystemPromptParam = node.params?.some(p => {
            if (p.type === 'ObjectPattern') {
                return p.properties?.some(prop => {
                    const keyName = prop.key?.name || '';
                    return keyName.toLowerCase().includes('systemprompt');
                });
            }
            return false;
        });
        
        if (hasSystemPromptParam) {
            // 提取相关参数名
            const systemParams = [];
            node.params?.forEach(p => {
                if (p.type === 'ObjectPattern') {
                    p.properties?.forEach(prop => {
                        const keyName = prop.key?.name || '';
                        if (keyName.toLowerCase().includes('systemprompt') || 
                            keyName.toLowerCase().includes('append')) {
                            systemParams.push(keyName);
                        }
                    });
                }
            });
            
            results.assemblyFunctions.push({
                name: name,
                line: node.loc?.start.line,
                systemParams: systemParams,
                paramCount: node.params?.length || 0
            });
        }
    },
    
    // 3. 查找箭头函数中的 systemPrompt 处理
    ArrowFunctionExpression(path) {
        const node = path.node;
        const parent = path.parent;
        
        // 检查参数中的 systemPrompt 相关
        const hasSystemPromptParam = node.params?.some(p => {
            if (p.type === 'ObjectPattern') {
                return p.properties?.some(prop => {
                    const keyName = prop.key?.name || '';
                    return keyName.toLowerCase().includes('systemprompt');
                });
            }
            return false;
        });
        
        if (hasSystemPromptParam) {
            let funcName = 'anonymous';
            if (parent.type === 'VariableDeclarator' && parent.id?.name) {
                funcName = parent.id.name;
            }
            
            const systemParams = [];
            node.params?.forEach(p => {
                if (p.type === 'ObjectPattern') {
                    p.properties?.forEach(prop => {
                        const keyName = prop.key?.name || '';
                        if (keyName.toLowerCase().includes('systemprompt') || 
                            keyName.toLowerCase().includes('append') ||
                            keyName.toLowerCase().includes('custom') ||
                            keyName.toLowerCase().includes('override')) {
                            systemParams.push(keyName);
                        }
                    });
                }
            });
            
            if (systemParams.length > 0) {
                results.paramFlow.push({
                    name: funcName,
                    line: node.loc?.start.line,
                    systemParams: systemParams
                });
            }
        }
    },
    
    // 4. 查找 systemPrompt 组装逻辑（字符串拼接、模板字符串）
    TemplateLiteral(path) {
        const code = generate(path.node).code;
        if (code.includes('systemPrompt') || code.includes('SystemPrompt')) {
            const parentFunc = path.getFunctionParent();
            results.promptTypes.custom.push({
                line: path.node.loc?.start.line,
                context: parentFunc?.node?.id?.name || 'anonymous',
                preview: code.substring(0, 150)
            });
        }
    },
    
    // 5. 查找条件判断 (customSystemPrompt ? ... : ...)
    ConditionalExpression(path) {
        const testCode = generate(path.node.test).code;
        if (testCode.includes('customSystemPrompt') || 
            testCode.includes('appendSystemPrompt') ||
            testCode.includes('overrideSystemPrompt')) {
            results.promptTypes.append.push({
                line: path.node.loc?.start.line,
                condition: testCode.substring(0, 100),
                consequent: generate(path.node.consequent).code.substring(0, 80),
                alternate: generate(path.node.alternate).code.substring(0, 80)
            });
        }
    },
    
    // 6. 查找 fX1 函数（核心组装函数）
    VariableDeclarator(path) {
        const node = path.node;
        const name = node.id?.name;
        
        // 追踪关键变量
        if (name && node.init) {
            const initCode = generate(node.init).code;
            
            // 查找 defaultSystemPrompt 定义
            if (initCode.includes('You are Claude Code') && initCode.length > 50) {
                results.coreVariables.push({
                    name: name,
                    line: node.loc?.start.line,
                    type: 'defaultSystemPrompt',
                    preview: initCode.substring(0, 200)
                });
            }
            
            // 查找涉及 prompt 拼接的变量
            if ((name.toLowerCase().includes('prompt') || name.toLowerCase().includes('system')) &&
                initCode.includes('+') && initCode.includes('systemPrompt')) {
                results.coreVariables.push({
                    name: name,
                    line: node.loc?.start.line,
                    type: 'promptConcatenation',
                    preview: initCode.substring(0, 200)
                });
            }
        }
    }
});

// 打印分析结果
console.log('\n' + '='.repeat(80));
console.log('1. 命令行参数定义 (.option)');
console.log('='.repeat(80));
results.cliParsing.forEach(opt => {
    console.log(`\n行 ${opt.line}:`);
    console.log(`  参数: ${opt.option}`);
    console.log(`  描述: ${opt.description}`);
});

console.log('\n' + '='.repeat(80));
console.log('2. 带 systemPrompt 参数的函数');
console.log('='.repeat(80));
results.assemblyFunctions.forEach(fn => {
    console.log(`\n行 ${fn.line}: 函数 ${fn.name}`);
    console.log(`  system相关参数: ${fn.systemParams.join(', ')}`);
});

console.log('\n' + '='.repeat(80));
console.log('3. systemPrompt 参数传递链');
console.log('='.repeat(80));
results.paramFlow.slice(0, 20).forEach(flow => {
    console.log(`\n行 ${flow.line}: ${flow.name}`);
    console.log(`  参数: ${flow.systemParams.join(', ')}`);
});

console.log('\n' + '='.repeat(80));
console.log('4. API 调用点 (system 参数)');
console.log('='.repeat(80));
results.apiCalls.forEach(call => {
    console.log(`\n行 ${call.line}:`);
    console.log(`  方法: ${call.method}`);
    console.log(`  system值: ${call.systemProp}`);
});

console.log('\n' + '='.repeat(80));
console.log('5. customSystemPrompt/appendSystemPrompt 条件判断');
console.log('='.repeat(80));
results.promptTypes.append.forEach(p => {
    console.log(`\n行 ${p.line}:`);
    console.log(`  条件: ${p.condition}`);
    console.log(`  真值: ${p.consequent}`);
    console.log(`  假值: ${p.alternate}`);
});

console.log('\n' + '='.repeat(80));
console.log('6. 核心变量追踪');
console.log('='.repeat(80));
results.coreVariables.forEach(v => {
    console.log(`\n行 ${v.line}: ${v.name} [${v.type}]`);
    console.log(`  预览: ${v.preview}`);
});

// 统计
console.log('\n' + '='.repeat(80));
console.log('统计');
console.log('='.repeat(80));
console.log(`命令行参数: ${results.cliParsing.length}`);
console.log(`组装函数: ${results.assemblyFunctions.length}`);
console.log(`参数传递节点: ${results.paramFlow.length}`);
console.log(`API调用点: ${results.apiCalls.length}`);
console.log(`条件判断: ${results.promptTypes.append.length}`);
console.log(`核心变量: ${results.coreVariables.length}`);

// 保存结果为 JSON
fs.writeFileSync('system-prompt-analysis.json', JSON.stringify(results, null, 2));
console.log('\n详细结果已保存到 system-prompt-analysis.json');
