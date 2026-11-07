/**
 * Minecraft World Sync — Cloudflare Worker with Durable Objects & R2 Storage
 *
 * Deployed at: https://spring-paper-f60a.xmenlittlegamer.workers.dev
 * WebSocket endpoint: wss://spring-paper-f60a.xmenlittlegamer.workers.dev/ws?room=friendgroup123
 */

export interface Env {
  WORLDS_BUCKET: R2Bucket;
  SYNC_ROOM: DurableObjectNamespace;
}

interface DevicePresence {
  deviceId: string;
  displayName: string;
  role: string;
  androidVersion: number;
  lastSeen: number;
}

interface ActiveLease {
  worldId: string;
  holderDeviceId: string;
  holderDisplayName: string;
  expiresEpoch: number;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // Root status check
    if (url.pathname === '/' || url.pathname === '/health') {
      return new Response(
        JSON.stringify({
          status: 'online',
          service: 'Minecraft World Sync',
          websocket: '/ws',
          endpoints: {
            ws: '/ws?room=friendgroup123',
            uploadSnapshot: 'PUT /api/snapshots/:worldId/:commitId',
            downloadSnapshot: 'GET /api/snapshots/:worldId/:commitId',
            latestSnapshot: 'GET /api/snapshots/:worldId/latest'
          }
        }),
        { headers: { 'Content-Type': 'application/json' } }
      );
    }

    // WebSocket Room Gateway -> Durable Object
    if (url.pathname === '/ws') {
      const upgradeHeader = request.headers.get('Upgrade');
      if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
        return new Response('Expected WebSocket Upgrade', { status: 426 });
      }

      const roomId = url.searchParams.get('room') || 'friendgroup123';
      const id = env.SYNC_ROOM.idFromName(roomId);
      const roomObject = env.SYNC_ROOM.get(id);
      return roomObject.fetch(request);
    }

    // Binary Snapshot Upload (PUT /api/snapshots/:worldId/:commitId)
    if (request.method === 'PUT' && url.pathname.startsWith('/api/snapshots/')) {
      const parts = url.pathname.replace('/api/snapshots/', '').split('/');
      if (parts.length < 2) {
        return new Response('Missing worldId or commitId in path', { status: 400 });
      }

      const [worldId, commitId] = parts;
      const sha256 = request.headers.get('X-SHA256-Checksum') || '';
      const deviceId = request.headers.get('X-Device-Id') || 'unknown';
      const authorName = request.headers.get('X-Author-Name') || 'Player';
      const commitMessage = request.headers.get('X-Commit-Message') || '';
      const timestamp = request.headers.get('X-Timestamp') || Date.now().toString();

      const objectKey = `${worldId}/${commitId}.mcworld`;

      await env.WORLDS_BUCKET.put(objectKey, request.body, {
        httpMetadata: {
          contentType: 'application/octet-stream'
        },
        customMetadata: {
          worldId,
          commitId,
          sha256,
          deviceId,
          authorName,
          commitMessage,
          timestamp
        }
      });

      // Also record latest pointer for quick device-switching checks
      const latestMetaKey = `${worldId}/latest.json`;
      await env.WORLDS_BUCKET.put(
        latestMetaKey,
        JSON.stringify({
          worldId,
          commitId,
          sha256Checksum: sha256,
          authorName,
          commitMessage,
          timestamp: parseInt(timestamp, 10),
          downloadUrl: `${url.origin}/api/snapshots/${worldId}/${commitId}`
        }),
        { httpMetadata: { contentType: 'application/json' } }
      );

      return new Response(
        JSON.stringify({
          success: true,
          worldId,
          commitId,
          objectKey,
          downloadUrl: `${url.origin}/api/snapshots/${worldId}/${commitId}`
        }),
        { headers: { 'Content-Type': 'application/json' } }
      );
    }

    // Binary Snapshot Download (GET /api/snapshots/:worldId/:commitId)
    if (request.method === 'GET' && url.pathname.startsWith('/api/snapshots/')) {
      const subpath = url.pathname.replace('/api/snapshots/', '');
      const parts = subpath.split('/');

      // Latest metadata request
      if (parts.length === 2 && parts[1] === 'latest') {
        const worldId = parts[0];
        const latestObj = await env.WORLDS_BUCKET.get(`${worldId}/latest.json`);
        if (!latestObj) {
          return new Response(JSON.stringify({ error: 'No snapshots found for world' }), {
            status: 404,
            headers: { 'Content-Type': 'application/json' }
          });
        }
        return new Response(latestObj.body, {
          headers: { 'Content-Type': 'application/json' }
        });
      }

      // Specific commit binary download
      if (parts.length >= 2) {
        const [worldId, commitId] = parts;
        const objectKey = `${worldId}/${commitId}.mcworld`;
        const object = await env.WORLDS_BUCKET.get(objectKey);

        if (!object) {
          return new Response(JSON.stringify({ error: 'Snapshot not found in R2' }), {
            status: 404,
            headers: { 'Content-Type': 'application/json' }
          });
        }

        const headers = new Headers();
        headers.set('Content-Type', 'application/octet-stream');
        headers.set('Content-Length', object.size.toString());
        if (object.httpEtag) headers.set('ETag', object.httpEtag);
        if (object.customMetadata?.sha256) {
          headers.set('X-SHA256-Checksum', object.customMetadata.sha256);
        }

        return new Response(object.body, { headers });
      }
    }

    return new Response('Not Found', { status: 404 });
  }
};

