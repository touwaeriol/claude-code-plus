#!/usr/bin/env node

/**
 * 打包脚本：将编译后的 JS 和依赖打包成单个文件
 */

const fs = require('fs');
const path = require('path');

function createBundleScript() {
    const distDir = path.join(__dirname, '..', 'dist');
    const serverFile = path.join(distDir, 'server.js');
    const bundleFile = path.join(distDir, 'server-bundle.js');
    
    if (!fs.existsSync(serverFile)) {
        throw new Error('server.js not found. Please run "npm run build" first.');
    }
    
    console.log('创建打包文件...');
    
    // 读取主服务文件
    const serverCode = fs.readFileSync(serverFile, 'utf8');
    
    // 创建打包后的代码
    const bundleCode = `#!/usr/bin/env node

/**
 * Claude SDK Wrapper Server Bundle
 * 这是一个自包含的 Node.js 服务文件，兼容 Node.js 18+
 */

// 检查 Node.js 版本
const nodeVersion = process.version;
const majorVersion = parseInt(nodeVersion.slice(1).split('.')[0]);
if (majorVersion < 18) {
    console.error('错误：需要 Node.js 18.0.0 或更高版本 (Claude SDK 要求)');
    console.error('当前版本：' + nodeVersion);
    process.exit(1);
}

// 设置环境变量
process.env.NODE_ENV = process.env.NODE_ENV || 'production';

// 原始服务代码
${serverCode}
`;
    
    // 写入打包文件
    fs.writeFileSync(bundleFile, bundleCode);
    
    // 设置执行权限
    try {
        fs.chmodSync(bundleFile, '755');
    } catch (error) {
        console.warn('无法设置执行权限：', error.message);
    }
    
    console.log('打包完成：', bundleFile);
    
    // 显示文件大小
    const stats = fs.statSync(bundleFile);
    console.log('文件大小：', Math.round(stats.size / 1024), 'KB');
    
    return bundleFile;
}

function copyDependencies() {
    const srcNodeModules = path.join(__dirname, '..', 'node_modules');
    const distDir = path.join(__dirname, '..', 'dist');
    const distNodeModules = path.join(distDir, 'node_modules');
    
    // 只复制生产依赖
    const packageJson = require('../package.json');
    const productionDeps = Object.keys(packageJson.dependencies || {});
    
    console.log('复制生产依赖：', productionDeps.length, '个包');
    
    if (fs.existsSync(distNodeModules)) {
        fs.rmSync(distNodeModules, { recursive: true, force: true });
    }
    fs.mkdirSync(distNodeModules, { recursive: true });
    
    productionDeps.forEach(dep => {
        const srcPath = path.join(srcNodeModules, dep);
        const destPath = path.join(distNodeModules, dep);
        
        if (fs.existsSync(srcPath)) {
            copyRecursive(srcPath, destPath);
            console.log('  ✓', dep);
        } else {
            console.warn('  ⚠️ 未找到依赖:', dep);
        }
    });
}

function copyRecursive(src, dest) {
    const stat = fs.statSync(src);
    
    if (stat.isDirectory()) {
        if (!fs.existsSync(dest)) {
            fs.mkdirSync(dest, { recursive: true });
        }
        
        const files = fs.readdirSync(src);
        files.forEach(file => {
            copyRecursive(path.join(src, file), path.join(dest, file));
        });
    } else {
        fs.copyFileSync(src, dest);
    }
}

function main() {
    try {
        console.log('开始打包 Claude SDK Wrapper...\n');
        
        // 创建打包文件
        const bundleFile = createBundleScript();
        
        // 复制依赖
        copyDependencies();
        
        console.log('\n✅ 打包完成！');
        console.log('可以使用以下命令运行：');
        console.log('  node', bundleFile);
        
    } catch (error) {
        console.error('❌ 打包失败：', error.message);
        process.exit(1);
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    main();
}

module.exports = { createBundleScript, copyDependencies };