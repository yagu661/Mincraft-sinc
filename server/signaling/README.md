# Minecraft GitSync Signaling Backend

A lightweight, secure WebSocket signaling server for coordinating peer-to-peer WebRTC connections between two Minecraft players over the internet.

## Responsibilities
- **Presence Detection:** Monitors when Device A and Device B are online.
- **SDP Exchange:** Relays WebRTC Offer and Answer messages.
- **ICE Candidate Exchange:** Exchanges STUN/TURN network path candidates.
- **Pending Updates Queue:** If Player B is offline when Player A finishes a Minecraft session, this server preserves the update metadata (`commitId`, `worldId`) and delivers it immediately when Player B reconnects (Rules B & C).

> **Privacy & Security Guarantee:**
> This server **never** receives, handles, or stores Minecraft world archives. All binary .mcworld data is transferred directly phone-to-phone via encrypted WebRTC DataChannels.

## Environment Variables
| Variable | Default | Description |
|---|---|---|
| `SIGNALING_PORT` | `8443` | WebSocket listening port |
| `NODE_ENV` | `production` | Node environment |

## Running Locally
```bash
npm install
npm run dev
```

## Running with Docker
```bash
docker build -t gitsync-signaling .
docker run -p 8443:8443 gitsync-signaling
```
