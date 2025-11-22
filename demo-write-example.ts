// Write 工具演示文件
// 创建时间: 2025-11-23

interface User {
  id: number;
  name: string;
  email: string;
  createdAt: Date;  // MultiEdit 演示：添加时间戳
}

class UserManager {
  private users: User[] = [];

  addUser(user: User): void {
    // Edit 工具演示：添加验证逻辑
    if (this.users.some(u => u.id === user.id)) {
      throw new Error(`用户 ID ${user.id} 已存在`);
    }
    // MultiEdit 演示：自动设置创建时间
    user.createdAt = new Date();
    this.users.push(user);
    console.log(`用户 ${user.name} 已添加`);
  }

  getUser(id: number): User | undefined {
    return this.users.find(u => u.id === id);
  }

  getAllUsers(): User[] {
    return [...this.users];
  }

  // MultiEdit 演示：添加删除用户方法
  removeUser(id: number): boolean {
    const index = this.users.findIndex(u => u.id === id);
    if (index !== -1) {
      this.users.splice(index, 1);
      return true;
    }
    return false;
  }
}

export { User, UserManager };
