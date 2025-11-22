// Demo TypeScript 文件
// 这是一个简单的演示文件

interface User {
  id: number;
  name: string;
  email: string;
}

class UserService {
  private users: User[] = [];

  addUser(user: User): void {
    this.users.push(user);
    console.log(`用户 ${user.name} 已添加`);
  }

  getUser(id: number): User | undefined {
    return this.users.find(user => user.id === id);
  }

  getAllUsers(): User[] {
    return this.users;
  }
}

// 使用示例
const userService = new UserService();

userService.addUser({
  id: 1,
  name: "张三",
  email: "zhangsan@example.com"
});

userService.addUser({
  id: 2,
  name: "李四",
  email: "lisi@example.com"
});

console.log("所有用户:", userService.getAllUsers());
