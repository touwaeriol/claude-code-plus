/**
 * 这是一个演示文件
 * 用于展示 Claude Code Plus 的文件操作工具
 */

export class DemoClass {
    private name: string;
    private version: number;

    constructor(name: string, version: number) {
        this.name = name;
        this.version = version;
    }

    // 获取信息
    getInfo(): string {
        return `${this.name} v${this.version}`;
    }

    // 更新版本
    updateVersion(newVersion: number): void {
        this.version = newVersion;
        console.log(`版本已更新到 ${newVersion}`);
    }

    // 新增：获取版本号
    getVersion(): number {
        return this.version;
    }

    // 新增：重置版本
    resetVersion(): void {
        this.version = 1;
        console.log('版本已重置为 1');
    }
}

// 一些辅助函数
export function formatDate(date: Date): string {
    return date.toISOString();
}

export function calculateSum(numbers: number[]): number {
    return numbers.reduce((sum, num) => sum + num, 0);
}
