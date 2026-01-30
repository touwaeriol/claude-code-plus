// Bundles and runs `scripts/parity-check.ts` without requiring a root-level Node toolchain.
//
// Usage:
//   node scripts/parity-check.mjs --baseUrl http://127.0.0.1:8765 --token <token> --out parity.json
//
// Notes:
// - Uses esbuild from `vscode-extension/node_modules` (already present in this repo).
// - Adds `nodePaths` so the bundle can resolve deps from `frontend/node_modules` and `vscode-extension/node_modules`.

import * as childProcess from 'child_process'
import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const root = path.resolve(__dirname, '..')
const entry = path.join(root, 'scripts', 'parity-check.ts')
const cacheDir = path.join(root, 'scripts', '.cache')
const outfile = path.join(cacheDir, 'parity-check.cjs')

function resolveEsbuild() {
  // Prefer the esbuild installed for the VS Code extension.
  const p = path.join(root, 'vscode-extension', 'node_modules', 'esbuild')
  return import(p)
}

async function build() {
  fs.mkdirSync(cacheDir, { recursive: true })

  const esbuild = await resolveEsbuild()
  await esbuild.build({
    entryPoints: [entry],
    bundle: true,
    platform: 'node',
    target: 'node18',
    format: 'cjs',
    outfile,
    sourcemap: false,
    logLevel: 'silent',
    // Allow resolving deps from sub-projects without adding a root package.json.
    nodePaths: [
      path.join(root, 'frontend', 'node_modules'),
      path.join(root, 'vscode-extension', 'node_modules'),
    ],
  })
}

async function main() {
  await build()

  const args = process.argv.slice(2)
  const res = childProcess.spawnSync(process.execPath, [outfile, ...args], {
    stdio: 'inherit',
    cwd: root,
  })

  // Preserve exit code for CI.
  process.exitCode = typeof res.status === 'number' ? res.status : 1
}

main().catch((err) => {
  console.error(err instanceof Error ? err.stack || err.message : String(err))
  process.exit(1)
})