/**
 * Durable Object managing room synchronization and message forwarding.
 */
export class SyncRoom {
  private state: DurableObjectState;
  private sessions: Set<WebSocket> = new Set();
  private deviceSessions: Map<WebSocket, DevicePresence> = new Map();
  private activeLeases: Map<string, ActiveLease> = new Map();

  constructor(state: DurableObjectState) {
    this.state = state;
  }

  async fetch(request: Request): Promise<Response> {
    const webSocketPair = new WebSocketPair();
    const [client, server] = Object.values(webSocketPair);

    server.accept();
    this.sessions.add(server);

    // Initial greeting
    server.send(
      JSON.stringify({
        type: 'connected',
        room: 'ready',
        timestamp: Date.now()
      })
    );

    server.addEventListener('message', async (event) => {
      try {
        const text = typeof event.data === 'string' ? event.data : new TextDecoder().decode(event.data);
        const data = JSON.parse(text);

        switch (data.type) {
          case 'PING':
            server.send(JSON.stringify({ type: 'PONG', timestamp: Date.now() }));
            break;

          case 'DEVICE_REGISTER': {
            const presence: DevicePresence = {
              deviceId: data.deviceId,
              displayName: data.displayName || 'Player Phone',
              role: data.role || 'MEMBER',
              androidVersion: data.androidVersion || 11,
              lastSeen: Date.now()
            };
            this.deviceSessions.set(server, presence);

            // Forward registration to all other connected devices in this room
            this.broadcast(text, server);
            break;
          }

          case 'SYNC_NOTIFICATION':
            // Broadcast new world snapshot availability to all other connected phones
            this.broadcast(text, server);
            break;

          case 'LEASE_REQUEST': {
            const worldId = data.worldId || 'SurvivalWorld';
            const now = Date.now();
            const currentLease = this.activeLeases.get(worldId);

            if (currentLease && now < currentLease.expiresEpoch && currentLease.holderDeviceId !== data.holderDeviceId) {
              // Denied: Another device is actively holding the lock
              server.send(
                JSON.stringify({
                  type: 'LEASE_DENIED',
                  worldId,
                  holderDisplayName: currentLease.holderDisplayName,
                  expiresEpoch: currentLease.expiresEpoch
                })
              );
            } else {
              // Granted: 90s TTL lease
              const newLease: ActiveLease = {
                worldId,
                holderDeviceId: data.holderDeviceId,
                holderDisplayName: data.holderDisplayName,
                expiresEpoch: now + 90_000
              };
              this.activeLeases.set(worldId, newLease);
              this.broadcast(
                JSON.stringify({
                  type: 'LEASE_GRANTED',
                  worldId,
                  holderDeviceId: data.holderDeviceId,
                  holderDisplayName: data.holderDisplayName,
                  expiresEpoch: newLease.expiresEpoch
                })
              );
            }
            break;
          }

          case 'LEASE_RELEASE': {
            const worldId = data.worldId || 'SurvivalWorld';
            this.activeLeases.delete(worldId);
            this.broadcast(
              JSON.stringify({
                type: 'LEASE_RELEASED',
                worldId,
                holderDeviceId: data.holderDeviceId
              })
            );
            break;
          }

          default:
            // Standard peer-to-peer WebRTC signaling (OFFER, ANSWER, ICE_CANDIDATE) relay
            this.broadcast(text, server);
            break;
        }
      } catch {
        // Forward raw text or binary payload to room peers
        this.broadcast(event.data, server);
      }
    });

    const cleanup = () => {
      const presence = this.deviceSessions.get(server);
      if (presence) {
        this.broadcast(
          JSON.stringify({
            type: 'PEER_DISCONNECTED',
            deviceId: presence.deviceId,
            displayName: presence.displayName
          })
        );
      }
      this.sessions.delete(server);
      this.deviceSessions.delete(server);
    };

    server.addEventListener('close', cleanup);
    server.addEventListener('error', cleanup);

    return new Response(null, { status: 101, webSocket: client });
  }

  private broadcast(message: any, sender: WebSocket | null = null) {
    for (const session of this.sessions) {
      if (session !== sender) {
        try {
          session.send(message);
        } catch {
          this.sessions.delete(session);
        }
      }
    }
  }
}
