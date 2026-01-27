/**
 * 主题管理器
 * 
 * 监听 VS Code 主题变更并提供主题颜色
 * 
 * 翻译自: jetbrains-plugin/.../theme/ThemeManager.kt
 */

import * as vscode from 'vscode';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[ThemeManager] ${msg}`),
};

/**
 * IDE 主题颜色定义
 */
export interface IdeTheme {
  background: string;
  foreground: string;
  borderColor: string;
  panelBackground: string;
  textFieldBackground: string;
  selectionBackground: string;
  selectionForeground: string;
  linkColor: string;
  errorColor: string;
  warningColor: string;
  successColor: string;
  separatorColor: string;
  hoverBackground: string;
  accentColor: string;
  infoBackground: string;
  codeBackground: string;
  secondaryForeground: string;
}

/**
 * 主题颜色（简化版）
 */
export interface ThemeColors {
  background: string;
  foreground: string;
  panelBackground: string;
  borderColor: string;
  highlightColor: string;
}

export class ThemeManager implements vscode.Disposable {
  private themeChangeListeners: Array<() => void> = [];
  private disposable: vscode.Disposable;

  constructor() {
    // 监听 VS Code 主题变更
    this.disposable = vscode.window.onDidChangeActiveColorTheme(() => {
      this.notifyThemeChanged();
    });
  }

  /**
   * 注册主题变更监听器
   */
  addThemeChangeListener(listener: () => void): void {
    this.themeChangeListeners.push(listener);
  }

  /**
   * 移除主题变更监听器
   */
  removeThemeChangeListener(listener: () => void): void {
    const index = this.themeChangeListeners.indexOf(listener);
    if (index >= 0) {
      this.themeChangeListeners.splice(index, 1);
    }
  }

  /**
   * 通知所有监听器主题已变更
   */
  private notifyThemeChanged(): void {
    log.info('Theme changed');
    this.themeChangeListeners.forEach(listener => listener());
  }

  /**
   * 获取当前主题
   */
  getTheme(): IdeTheme {
    const colorTheme = vscode.window.activeColorTheme;
    const isDark = colorTheme.kind === vscode.ColorThemeKind.Dark ||
                   colorTheme.kind === vscode.ColorThemeKind.HighContrastDark;

    // VS Code 不直接暴露主题颜色，我们使用合理的默认值
    // 基于当前是亮色还是暗色主题
    if (isDark) {
      return this.getDarkTheme();
    } else {
      return this.getLightTheme();
    }
  }

  /**
   * 获取主题颜色（简化版）
   */
  getThemeColors(): ThemeColors {
    const theme = this.getTheme();
    return {
      background: theme.background,
      foreground: theme.foreground,
      panelBackground: theme.panelBackground,
      borderColor: theme.borderColor,
      highlightColor: theme.accentColor,
    };
  }

  /**
   * 检查是否是暗色主题
   */
  isDarkTheme(): boolean {
    const colorTheme = vscode.window.activeColorTheme;
    return colorTheme.kind === vscode.ColorThemeKind.Dark ||
           colorTheme.kind === vscode.ColorThemeKind.HighContrastDark;
  }

  /**
   * 暗色主题默认值
   */
  private getDarkTheme(): IdeTheme {
    return {
      background: '#1e1e1e',
      foreground: '#cccccc',
      borderColor: '#454545',
      panelBackground: '#252526',
      textFieldBackground: '#3c3c3c',
      selectionBackground: '#264f78',
      selectionForeground: '#ffffff',
      linkColor: '#3794ff',
      errorColor: '#f14c4c',
      warningColor: '#cca700',
      successColor: '#89d185',
      separatorColor: '#454545',
      hoverBackground: '#2a2d2e',
      accentColor: '#007acc',
      infoBackground: '#063b49',
      codeBackground: '#2d2d2d',
      secondaryForeground: '#858585',
    };
  }

  /**
   * 亮色主题默认值
   */
  private getLightTheme(): IdeTheme {
    return {
      background: '#ffffff',
      foreground: '#333333',
      borderColor: '#e5e5e5',
      panelBackground: '#f3f3f3',
      textFieldBackground: '#ffffff',
      selectionBackground: '#add6ff',
      selectionForeground: '#000000',
      linkColor: '#006ab1',
      errorColor: '#e51400',
      warningColor: '#bf8803',
      successColor: '#388a34',
      separatorColor: '#e5e5e5',
      hoverBackground: '#e8e8e8',
      accentColor: '#0066b8',
      infoBackground: '#d6ecf2',
      codeBackground: '#f5f5f5',
      secondaryForeground: '#6e6e6e',
    };
  }

  dispose(): void {
    this.disposable.dispose();
    this.themeChangeListeners = [];
  }
}

/**
 * 完整的 VS Code 主题配置
 */
export interface VsCodeThemeConfig {
  isDarkTheme: boolean;
  colors: IdeTheme;
  fonts: ThemeFonts;
  metrics: ThemeMetrics;
}

/**
 * 主题字体配置
 */
export interface ThemeFonts {
  editorFontFamily: string;
  editorFontSize: number;
  editorLineSpacing: number;
  uiFontFamily: string;
  uiFontSize: number;
  consoleFontFamily: string;
  consoleFontSize: number;
  useLigatures: boolean;
  useAntialiasing: boolean;
}

/**
 * 主题度量配置
 */
export interface ThemeMetrics {
  defaultSpacing: number;
  compactSpacing: number;
  largeSpacing: number;
  borderRadius: number;
  borderWidth: number;
  scrollbarWidth: number;
  toolbarHeight: number;
  tabHeight: number;
}

/**
 * VS Code 主题集成
 * 获取完整的主题配置
 */
export class VsCodeThemeIntegration {
  /**
   * 获取当前 VS Code 主题配置
   */
  static getCurrentThemeConfig(): VsCodeThemeConfig {
    const themeManager = new ThemeManager();
    const isDark = themeManager.isDarkTheme();
    const colors = themeManager.getTheme();

    return {
      isDarkTheme: isDark,
      colors,
      fonts: this.getThemeFonts(),
      metrics: this.getThemeMetrics(),
    };
  }

  /**
   * 获取主题字体配置
   */
  private static getThemeFonts(): ThemeFonts {
    const config = vscode.workspace.getConfiguration('editor');
    const terminalConfig = vscode.workspace.getConfiguration('terminal.integrated');

    return {
      editorFontFamily: config.get<string>('fontFamily') || 'Consolas, monospace',
      editorFontSize: config.get<number>('fontSize') || 14,
      editorLineSpacing: config.get<number>('lineHeight') || 1.5,
      uiFontFamily: 'system-ui, -apple-system, sans-serif',
      uiFontSize: 13,
      consoleFontFamily: terminalConfig.get<string>('fontFamily') || 'monospace',
      consoleFontSize: terminalConfig.get<number>('fontSize') || 14,
      useLigatures: config.get<boolean>('fontLigatures') || false,
      useAntialiasing: true,
    };
  }

  /**
   * 获取主题度量配置
   */
  private static getThemeMetrics(): ThemeMetrics {
    return {
      defaultSpacing: 8,
      compactSpacing: 4,
      largeSpacing: 16,
      borderRadius: 4,
      borderWidth: 1,
      scrollbarWidth: 12,
      toolbarHeight: 30,
      tabHeight: 36,
    };
  }

  /**
   * 注册主题变化监听器
   */
  static registerThemeChangeListener(onChange: (config: VsCodeThemeConfig) => void): vscode.Disposable {
    return vscode.window.onDidChangeActiveColorTheme(() => {
      const newConfig = this.getCurrentThemeConfig();
      log.info('VS Code theme changed');
      onChange(newConfig);
    });
  }
}
