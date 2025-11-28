import js from '@eslint/js'
import typescript from '@typescript-eslint/eslint-plugin'
import tsParser from '@typescript-eslint/parser'
import vue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import globals from 'globals'

export default [
  // 基础推荐规则
  js.configs.recommended,

  // TypeScript 文件
  {
    files: ['**/*.ts', '**/*.tsx', '**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tsParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    plugins: {
      '@typescript-eslint': typescript,
      vue,
    },
    rules: {
      // Vue 规则
      ...vue.configs['vue3-recommended'].rules,
      'vue/multi-word-component-names': 'off', // 允许单词组件名
      'vue/no-v-html': 'off', // Markdown 渲染必须用 v-html
      'vue/require-default-prop': 'off', // 不强制默认值
      'vue/no-setup-props-destructure': 'off', // 允许 props 解构
      'vue/one-component-per-file': 'off', // 允许子组件在同一文件
      'vue/no-template-shadow': 'off', // 允许合理的变量名重用

      // TypeScript 规则
      ...typescript.configs.recommended.rules,
      '@typescript-eslint/no-explicit-any': 'off', // Bridge 接口需要 any
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        destructuredArrayIgnorePattern: '^_'
      }],
      '@typescript-eslint/no-non-null-assertion': 'off', // 允许 ! 断言

      // 通用规则
      'no-console': 'off', // 开发时允许 console
      'no-debugger': 'warn', // debugger 警告
      'prefer-const': 'warn',
      'no-unused-vars': 'off' // 使用 TS 版本的规则
    },
  },

  // 忽略文件
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      '*.config.js',
      'coverage/**',
    ],
  },
]
