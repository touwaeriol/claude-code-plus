import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const repoRoot = path.resolve(__dirname, '..', '..')
const frontendDir = path.join(repoRoot, 'frontend')
const frontendDistDir = path.join(frontendDir, 'dist')
const targetDir = path.join(repoRoot, 'vscode-extension', 'media', 'dist')

const args = process.argv.slice(2)
const skipBuild = args.includes('--skip-build')

function run(cmd, cmdArgs, opts) {
  const isWin = process.platform === 'win32'
  const cmdLine = [cmd, ...cmdArgs]
    .map((s) => {
      const v = String(s)
      return /[\s"]/g.test(v) ? `"${v.replaceAll('"', '\\"')}"` : v
    })
    .join(' ')
  const result = isWin
    ? spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', cmdLine], {
        stdio: 'inherit',
        ...opts,
      })
    : spawnSync(cmd, cmdArgs, { stdio: 'inherit', ...opts })
  if (result.error) {
    throw new Error(`Command failed to start: ${cmd} ${cmdArgs.join(' ')} (${result.error.message})`)
  }
  if (result.status !== 0) {
    throw new Error(`Command failed (${result.status}): ${cmd} ${cmdArgs.join(' ')}`)
  }
}

async function rmExcept(dir, keepNames) {
  const keep = new Set(keepNames)
  await fsp.mkdir(dir, { recursive: true })
  const entries = await fsp.readdir(dir, { withFileTypes: true })
  await Promise.all(
    entries.map(async (ent) => {
      if (keep.has(ent.name)) return
      const full = path.join(dir, ent.name)
      await fsp.rm(full, { recursive: true, force: true })
    })
  )
}

async function copyDir(src, dest) {
  await fsp.mkdir(dest, { recursive: true })
  const entries = await fsp.readdir(src, { withFileTypes: true })
  for (const ent of entries) {
    const srcPath = path.join(src, ent.name)
    const destPath = path.join(dest, ent.name)
    if (ent.isDirectory()) {
      await copyDir(srcPath, destPath)
      continue
    }
    if (ent.isFile()) {
      await fsp.copyFile(srcPath, destPath)
      continue
    }
    // Skip symlinks/other types.
  }
}

async function main() {
  if (!fs.existsSync(frontendDir)) {
    throw new Error(`frontend directory not found: ${frontendDir}`)
  }

  if (!skipBuild) {
    run('npm', ['run', 'build:dev'], { cwd: frontendDir })
  }

  if (!fs.existsSync(frontendDistDir)) {
    throw new Error(`frontend dist not found: ${frontendDistDir} (run frontend build first)`)
  }

  await rmExcept(targetDir, ['.gitkeep'])
  await copyDir(frontendDistDir, targetDir)

  console.log(`[sync-frontend] OK: ${frontendDistDir} -> ${targetDir}`)
}

await main()
