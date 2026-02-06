import { cpSync, existsSync, rmSync, statSync } from 'node:fs'
import path from 'node:path'

function resolvePath(...parts) {
  return path.resolve(process.cwd(), ...parts)
}

function main() {
  const repoRoot = resolvePath('..')
  const frontendDist = path.join(repoRoot, 'frontend', 'dist')
  const targetDist = resolvePath('media', 'dist')

  if (!existsSync(frontendDist)) {
    throw new Error(`frontend dist not found: ${frontendDist}. Please run: (cd ../frontend && npm run build)`)
  }
  if (!statSync(frontendDist).isDirectory()) {
    throw new Error(`frontend dist is not a directory: ${frontendDist}`)
  }

  rmSync(targetDist, { recursive: true, force: true })
  cpSync(frontendDist, targetDist, { recursive: true })

  // eslint-disable-next-line no-console
  console.log(`[sync-frontend] copied ${frontendDist} -> ${targetDist}`)
}

main()

