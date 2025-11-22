// 这是一个演示文件
// 用于展示 Claude Code Plus 的工具功能

export interface User {
    id: number;
    name: string;
    email: string;
}

export class UserService {
    private users: User[] = [];

    // 添加用户
    // @param user - 要添加的用户对象
    // @returns 添加后的用户ID
    // @throws Error 如果用户ID已存在
    addUser(user: User): number {
        // 验证用户ID是否已存在
        if (this.users.some(u => u.id === user.id)) {
            throw new Error(`用户ID ${user.id} 已存在`);
        }

        // 验证必填字段
        if (!user.name || !user.email) {
            throw new Error('用户名称和邮箱不能为空');
        }

        this.users.push(user);
        console.log(`用户 ${user.name} 已添加`);
        return user.id;
    }

    // 获取所有用户
    // @returns 所有用户的数组副本
    getAllUsers(): User[] {
        console.log(`当前共有 ${this.users.length} 个用户`);
        return [...this.users]; // 返回副本，避免外部修改
    }

    // 根据ID查找用户
    // @param id - 用户ID
    // @returns 找到的用户对象，如果不存在则返回 undefined
    findUserById(id: number): User | undefined {
        const user = this.users.find(u => u.id === id);
        if (user) {
            console.log(`找到用户: ${user.name}`);
        } else {
            console.warn(`未找到ID为 ${id} 的用户`);
        }
        return user;
    }

    // 删除用户
    // @param id - 要删除的用户ID
    // @returns 是否删除成功
    deleteUser(id: number): boolean {
        const index = this.users.findIndex(u => u.id === id);
        if (index !== -1) {
            const deletedUser = this.users.splice(index, 1)[0];
            console.log(`用户 ${deletedUser.name} 已删除`);
            return true;
        }
        console.warn(`删除失败: 未找到ID为 ${id} 的用户`);
        return false;
    }
}
