#!/usr/bin/env node

/**
 * 检查 Node.js 版本兼容性
 */

const semver = require('semver');
const fs = require('fs');
const path = require('path');

// 读取 package.json 中的最低版本要求
function getMinimumNodeVersion() {
    try {
        const packagePath = path.join(__dirname, '..', 'package.json');
        const packageJson = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
        return packageJson.engines?.node || '>=18.0.0';
    } catch (error) {
        console.warn('无法读取 package.json，使用默认最低版本要求');
        return '>=18.0.0';
    }
}

function checkNodeVersion() {
    const currentVersion = process.version;
    const minimumVersion = getMinimumNodeVersion();
    
    console.log(`当前 Node.js 版本: ${currentVersion}`);
    console.log(`最低版本要求: ${minimumVersion}`);
    
    if (semver.satisfies(currentVersion, minimumVersion)) {
        console.log('✅ Node.js 版本兼容');
        return true;
    } else {
        console.error('❌ Node.js 版本不兼容');
        console.error(`请升级到 Node.js ${minimumVersion.replace('>=', '')} 或更高版本`);
        console.error('Claude SDK 需要 Node.js 18+ 支持');
        return false;
    }
}

function checkESFeatures() {
    const features = [
        { name: 'async/await', check: () => eval('(async () => {})') },
        { name: 'Object.entries', check: () => typeof Object.entries === 'function' },
        { name: 'Array.includes', check: () => typeof Array.prototype.includes === 'function' },
        { name: 'Promise', check: () => typeof Promise === 'function' }
    ];
    
    console.log('\n检查 JavaScript 特性支持:');
    let allSupported = true;
    
    features.forEach(feature => {
        try {
            feature.check();
            console.log(`✅ ${feature.name}`);
        } catch (error) {
            console.log(`❌ ${feature.name}`);
            allSupported = false;
        }
    });
    
    return allSupported;
}

function main() {
    console.log('Node.js 环境兼容性检查\n');
    
    const versionOk = checkNodeVersion();
    const featuresOk = checkESFeatures();
    
    if (versionOk && featuresOk) {
        console.log('\n🎉 所有检查通过，环境兼容！');
        process.exit(0);
    } else {
        console.log('\n💥 环境检查失败，请检查 Node.js 版本');
        process.exit(1);
    }
}

// 如果直接运行此脚本
if (require.main === module) {
    main();
}

module.exports = { checkNodeVersion, checkESFeatures };