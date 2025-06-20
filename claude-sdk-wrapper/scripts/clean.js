#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

function cleanDirectory(dirPath, description) {
    if (fs.existsSync(dirPath)) {
        console.log(`Cleaning ${description}...`);
        const files = fs.readdirSync(dirPath);
        if (files.length > 0) {
            console.log(`  Found ${files.length} items to remove`);
        }
        fs.rmSync(dirPath, { recursive: true, force: true });
        console.log(`  ✓ ${description} cleaned`);
    } else {
        console.log(`${description} already clean`);
    }
}

// 清理目录
const projectRoot = path.resolve(__dirname, '..');
const distDir = path.join(projectRoot, 'dist');
const targetDir = path.join(projectRoot, '../src/main/resources/claude-node');

console.log('🧹 Cleaning build directories...\n');

// 清理 dist 目录
cleanDirectory(distDir, 'dist directory');

// 清理 claude-node 资源目录
cleanDirectory(targetDir, 'claude-node resources');

console.log('\n✅ Cleanup completed!');