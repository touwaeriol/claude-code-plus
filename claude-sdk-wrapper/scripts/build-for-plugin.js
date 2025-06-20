#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function buildForPlugin() {
    console.log('Building Claude SDK Wrapper for IntelliJ Plugin...\n');
    
    const projectRoot = path.resolve(__dirname, '..');
    const distDir = path.join(projectRoot, 'dist');
    const targetDir = path.join(projectRoot, '../src/main/resources/claude-node');
    
    try {
        // 1. 清理目标目录
        console.log('1. Cleaning target directory...');
        if (fs.existsSync(targetDir)) {
            // 列出将要删除的文件
            const existingFiles = fs.readdirSync(targetDir);
            if (existingFiles.length > 0) {
                console.log('   Removing existing files:');
                existingFiles.forEach(file => {
                    console.log(`   - ${file}`);
                });
            }
            fs.rmSync(targetDir, { recursive: true, force: true });
        }
        fs.mkdirSync(targetDir, { recursive: true });
        
        // 2. 复制编译后的 JavaScript 文件
        console.log('2. Copying compiled JavaScript files...');
        
        // 复制主文件
        const mainFiles = ['server.js', 'server-esm-wrapper.mjs'];
        mainFiles.forEach(file => {
            const src = path.join(distDir, file);
            const dest = path.join(targetDir, file);
            if (fs.existsSync(src)) {
                fs.copyFileSync(src, dest);
                console.log(`   ✓ ${file}`);
            }
        });
        
        // 复制服务和路由目录
        const dirs = ['services', 'routes', 'utils'];
        dirs.forEach(dir => {
            const srcDir = path.join(distDir, dir);
            const destDir = path.join(targetDir, dir);
            if (fs.existsSync(srcDir)) {
                fs.cpSync(srcDir, destDir, { recursive: true });
                console.log(`   ✓ ${dir}/`);
            }
        });
        
        // 3. 创建精简版 package.json（只包含运行时依赖）
        console.log('3. Creating minimal package.json...');
        const originalPackage = JSON.parse(
            fs.readFileSync(path.join(projectRoot, 'package.json'), 'utf8')
        );
        
        const minimalPackage = {
            name: originalPackage.name,
            version: originalPackage.version,
            description: originalPackage.description,
            main: 'server.js',
            engines: originalPackage.engines,
            dependencies: originalPackage.dependencies
        };
        
        fs.writeFileSync(
            path.join(targetDir, 'package.json'),
            JSON.stringify(minimalPackage, null, 2)
        );
        console.log('   ✓ package.json');
        
        // 4. 复制 package-lock.json（用于确保版本一致性）
        console.log('4. Copying package-lock.json...');
        const lockFile = path.join(projectRoot, 'package-lock.json');
        if (fs.existsSync(lockFile)) {
            fs.copyFileSync(lockFile, path.join(targetDir, 'package-lock.json'));
            console.log('   ✓ package-lock.json');
        }
        
        // 5. 复制 node_modules（包含所有依赖）
        console.log('5. Copying node_modules...');
        const nodeModulesSource = path.join(projectRoot, 'node_modules');
        const nodeModulesTarget = path.join(targetDir, 'node_modules');
        
        if (fs.existsSync(nodeModulesSource)) {
            fs.cpSync(nodeModulesSource, nodeModulesTarget, { recursive: true });
            console.log('   ✓ node_modules (all dependencies included)');
        } else {
            console.error('   ✗ node_modules not found! Run npm install first.');
        }
        
        // 6. 创建启动脚本
        console.log('6. Creating startup script...');
        const startupScript = `#!/usr/bin/env node
// Claude SDK Server Startup Script
// This script ensures the server runs properly within the IntelliJ plugin environment

const path = require('path');
const { spawn } = require('child_process');

// 确保工作目录正确
process.chdir(__dirname);

// 直接启动服务器（依赖已经打包）
require('./server.js');
`;
        
        fs.writeFileSync(
            path.join(targetDir, 'start.js'),
            startupScript
        );
        fs.chmodSync(path.join(targetDir, 'start.js'), '755');
        console.log('   ✓ start.js');
        
        // 7. 创建安装脚本（供插件使用）
        console.log('7. Creating install script...');
        const installScript = `#!/usr/bin/env node
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
`;
        
        fs.writeFileSync(
            path.join(targetDir, 'install.js'),
            installScript
        );
        fs.chmodSync(path.join(targetDir, 'install.js'), '755');
        console.log('   ✓ install.js');
        
        // 8. 复制检查脚本
        console.log('8. Copying check scripts...');
        const checkScript = path.join(projectRoot, 'scripts/check-node-version.js');
        if (fs.existsSync(checkScript)) {
            const scriptsDir = path.join(targetDir, 'scripts');
            fs.mkdirSync(scriptsDir, { recursive: true });
            fs.copyFileSync(checkScript, path.join(scriptsDir, 'check-node-version.js'));
            console.log('   ✓ scripts/check-node-version.js');
        }
        
        // 9. 创建 README
        console.log('9. Creating README...');
        const readme = `# Claude SDK Node Service

This is the compiled Node.js service for the Claude Code Plus IntelliJ plugin.

## Structure
- server.js - Main server entry point
- services/ - Service modules
- routes/ - HTTP and WebSocket route handlers
- start.js - Startup script
- install.js - Dependency installation script

## Usage

1. Install dependencies (first time only):
   \`\`\`
   node install.js
   \`\`\`

2. Start the server:
   \`\`\`
   node start.js --port 18080
   \`\`\`

## Requirements
- Node.js >= 18.0.0

## Note
This is a compiled version for the IntelliJ plugin. 
Do not modify these files directly - they will be overwritten during builds.
`;
        
        fs.writeFileSync(path.join(targetDir, 'README.md'), readme);
        console.log('   ✓ README.md');
        
        console.log('\n✅ Build completed successfully!');
        console.log(`📁 Output directory: ${targetDir}`);
        
        // 显示文件统计
        const files = countFiles(targetDir);
        console.log(`📊 Total files: ${files}`);
        
    } catch (error) {
        console.error('\n❌ Build failed:', error.message);
        process.exit(1);
    }
}

function countFiles(dir) {
    let count = 0;
    const items = fs.readdirSync(dir);
    
    for (const item of items) {
        const fullPath = path.join(dir, item);
        const stat = fs.statSync(fullPath);
        
        if (stat.isDirectory()) {
            count += countFiles(fullPath);
        } else {
            count++;
        }
    }
    
    return count;
}

// 执行构建
if (require.main === module) {
    buildForPlugin();
}

module.exports = { buildForPlugin };