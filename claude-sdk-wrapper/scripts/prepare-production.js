#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function prepareProduction() {
    console.log('准备生产环境...\n');
    
    const projectRoot = path.resolve(__dirname, '..');
    const tempDir = path.join(projectRoot, 'dist/production-temp');
    const targetDir = path.join(projectRoot, 'dist/production');
    
    try {
        // 清理旧的生产目录
        if (fs.existsSync(targetDir)) {
            fs.rmSync(targetDir, { recursive: true, force: true });
        }
        if (fs.existsSync(tempDir)) {
            fs.rmSync(tempDir, { recursive: true, force: true });
        }
        
        // 创建临时目录
        fs.mkdirSync(tempDir, { recursive: true });
        
        // 复制必要文件到临时目录
        fs.copyFileSync(
            path.join(projectRoot, 'package.json'),
            path.join(tempDir, 'package.json')
        );
        
        // 只安装生产依赖
        console.log('安装生产依赖...');
        execSync('npm install --omit=dev --omit=optional', {
            cwd: tempDir,
            stdio: 'inherit'
        });
        
        // 创建目标目录
        fs.mkdirSync(targetDir, { recursive: true });
        
        // 复制编译后的文件
        const distDir = path.join(projectRoot, 'dist');
        const files = fs.readdirSync(distDir);
        files.forEach(file => {
            if (file.endsWith('.js') || file.endsWith('.mjs')) {
                fs.copyFileSync(
                    path.join(distDir, file),
                    path.join(targetDir, file)
                );
            }
        });
        
        // 复制服务目录
        const servicesDir = path.join(distDir, 'services');
        const routesDir = path.join(distDir, 'routes');
        
        if (fs.existsSync(servicesDir)) {
            fs.cpSync(servicesDir, path.join(targetDir, 'services'), { recursive: true });
        }
        if (fs.existsSync(routesDir)) {
            fs.cpSync(routesDir, path.join(targetDir, 'routes'), { recursive: true });
        }
        
        // 移动 node_modules
        fs.renameSync(
            path.join(tempDir, 'node_modules'),
            path.join(targetDir, 'node_modules')
        );
        
        // 复制 package.json
        fs.copyFileSync(
            path.join(tempDir, 'package.json'),
            path.join(targetDir, 'package.json')
        );
        
        // 清理临时目录
        fs.rmSync(tempDir, { recursive: true, force: true });
        
        console.log('\n✅ 生产环境准备完成！');
        console.log(`位置: ${targetDir}`);
        
    } catch (error) {
        console.error('❌ 准备失败：', error.message);
        // 清理
        if (fs.existsSync(tempDir)) {
            fs.rmSync(tempDir, { recursive: true, force: true });
        }
        throw error;
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    prepareProduction();
}

module.exports = { prepareProduction };