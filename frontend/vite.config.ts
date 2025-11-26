import { defineConfig, Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

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

export default defineConfig({
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
    })
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
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    // JCEF 兼容设置
    target: 'es2020',
    cssTarget: 'chrome80',
    minify: 'esbuild',
    rollupOptions: {
      output: {
        format: 'es',
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
        manualChunks: {
          'vue-vendor': ['vue', 'pinia'],
          'element-plus': ['element-plus'],
          shiki: ['shiki']
        }
      }
    },
    // 放宽体积告警阈值，结合按需拆分大依赖
    chunkSizeWarningLimit: 1200
  },
  base: './'
})
