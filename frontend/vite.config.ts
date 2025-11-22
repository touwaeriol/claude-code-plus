import { defineConfig, Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 自定义插件：JCEF 兼容性修复
function jcefCompatibility(): Plugin {
  return {
    name: 'jcef-compatibility',
    transformIndexHtml(html) {
      // 1. 保留 type="module"（ES 格式需要）
      // JCEF 的 Chromium 支持 ES6 模块
      
      // 2. 移动 script 标签到 body 底部
      const scriptMatch = html.match(/<script[^>]*src="[^"]*"[^>]*><\/script>/g)
      if (scriptMatch) {
        // 从 head 中移除 script
        html = html.replace(/<script[^>]*src="[^"]*"[^>]*><\/script>/g, '')
        // 在 </body> 前插入 script
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
    // ✅ 启用自动导入（Element Plus 组件注册必需）
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'components.d.ts',
      directoryAsNamespace: false,
    }),
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
    // 🔧 针对 JCEF 的兼容性配置
    target: 'es2020', // JCEF 的 Chromium 支持 ES2020
    cssTarget: 'chrome80', // JCEF 基于 Chromium
    minify: 'esbuild', // 使用 esbuild 压缩
    rollupOptions: {
      output: {
        // ✅ ES 模块格式（JCEF 支持）
        format: 'es',
        // 简化文件名
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
        // 启用代码分割以减小单文件大小
        manualChunks: {
          'vue-vendor': ['vue', 'pinia'],
          'element-plus': ['element-plus']
        }
      }
    }
  },
  base: './'
})
