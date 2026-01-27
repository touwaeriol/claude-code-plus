declare module 'ws' {
  export class Server {
    constructor(options?: any)
    on(event: string, listener: (...args: any[]) => void): this
    close(callback?: (...args: any[]) => void): void
  }

  export { Server as WebSocketServer }
}

