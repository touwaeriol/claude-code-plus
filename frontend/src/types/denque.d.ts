declare module 'denque' {
  export default class Denque<T> {
    constructor(items?: T[])
    push(item: T): number
    unshift(item: T): number
    pop(): T | undefined
    shift(): T | undefined
    toArray(): T[]
    readonly length: number
  }
}
