#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

async function createStandaloneBundle() {
    try {
        console.log('创建独立的 Node.js 服务包...\n');
        
        const projectRoot = path.resolve(__dirname, '..');
        const distDir = path.join(projectRoot, 'dist');
        const standaloneFile = path.join(distDir, 'server-standalone.js');
        
        // 确保 dist 目录存在
        if (!fs.existsSync(distDir)) {
            fs.mkdirSync(distDir, { recursive: true });
        }
        
        console.log('使用 esbuild 打包...');
        
        // 安装 esbuild 如果还没有
        try {
            require.resolve('esbuild');
        } catch (e) {
            console.log('安装 esbuild...');
            await execPromise('npm install --save-dev esbuild', { cwd: projectRoot });
        }
        
        // 使用 esbuild 打包
        const esbuild = require('esbuild');
        
        await esbuild.build({
            entryPoints: [path.join(distDir, 'server.js')],
            bundle: true,
            outfile: standaloneFile,
            platform: 'node',
            target: 'node18',
            format: 'cjs',
            minify: false,
            sourcemap: false,
            external: [
                '@anthropic-ai/claude-code' // 这个包不能打包，需要单独处理
            ],
            banner: {
                js: `#!/usr/bin/env node
/**
 * Claude SDK Wrapper Server - Standalone Bundle
 * Node.js 18+ required
 */

// 检查 Node.js 版本
const nodeVersion = process.version;
const majorVersion = parseInt(nodeVersion.slice(1).split('.')[0]);
if (majorVersion < 18) {
    console.error('错误：需要 Node.js 18.0.0 或更高版本');
    console.error('当前版本：' + nodeVersion);
    process.exit(1);
}
`
            }
        });
        
        // 使文件可执行
        fs.chmodSync(standaloneFile, '755');
        
        console.log(`\n✅ 独立服务包创建成功！`);
        console.log(`文件位置: ${standaloneFile}`);
        console.log(`文件大小: ${(fs.statSync(standaloneFile).size / 1024).toFixed(2)} KB`);
        
        return standaloneFile;
        
    } catch (error) {
        console.error('❌ 创建失败：', error.message);
        throw error;
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    createStandaloneBundle().catch(err => {
        process.exit(1);
    });
}

module.exports = { createStandaloneBundle };