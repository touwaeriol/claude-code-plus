#!/usr/bin/env node
/**
 * Copy enhanced Claude CLI from claude-agent-sdk to vscode-extension resources
 * 
 * This script copies the patched CLI that includes control commands like:
 * - run_to_background (007)
 * - mcp_reconnect/disable/enable (004)
 * - get_capabilities (008)
 * - etc.
 */

const fs = require('fs')
const path = require('path')

const sourceFile = path.resolve(__dirname, '../../claude-agent-sdk/src/main/resources/bundled/claude-cli-2.1.17-enhanced.mjs')
const targetDir = path.resolve(__dirname, '../resources/bundled')
const targetFile = path.join(targetDir, 'claude-cli-enhanced.mjs')

// Ensure target directory exists
if (!fs.existsSync(targetDir)) {
  fs.mkdirSync(targetDir, { recursive: true })
  console.log(`Created directory: ${targetDir}`)
}

// Check source file exists
if (!fs.existsSync(sourceFile)) {
  console.error(`Source file not found: ${sourceFile}`)
  console.error('Please ensure claude-agent-sdk has the enhanced CLI built.')
  process.exit(1)
}

// Copy file
try {
  fs.copyFileSync(sourceFile, targetFile)
  const stats = fs.statSync(targetFile)
  console.log(`✅ Copied enhanced CLI (${(stats.size / 1024 / 1024).toFixed(2)} MB)`)
  console.log(`   From: ${sourceFile}`)
  console.log(`   To:   ${targetFile}`)
} catch (err) {
  console.error('Failed to copy file:', err.message)
  process.exit(1)
}

// Also copy the patches directory for reference/debugging
const patchesSource = path.resolve(__dirname, '../../claude-agent-sdk/cli-patches/patches')
const patchesTarget = path.resolve(__dirname, '../resources/cli-patches')

if (fs.existsSync(patchesSource)) {
  if (!fs.existsSync(patchesTarget)) {
    fs.mkdirSync(patchesTarget, { recursive: true })
  }
  
  const patchFiles = fs.readdirSync(patchesSource).filter(f => f.endsWith('.js'))
  for (const file of patchFiles) {
    fs.copyFileSync(
      path.join(patchesSource, file),
      path.join(patchesTarget, file)
    )
  }
  console.log(`✅ Copied ${patchFiles.length} patch files for reference`)
}

console.log('\n📌 Next steps:')
console.log('1. The enhanced CLI is now available at: resources/bundled/claude-cli-enhanced.mjs')
console.log('2. Use `node resources/bundled/claude-cli-enhanced.mjs` to run it')
console.log('3. Or update claudeCli.ts to use this path instead of system `claude`')
