#!/usr/bin/env node

const esbuild = require('esbuild');
const path = require('path');
const fs = require('fs');

async function bundleWithEsbuild() {
    console.log('🚀 Building Node service with esbuild...\n');
    
    const projectRoot = path.resolve(__dirname, '..');
    const distDir = path.join(projectRoot, 'dist');
    const targetDir = path.join(projectRoot, '../src/main/resources/claude-node');
    
    try {
        // 0. 检查 dist 目录是否存在
        if (!fs.existsSync(distDir) || !fs.existsSync(path.join(distDir, 'server.js'))) {
            console.error('❌ Error: dist directory not found or server.js not compiled.');
            console.error('Please run "npm run build" first to compile TypeScript.');
            process.exit(1);
        }
        
        // 1. 清理整个 resources 目录，确保没有任何多余文件
        console.log('1. Cleaning resources directory...');
        
        // 清理整个 claude-node 目录
        if (fs.existsSync(targetDir)) {
            // 列出将要删除的文件
            const existingFiles = fs.readdirSync(targetDir);
            if (existingFiles.length > 0) {
                console.log('   Removing all existing files:');
                existingFiles.forEach(file => {
                    console.log(`   - ${file}`);
                });
            }
            fs.rmSync(targetDir, { recursive: true, force: true });
            console.log('   ✓ Target directory completely cleaned');
        }
        
        // 重新创建目录
        fs.mkdirSync(targetDir, { recursive: true });
        console.log('   ✓ Target directory recreated');
        
        // 2. 打包主服务文件
        console.log('2. Bundling server with all dependencies...');
        
        // 创建临时入口文件，确保正确的模块导出
        const tempEntryPath = path.join(distDir, '_temp_entry.js');
        const entryContent = `
// 临时入口文件
const server = require('./server.js');

// 确保服务器启动
if (require.main === module) {
    // 服务器已经在 server.js 中启动
}

// 为 Claude SDK 创建 import.meta.url
if (typeof global !== 'undefined' && !global.import) {
    global.import = { meta: { url: 'file://' + __filename } };
}

module.exports = server;
`;
        fs.writeFileSync(tempEntryPath, entryContent);
        
        await esbuild.build({
            entryPoints: [tempEntryPath],
            bundle: true,
            platform: 'node',
            target: 'node18',
            outfile: path.join(targetDir, 'server.bundle.js'),
            external: [
                // Node.js 内置模块不需要打包
                'fs', 'path', 'http', 'https', 'crypto', 'stream', 
                'util', 'events', 'child_process', 'os', 'net',
                'tty', 'url', 'assert', 'buffer', 'process',
                'querystring', 'string_decoder', 'timers', 'zlib',
                // Native 模块无法打包
                'utf-8-validate',
                'bufferutil',
                '@mapbox/node-pre-gyp'
            ],
            minify: false,  // 暂时关闭代码压缩，确保功能正常
            treeShaking: true,  // 移除死代码
            sourcemap: false,
            format: 'cjs',
            logLevel: 'info',
            metafile: true,
            loader: {
                '.node': 'file',
                '.json': 'json'
            },
            define: {
                'process.env.NODE_ENV': '"production"'
            },
            // 额外的优化选项
            legalComments: 'none',  // 移除所有注释
            // 注意：不要移除 console，因为服务需要通过 console.log 输出 socket 路径
            drop: ['debugger'],  // 只移除debugger语句
            dropLabels: ['DEBUG', 'TEST']  // 移除特定标签
        });
        
        // 删除临时文件
        fs.unlinkSync(tempEntryPath);
        console.log('   ✓ Server bundled successfully');
        
        // 3. 创建启动脚本
        console.log('3. Creating start script...');
        const startScript = `#!/usr/bin/env node

// Claude SDK Server Launcher
const path = require('path');
const fs = require('fs');

// 定义错误输出格式
function reportError(stage, error) {
    console.error('ERROR:' + JSON.stringify({
        stage: stage,
        message: error.message,
        stack: error.stack,
        code: error.code || 'UNKNOWN'
    }));
}

// 捕获未处理的异常
process.on('uncaughtException', (error) => {
    reportError('UNCAUGHT_EXCEPTION', error);
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    reportError('UNHANDLED_REJECTION', new Error(String(reason)));
    process.exit(1);
});

// 检查 Node.js 版本
const nodeVersion = process.version;
const majorVersion = parseInt(nodeVersion.slice(1).split('.')[0]);
if (majorVersion < 18) {
    reportError('VERSION_CHECK', new Error('Node.js 18.0.0 or higher required. Current: ' + nodeVersion));
    process.exit(1);
}

// 设置工作目录
try {
    process.chdir(__dirname);
} catch (error) {
    reportError('CHDIR', error);
    process.exit(1);
}

// 启动服务器
try {
    require('./server.bundle.js');
} catch (error) {
    reportError('SERVER_START', error);
    process.exit(1);
}
`;
        
        fs.writeFileSync(path.join(targetDir, 'start.js'), startScript);
        fs.chmodSync(path.join(targetDir, 'start.js'), '755');
        console.log('   ✓ start.js created');
        
        // 4. 完成！不需要其他文件
        
        // 获取文件统计信息
        const bundleStats = fs.statSync(path.join(targetDir, 'server.bundle.js'));
        const startStats = fs.statSync(path.join(targetDir, 'start.js'));
        const totalSize = bundleStats.size + startStats.size;
        
        console.log('\n✅ Build completed successfully!');
        console.log(`📁 Output directory: ${targetDir}`);
        console.log(`📦 Bundle size: ${(bundleStats.size / 1024 / 1024).toFixed(2)} MB`);
        console.log(`📄 Total files: 2 (server.bundle.js, start.js)`);
        console.log(`💾 Total size: ${(totalSize / 1024 / 1024).toFixed(2)} MB`);
        
    } catch (error) {
        console.error('\n❌ Build failed:', error.message);
        console.error(error.stack);
        process.exit(1);
    }
}


// 执行打包
if (require.main === module) {
    bundleWithEsbuild();
}

module.exports = { bundleWithEsbuild };