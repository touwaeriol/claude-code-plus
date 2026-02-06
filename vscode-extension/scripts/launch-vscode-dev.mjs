import { spawn } from 'node:child_process'
import path from 'node:path'

function resolvePath(...parts) {
  return path.resolve(process.cwd(), ...parts)
}

function run(cmd, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, {
      stdio: 'inherit',
      shell: true,
      ...options,
    })
    child.on('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`${cmd} exited with code ${code}`))
    })
  })
}

async function main() {
  const repoRoot = resolvePath('..')
  const frontendDir = path.join(repoRoot, 'frontend')

  await run('npm', ['run', 'build'], { cwd: frontendDir })
  await run('node', [resolvePath('scripts', 'sync-frontend.mjs')])
  await run('npm', ['run', 'compile'], { cwd: process.cwd() })

  const extensionDevPath = process.cwd()
  const workspacePath = repoRoot

  // Keep logs/extension state isolated so we can reliably find ports.
  const userDataDir = resolvePath('.vscode-dev', 'user-data-test')
  const extensionsDir = resolvePath('.vscode-dev', 'extensions-test')

  // 启动 Extension Development Host（独立进程，不等待退出）
  spawn(
    'code',
    [
      '--new-window',
      `--user-data-dir=${userDataDir}`,
      `--extensions-dir=${extensionsDir}`,
      '--extensionDevelopmentPath',
      extensionDevPath,
      workspacePath,
    ],
    {
    stdio: 'inherit',
    shell: true,
    detached: true,
    }
  )
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error(err)
  process.exit(1)
})
