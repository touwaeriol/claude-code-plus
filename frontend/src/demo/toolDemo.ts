/**
 * 工具演示文件
 * 此文件用于演示 Claude Code 的各种工具功能
 */

export interface User {
    id: number
    name: string
    emailAddress: string
}

export function greetUser(user: User): string {
    return `Hello, ${user.name}!`
}

export function formatEmail(emailAddress: string): string {
    return emailAddress.toLowerCase().trim()
}

export function validateEmail(emailAddress: string): boolean {
    const emailAddressRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return emailAddressRegex.test(emailAddress)
}

export const DEFAULT_USER: User = {
    id: 0,
    name: 'Guest',
    emailAddress: 'guest@example.com'
}
