import { spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const repoRoot = path.resolve(__dirname, '..', '..')
const extensionDir = path.join(repoRoot, 'vscode-extension')

const argv = process.argv.slice(2)
const getArg = (name) => {
  const idx = argv.indexOf(name)
  if (idx < 0) return undefined
  return argv[idx + 1]
}

const workspacePath = getArg('--workspace') ?? repoRoot
const skipFrontendBuild = argv.includes('--skip-frontend-build')
const skipSyncFrontend = argv.includes('--skip-sync-frontend')
const skipCompile = argv.includes('--skip-compile')

function run(cmd, cmdArgs, opts) {
  const isWin = process.platform === 'win32'
  const cmdLine = [cmd, ...cmdArgs]
    .map((s) => {
      const v = String(s)
      return /[\s"]/g.test(v) ? `"${v.replaceAll('"', '\\"')}"` : v
    })
    .join(' ')
  const result = isWin
    ? spawnSync(process.env.ComSpec ?? 'cmd.exe', ['/d', '/s', '/c', cmdLine], { stdio: 'inherit', ...opts })
    : spawnSync(cmd, cmdArgs, { stdio: 'inherit', ...opts })
  if (result.error) {
    throw new Error(`Command failed to start: ${cmd} ${cmdArgs.join(' ')} (${result.error.message})`)
  }
  if (result.status !== 0) {
    throw new Error(`Command failed (${result.status}): ${cmd} ${cmdArgs.join(' ')}`)
  }
}

async function main() {
  if (!fs.existsSync(extensionDir)) {
    throw new Error(`extension directory not found: ${extensionDir}`)
  }

  if (!skipSyncFrontend) {
    const args = ['node', './scripts/sync-frontend.mjs']
    if (skipFrontendBuild) args.push('--skip-build')
    run(args[0], args.slice(1), { cwd: extensionDir })
  }

  if (!skipCompile) {
    run('npm', ['run', 'compile'], { cwd: extensionDir })
  }

  const devRoot = path.join(extensionDir, '.vscode-dev')
  const userDataDir = path.join(devRoot, 'user-data')
  const extensionsDir = path.join(devRoot, 'extensions')

  await fsp.mkdir(userDataDir, { recursive: true })
  await fsp.mkdir(extensionsDir, { recursive: true })

  const codeCmd = process.platform === 'win32' ? 'code.cmd' : 'code'
  const args = [
    '--new-window',
    `--user-data-dir=${userDataDir}`,
    `--extensions-dir=${extensionsDir}`,
    `--extensionDevelopmentPath=${extensionDir}`,
    workspacePath,
  ]

  const isWin = process.platform === 'win32'
  const child = isWin
    ? spawn(
        process.env.ComSpec ?? 'cmd.exe',
        [
          '/d',
          '/s',
          '/c',
          [codeCmd, ...args]
            .map((s) => {
              const v = String(s)
              return /[\s"]/g.test(v) ? `"${v.replaceAll('"', '\\"')}"` : v
            })
            .join(' '),
        ],
        { stdio: 'ignore', detached: true }
      )
    : spawn(codeCmd, args, { stdio: 'ignore', detached: true })
  child.unref()

  console.log('[launch-vscode-dev] VS Code started')
  console.log(`- extensionDevelopmentPath: ${extensionDir}`)
  console.log(`- workspace: ${workspacePath}`)
  console.log(`- userDataDir: ${userDataDir}`)
  console.log(`- extensionsDir: ${extensionsDir}`)
}

await main()
