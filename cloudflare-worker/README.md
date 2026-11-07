# Minecraft World Sync — Cloudflare Worker & R2 Backend

This is the production backend for **Minecraft GitSync**, providing real-time room coordination across your 3 Android devices via **Durable Objects** and permanent world snapshot storage via **Cloudflare R2**.

- **Live Worker URL:** `https://spring-paper-f60a.xmenlittlegamer.workers.dev`
- **Live WebSocket Endpoint:** `wss://spring-paper-f60a.xmenlittlegamer.workers.dev/ws?room=friendgroup123`

---

## 1. Cloudflare Dashboard Setup (R2 Storage)

The worker requires an R2 bucket named `minecraft-worlds` to store the binary world snapshots (`.mcworld` files).

1. Log in to the [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. In the left navigation sidebar, click **R2**.
3. Click **Create bucket**.
4. Name the bucket: `minecraft-worlds`
5. Select location **Automatic** (or closest region) and click **Create Bucket**.

---

## 2. Deploying the Worker

Make sure you have Node.js and npm installed.

```bash
cd cloudflare-worker
npm install

# Log in to your Cloudflare account (if not already logged in)
npx wrangler login

# Deploy Worker, Durable Object migration, and R2 bindings
npx wrangler deploy
```

Once deployed, wrangler will confirm:
```
Uploaded spring-paper-f60a
Deployed to https://spring-paper-f60a.xmenlittlegamer.workers.dev
```

---

## 3. Endpoints Overview

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` or `/health` | Health check & service info |
| `GET` | `/ws?room=friendgroup123` | WebSocket room gateway (Durable Object) |
| `PUT` | `/api/snapshots/:worldId/:commitId` | Streams `.mcworld` binary into R2 with SHA-256 validation |
| `GET` | `/api/snapshots/:worldId/:commitId` | Streams `.mcworld` binary from R2 to Android app |
| `GET` | `/api/snapshots/:worldId/latest` | Returns latest commit metadata JSON |

---

## 4. WebSocket Room Events (`friendgroup123`)

The WebSocket handles real-time coordination without sending bulk binary data over sockets:

- `DEVICE_REGISTER`: When Device A (Owner M30s), Device B (Friend Phone 1), or Device C (Friend Phone 2) connects.
- `SYNC_NOTIFICATION`: Broadcasts when an upload finishes so all online devices immediately download the update.
- `LEASE_REQUEST` & `LEASE_GRANTED`: Single Active Player Lock (90-second TTL) ensuring only one phone modifies the world at a time.
- `PING` / `PONG`: Heartbeat keeping the connection alive through mobile network sleep.
