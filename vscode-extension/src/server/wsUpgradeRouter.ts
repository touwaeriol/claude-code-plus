import type * as http from 'http'
import type * as net from 'net'

type WsServerLike = {
  handleUpgrade: (req: http.IncomingMessage, socket: net.Socket, head: Buffer, cb: (ws: any, req: http.IncomingMessage) => void) => void
  emit: (event: 'connection', ws: any, req: http.IncomingMessage) => void
}

export class WsUpgradeRouter {
  private readonly handlers = new Map<string, WsServerLike>()
  private readonly upgradeListener: (req: http.IncomingMessage, socket: net.Socket, head: Buffer) => void

  constructor(
    private readonly httpServer: http.Server,
    private readonly log?: (message: string) => void
  ) {
    this.upgradeListener = (req, socket, head) => this.handleUpgrade(req, socket, head)
    this.httpServer.on('upgrade', this.upgradeListener)
  }

  register(pathname: string, wss: WsServerLike): void {
    this.handlers.set(pathname, wss)
  }

  dispose(): void {
    this.httpServer.off('upgrade', this.upgradeListener)
    this.handlers.clear()
  }

  private handleUpgrade(req: http.IncomingMessage, socket: net.Socket, head: Buffer): void {
    try {
      const url = new URL(String(req.url ?? ''), 'http://127.0.0.1')
      const pathname = url.pathname
      const wss = this.handlers.get(pathname)

      if (!wss) {
        // Unknown websocket endpoint; close silently.
        socket.destroy()
        return
      }

      wss.handleUpgrade(req, socket, head, (ws: any) => {
        try {
          // Mark the underlying net.Socket so HTTP-layer error handlers don't write into the ws stream.
          if (ws?._socket) (ws._socket as any).__ccp_isWebSocket = true
        } catch {
          // ignore
        }

        wss.emit('connection', ws, req)
      })
    } catch (err) {
      this.log?.(`[WsUpgradeRouter] handleUpgrade error: ${(err instanceof Error && (err.stack || err.message)) || String(err)}`)
      try {
        socket.destroy()
      } catch {
        // ignore
      }
    }
  }
}

