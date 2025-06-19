#!/usr/bin/env node
// 安装依赖脚本

const { execSync } = require('child_process');
const path = require('path');

console.log('Installing Claude SDK dependencies...');

try {
    // 使用 npm ci 安装精确版本的依赖
    execSync('npm ci --omit=dev --omit=optional', {
        cwd: __dirname,
        stdio: 'inherit'
    });
    console.log('Dependencies installed successfully!');
} catch (error) {
    console.error('Failed to install dependencies:', error.message);
    process.exit(1);
}
