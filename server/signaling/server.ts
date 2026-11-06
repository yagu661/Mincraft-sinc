/**
 * Minecraft GitSync — Authenticated WebRTC Signaling Server
 *
 * Responsibilities:
 * - Peer presence tracking (Online/Offline) across 3-device sync groups (Device A, Device B, Device C)
 * - Exchange WebRTC Session Description Protocol (SDP Offers and Answers)
 * - Exchange Interactive Connectivity Establishment (ICE) network candidates
 * - Queue pending snapshot notifications for offline devices (Rule B & C)
 * - Coordinate Single Active Player Editing Leases (Lease Acquisition, Heartbeat, Release)
 *
 * NOTE: World binary archives are NEVER uploaded or stored on this server;
 * transfers occur directly phone-to-phone over encrypted WebRTC DataChannels.
 */

import { WebSocketServer, WebSocket } from 'ws';
import * as http from 'http';

const PORT = parseInt(process.env.SIGNALING_PORT || '8443', 10);

interface ConnectedClient {
  ws: WebSocket;
  deviceId: string;
  token: string;
  pairedDeviceId?: string;
  groupId?: string;
  lastSeenEpoch: number;
}

interface PendingNotification {
  commitId: string;
  worldId: string;
  senderId: string;
  timestamp: number;
}

interface ActiveWorldLease {
  groupId: string;
  worldId: string;
  holderDeviceId: string;
  holderDisplayName: string;
  expiresEpoch: number;
}

// Active connections indexed by deviceId
const clients = new Map<string, ConnectedClient>();

// Queued updates for offline devices: targetDeviceId -> PendingNotification[]
const pendingQueues = new Map<string, PendingNotification[]>();

// Active editing leases indexed by groupId:worldId
const activeLeases = new Map<string, ActiveWorldLease>();

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        status: 'ok',
        onlineClients: clients.size,
        activeLeases: activeLeases.size,
        uptime: process.uptime(),
      })
    );
    return;
  }
  res.writeHead(404);
  res.end('Not Found');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws: WebSocket, req: http.IncomingMessage) => {
  let authenticatedDeviceId: string | null = null;
  let authenticatedGroupId: string | null = null;

  ws.on('message', (data: Buffer | string) => {
    try {
      const msg = JSON.parse(data.toString());
      const { type } = msg;

      switch (type) {
        case 'REGISTER': {
          const { deviceId, token, pairedDeviceId, groupId } = msg;
          if (!deviceId || !token) {
            ws.send(JSON.stringify({ type: 'ERROR', message: 'Missing deviceId or token' }));
            return;
          }

          authenticatedDeviceId = deviceId;
          authenticatedGroupId = groupId || 'group_bedrock_survival_3device';

          clients.set(deviceId, {
            ws,
            deviceId,
            token,
            pairedDeviceId,
            groupId: authenticatedGroupId,
            lastSeenEpoch: Date.now(),
          });

          console.log(`[Presence] Registered device ${deviceId} in group ${authenticatedGroupId}`);

          // Broadcast presence to all other group members
          broadcastToGroup(authenticatedGroupId, deviceId, {
            type: 'PRESENCE_UPDATE',
            online: true,
            deviceId,
          });

          // Inform this newly connected device of other online group devices
          for (const [id, c] of clients) {
            if (id !== deviceId && c.groupId === authenticatedGroupId && c.ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: 'PRESENCE_UPDATE', online: true, deviceId: id }));
            }
          }

          // Deliver any queued pending updates (Rule C)
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

        case 'LEASE_ACQUIRE': {
          const { groupId, worldId, holderDisplayName, durationMs } = msg;
          const leaseKey = `${groupId}:${worldId}`;
          const expiresEpoch = Date.now() + (durationMs || 15 * 60 * 1000);

          activeLeases.set(leaseKey, {
            groupId,
            worldId,
            holderDeviceId: authenticatedDeviceId || '',
            holderDisplayName: holderDisplayName || 'Player',
            expiresEpoch,
          });

          console.log(`[Lease] Acquired lease for ${leaseKey} by ${authenticatedDeviceId}`);
          broadcastToGroup(groupId, authenticatedDeviceId || '', {
            type: 'LEASE_STATUS_CHANGED',
            groupId,
            worldId,
            holderDeviceId: authenticatedDeviceId,
            holderDisplayName,
            expiresEpoch,
          });
          break;
        }

        case 'LEASE_RELEASE': {
          const { groupId, worldId } = msg;
          const leaseKey = `${groupId}:${worldId}`;
          activeLeases.delete(leaseKey);

          console.log(`[Lease] Released lease for ${leaseKey}`);
          broadcastToGroup(groupId, authenticatedDeviceId || '', {
            type: 'LEASE_STATUS_CHANGED',
            groupId,
            worldId,
            holderDeviceId: null,
            holderDisplayName: null,
            expiresEpoch: 0,
          });
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
    if (authenticatedDeviceId && authenticatedGroupId) {
      broadcastToGroup(authenticatedGroupId, authenticatedDeviceId, {
        type: 'PRESENCE_UPDATE',
        online: false,
        deviceId: authenticatedDeviceId,
      });
      clients.delete(authenticatedDeviceId);
      console.log(`[Presence] Device disconnected: ${authenticatedDeviceId}`);
    }
  });

  ws.on('error', (err: Error) => {
    console.error('[Signaling] WebSocket connection error:', err);
  });
});

function broadcastToGroup(groupId: string, senderId: string, message: object) {
  const payload = JSON.stringify(message);
  for (const [id, client] of clients) {
    if (id !== senderId && client.groupId === groupId && client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(payload);
    }
  }
}

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Minecraft GitSync Signaling Server listening on port ${PORT}`);
});
