#!/usr/bin/env node
// Claude SDK Server Startup Script
// This script ensures the server runs properly within the IntelliJ plugin environment

const path = require('path');
const { spawn } = require('child_process');

// 确保工作目录正确
process.chdir(__dirname);

// 检查是否已安装依赖
const fs = require('fs');
if (!fs.existsSync('node_modules')) {
    console.error('Dependencies not installed. Please run npm install first.');
    process.exit(1);
}

// 启动服务器
require('./server.js');
