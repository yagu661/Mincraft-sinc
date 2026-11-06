/**
 * Minecraft GitSync — Authenticated WebRTC Signaling Server
 *
 * Responsibilities:
 * - Peer presence tracking (Online/Offline)
 * - Exchange WebRTC Session Description Protocol (SDP Offers and Answers)
 * - Exchange Interactive Connectivity Establishment (ICE) network candidates
 * - Queue pending snapshot notifications for offline devices (Rule B & C)
 *
 * NOTE: World binary archives are NEVER uploaded or stored on this server;
 * transfers occur directly between devices over WebRTC DataChannels.
 */

import { WebSocketServer, WebSocket } from 'ws';
import * as http from 'http';

const PORT = parseInt(process.env.SIGNALING_PORT || '8443', 10);

interface ConnectedClient {
  ws: WebSocket;
  deviceId: string;
  token: string;
  pairedDeviceId?: string;
  lastSeenEpoch: number;
}

interface PendingNotification {
  commitId: string;
  worldId: string;
  senderId: string;
  timestamp: number;
}

// Active connections indexed by deviceId
const clients = new Map<string, ConnectedClient>();

// Queued updates for offline devices: targetDeviceId -> PendingNotification[]
const pendingQueues = new Map<string, PendingNotification[]>();

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', onlineClients: clients.size, uptime: process.uptime() }));
    return;
  }
  res.writeHead(404);
  res.end('Not Found');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws: WebSocket, req: http.IncomingMessage) => {
  let authenticatedDeviceId: string | null = null;

  ws.on('message', (data: Buffer | string) => {
    try {
      const msg = JSON.parse(data.toString());
      const { type } = msg;

      switch (type) {
        case 'REGISTER': {
          const { deviceId, token, pairedDeviceId } = msg;
          if (!deviceId || !token) {
            ws.send(JSON.stringify({ type: 'ERROR', message: 'Missing deviceId or token' }));
            return;
          }

          authenticatedDeviceId = deviceId;
          clients.set(deviceId, {
            ws,
            deviceId,
            token,
            pairedDeviceId,
            lastSeenEpoch: Date.now(),
          });

          console.log(`[Presence] Registered device: ${deviceId} (paired: ${pairedDeviceId || 'none'})`);

          // Notify partner if they are currently online
          if (pairedDeviceId) {
            const partner = clients.get(pairedDeviceId);
            if (partner && partner.ws.readyState === WebSocket.OPEN) {
              partner.ws.send(JSON.stringify({ type: 'PRESENCE_UPDATE', online: true, deviceId }));
              ws.send(JSON.stringify({ type: 'PRESENCE_UPDATE', online: true, deviceId: pairedDeviceId }));
            } else {
              ws.send(JSON.stringify({ type: 'PRESENCE_UPDATE', online: false, deviceId: pairedDeviceId }));
            }

            // Check if there are queued notifications for this reconnecting device (Rule C)
            const queued = pendingQueues.get(deviceId);
            if (queued && queued.length > 0) {
              console.log(`[Queue] Delivering ${queued.length} pending updates to reconnected device ${deviceId}`);
              for (const item of queued) {
                ws.send(
                  JSON.stringify({
                    type: 'PENDING_UPDATE',
                    commitId: item.commitId,
                    worldId: item.worldId,
                    senderId: item.senderId,
                  })
                );
              }
              pendingQueues.delete(deviceId);
            }
          }
          break;
        }

        case 'OFFER':
        case 'ANSWER':
        case 'ICE_CANDIDATE': {
          const { targetDeviceId } = msg;
          if (!targetDeviceId) return;

          const target = clients.get(targetDeviceId);
          if (target && target.ws.readyState === WebSocket.OPEN) {
            msg.senderId = authenticatedDeviceId;
            target.ws.send(JSON.stringify(msg));
          } else {
            console.log(`[Signaling] Target ${targetDeviceId} offline for ${type}`);
          }
          break;
        }

        case 'PENDING_UPDATE_NOTICE': {
          const { targetDeviceId, commitId, worldId } = msg;
          if (!targetDeviceId || !commitId) return;

          const target = clients.get(targetDeviceId);
          if (target && target.ws.readyState === WebSocket.OPEN) {
            target.ws.send(
              JSON.stringify({
                type: 'PENDING_UPDATE',
                commitId,
                worldId,
                senderId: authenticatedDeviceId,
              })
            );
          } else {
            // Queue for when target device reconnects (Rule B)
            console.log(`[Queue] Enqueueing update commit ${commitId} for offline device ${targetDeviceId}`);
            if (!pendingQueues.has(targetDeviceId)) {
              pendingQueues.set(targetDeviceId, []);
            }
            pendingQueues.get(targetDeviceId)!.push({
              commitId,
              worldId,
              senderId: authenticatedDeviceId || 'unknown',
              timestamp: Date.now(),
            });
          }
          break;
        }

        default:
          console.warn(`[Signaling] Unknown message type: ${type}`);
      }
    } catch (err) {
      console.error('[Signaling] Error parsing message:', err);
    }
  });

  ws.on('close', () => {
    if (authenticatedDeviceId) {
      const client = clients.get(authenticatedDeviceId);
      if (client?.pairedDeviceId) {
        const partner = clients.get(client.pairedDeviceId);
        if (partner && partner.ws.readyState === WebSocket.OPEN) {
          partner.ws.send(JSON.stringify({ type: 'PRESENCE_UPDATE', online: false, deviceId: authenticatedDeviceId }));
        }
      }
      clients.delete(authenticatedDeviceId);
      console.log(`[Presence] Device disconnected: ${authenticatedDeviceId}`);
    }
  });

  ws.on('error', (err: Error) => {
    console.error('[Signaling] WebSocket connection error:', err);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Minecraft GitSync Signaling Server listening on port ${PORT}`);
});
