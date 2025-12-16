import { defineConfig, Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { copyFileSync, existsSync } from 'fs'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import viteCompression from 'vite-plugin-compression'

// 生产模式：只复制必需的 public 文件（排除测试文件）
function copyEssentialPublicFiles(): Plugin {
  return {
    name: 'copy-essential-public-files',
    closeBundle() {
      const publicFiles = ['favicon.svg'] // 只复制这些文件
      publicFiles.forEach(file => {
        const src = resolve(__dirname, 'public', file)
        const dest = resolve(__dirname, 'dist', file)
        if (existsSync(src)) {
          copyFileSync(src, dest)
        }
      })
    }
  }
}

// JCEF 兼容性：将 script 标签移动到 body 底部，保持 type="module"
function jcefCompatibility(): Plugin {
  return {
    name: 'jcef-compatibility',
    transformIndexHtml(html) {
      const scriptMatch = html.match(/<script[^>]*src="[^"]*"[^>]*><\/script>/g)
      if (scriptMatch) {
        html = html.replace(/<script[^>]*src="[^"]*"[^>]*><\/script>/g, '')
        html = html.replace('</body>', `  ${scriptMatch.join('\n  ')}\n</body>`)
      }
      return html
    }
  }
}

export default defineConfig(({ mode }) => {
  const isProduction = mode === 'production'

  return {
  plugins: [
    vue(),
    jcefCompatibility(),
    // 自动导入（Element Plus 依赖）
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'components.d.ts',
      directoryAsNamespace: false
    }),
    // 生产模式启用 gzip 压缩 + 复制必需文件
    ...(isProduction ? [
      copyEssentialPublicFiles(),
      viteCompression({
        algorithm: 'gzip',
        ext: '.gz',
        threshold: 1024, // 大于 1KB 的文件才压缩
        deleteOriginFile: false
      }),
      viteCompression({
        algorithm: 'brotliCompress',
        ext: '.br',
        threshold: 1024,
        deleteOriginFile: false
      })
    ] : [])
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    host: 'localhost',
    port: 5174,
    strictPort: false,
    cors: true,
    hmr: {
      host: 'localhost',
      port: 5174
    },
    proxy: {
      '/ws': {
        target: 'ws://127.0.0.1:8765',
        ws: true,
        changeOrigin: true
      },
      '/api': {
        target: 'http://127.0.0.1:8765',
        changeOrigin: true
      },
      '/events': {
        target: 'http://127.0.0.1:8765',
        changeOrigin: true
      }
    }
  },
  // 生产模式排除测试文件
  publicDir: isProduction ? false : 'public',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // 生产模式手动复制需要的 public 文件
    copyPublicDir: !isProduction,
    // JCEF 兼容设置
    target: 'es2020',
    cssTarget: 'chrome80',
    // 生产模式使用 terser（压缩率更高），开发模式不压缩
    minify: isProduction ? 'terser' : false,
    terserOptions: isProduction ? {
      compress: {
        drop_console: false,  // ✅ 保留 console（用于生产环境调试）
        drop_debugger: true,  // 移除 debugger
        pure_funcs: []        // ✅ 不移除任何 console 函数
      },
      mangle: true,
      format: {
        comments: false // 移除注释
      }
    } : undefined,
    // 开发模式不生成 sourcemap
    sourcemap: !isProduction,
    rollupOptions: {
      output: {
        format: 'es',
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
        manualChunks(id) {
          // Vue 核心 + Element Plus（合并避免循环依赖导致 TDZ 错误）
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia') || id.includes('node_modules/@vue') ||
              id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus')) {
            return 'vue-vendor'
          }
          // Shiki 语法高亮
          if (id.includes('node_modules/shiki')) {
            return 'shiki'
          }
          // RSocket 通信
          if (id.includes('node_modules/rsocket') || id.includes('node_modules/@rsocket')) {
            return 'rsocket'
          }
          // Protobuf
          if (id.includes('node_modules/protobufjs') || id.includes('node_modules/@protobufjs')) {
            return 'protobuf'
          }
          // Markdown 相关
          if (id.includes('node_modules/markdown-it') || id.includes('node_modules/@mdit')) {
            return 'markdown'
          }
          // Shiki 语言定义（让 Vite 自动按需分包，不合并到 vendor）
          if (id.includes('node_modules/@shikijs') || id.includes('shiki/dist/langs')) {
            return // 返回 undefined，让 Vite 自动处理
          }
          // 其他小型依赖
          if (id.includes('node_modules')) {
            // 不合并，让 Vite 自动处理，避免 vendor 过大
            return
          }
        }
      }
    },
    // 放宽体积告警阈值，结合按需拆分大依赖
    chunkSizeWarningLimit: 1200
  },
  base: './'
}})
