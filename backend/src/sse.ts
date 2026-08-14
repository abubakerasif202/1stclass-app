import { Response } from 'express';

interface Client {
  id: string;
  res: Response;
}

class RealtimeBroadcaster {
  private clients: Map<string, Client> = new Map();

  public subscribe(id: string, res: Response): void {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders();

    this.clients.set(id, { id, res });

    // Send initial connected handshake event
    this.sendToClient(res, 'connected', { clientId: id, timestamp: Date.now() });

    res.on('close', () => {
      this.clients.delete(id);
    });
  }

  public broadcast(eventType: string, data: any): void {
    const payload = JSON.stringify(data);
    const message = `event: ${eventType}\ndata: ${payload}\n\n`;

    for (const [id, client] of this.clients.entries()) {
      try {
        client.res.write(message);
      } catch (err) {
        this.clients.delete(id);
      }
    }
  }

  private sendToClient(res: Response, eventType: string, data: any): void {
    const payload = JSON.stringify(data);
    res.write(`event: ${eventType}\ndata: ${payload}\n\n`);
  }

  public getConnectedCount(): number {
    return this.clients.size;
  }
}

export const realtimeBroadcaster = new RealtimeBroadcaster();
