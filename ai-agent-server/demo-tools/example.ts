// 这是一个示例 TypeScript 文件
// 用于演示各种文件操作工具

export class UserService {
    private users: Map<string, User> = new Map();

    constructor() {
        console.log('UserService 已初始化');
    }

    // 添加用户（带验证）
    addUser(user: User): void {
        if (!user.id || !user.name || !user.email) {
            throw new Error('用户信息不完整');
        }

        if (this.users.has(user.id)) {
            throw new Error(`用户 ID ${user.id} 已存在`);
        }

        this.users.set(user.id, user);
        console.log(`用户 ${user.name} 已成功添加`);
    }

    // 获取用户
    getUser(id: string): User | undefined {
        return this.users.get(id);
    }

    // 删除用户
    deleteUser(id: string): boolean {
        return this.users.delete(id);
    }
}

interface User {
    id: string;
    name: string;
    email: string;
}
