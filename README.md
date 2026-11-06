# Minecraft GitSync (Native Android)

**Automatic Bedrock World Synchronization between Two Friends**

Minecraft GitSync is a native Android application built with Kotlin, Jetpack Compose (Material 3), Room Database, WorkManager, and WebRTC DataChannels. It automatically synchronizes a Minecraft Bedrock Edition world between two friends over the internet without either player needing to press PUSH or PULL manually.

---

## 🎯 Core Operating Principles

1. **Automatic Detection:** Uses `UsageStatsManager` to detect when a player closes Minecraft (`com.mojang.minecraftpe`).
2. **Grace Period Verification:** Waits a configurable 8-second grace period to ensure Minecraft has completely finished flushing its LevelDB chunk logs (`db/` files) and that the user hasn't merely switched apps briefly.
3. **Consistent Snapshot Generation:** Packages the world into an immutable `.mcworld` container, computes a streaming **SHA-256** checksum, and stores the commit in a local Room database.
4. **Internet P2P Transfer (WebRTC):** Establishes an encrypted, direct WebRTC DataChannel connection between both devices using STUN servers. Works across cellular and different Wi-Fi networks without port forwarding.
5. **No Cloud Storage of World Archives:** The signaling server only handles connection negotiation (SDP/ICE) and offline presence queuing. World archives are **never** uploaded to any third-party server.
6. **Git-Style Divergence Detection:** Evaluates branch history before installing incoming snapshots. If both players modified the world concurrently from the same parent commit, a divergence conflict is flagged and both branches are safely preserved. No active Minecraft world is ever overwritten.

---

## 📂 Architecture Overview

```
app/
├── src/main/
│   ├── AndroidManifest.xml          # Permissions (UsageStats, Foregrounds, Internet, SAF)
│   ├── java/com/gitsync/minecraft/
│   │   ├── GitSyncApplication.kt    # App lifecycle & notification channels
│   │   ├── core/
│   │   │   ├── model/               # Commit, WorldSnapshot, SyncState, DeviceIdentity, StorageCapability
│   │   │   ├── database/            # Room AppDatabase, CommitDao, SyncQueueDao
│   │   │   ├── storage/             # MinecraftStorageDetector, StorageAccessHelper, WorldArchiver, Sha256Checksum, McWorldImporter
│   │   │   ├── security/            # KeystoreManager (AES256-GCM), PairingTokenGenerator
│   │   │   └── networking/          # WebRtcDataChannelManager, SignalingClient, ChunkProtocol, FileTransferSession
│   │   ├── feature/
│   │   │   ├── monitoring/          # MinecraftSessionMonitor, MinecraftUsageStatsHelper
│   │   │   ├── backup/              # BackupEngine, DivergenceDetector
│   │   │   └── sync/                # SyncEngine, NotificationHelper, AutoSnapshotWorker, DirectPushWorker, QueuedSyncWorker
│   │   └── ui/
│   │       ├── MainActivity.kt      # Main entry point & Compose navigation
│   │       ├── MainViewModel.kt     # State management & manual recovery actions
│   │       ├── theme/               # Dark Graphite palette, Emerald accents, typography
│   │       └── screens/             # DashboardScreen, StorageSetupScreen, PairingScreen, HistoryScreen, ConflictResolutionScreen, SettingsScreen, TestModeScreen
│   └── res/                         # Strings, colors, themes, backup rules
server/
└── signaling/                       # Lightweight TypeScript WebSocket signaling server
.github/
└── workflows/
    └── build-apk.yml                # CI/CD workflow building app-debug.apk
```

---

## 🛡️ Critical Minecraft Bedrock Android Storage Handling

Minecraft Bedrock on Android operates under two primary storage configurations:
- **Application Storage:** Sandboxed within `/data/data/com.mojang.minecraftpe/` (inaccessible on unrooted devices).
- **External Storage:** Located either at legacy `/sdcard/games/com.mojang/minecraftWorlds/` or modern `/sdcard/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/`.

### Android 11+ (API 30+) Scoped Storage Compliance
Starting with Android 11, direct filesystem traversal into `Android/data/` is blocked by the OS. GitSync handles this transparently:
1. **Capability Detection:** `MinecraftStorageDetector` checks whether the world directory is directly accessible, read-only, or restricted.
2. **Storage Access Framework (SAF):** Prompts the user to authorize the Minecraft folder once via Android's native system folder picker (`ACTION_OPEN_DOCUMENT_TREE`). The granted URI permission is persisted across reboots via `takePersistableUriPermission`.
3. **Safe .mcworld Fallback:** If Android prevents direct atomic in-place folder replacement, GitSync stages the verified `.mcworld` file and offers a one-tap native import intent (`ACTION_VIEW` with MIME type `application/mcworld`) that opens Minecraft directly to import the world.
4. **Zero Silent Overwrites:** The app never claims automatic replacement has occurred unless verified write access is active.

---

## ⚡ Git-Style Synchronization Rules

| Rule | Description |
|---|---|
| **RULE A** | When Player A closes Minecraft and creates a new snapshot, GitSync pushes it to Player B automatically. |
| **RULE B** | When Player B is offline, GitSync queues the update in Room (`sync_queue`). |
| **RULE C** | When Player B reconnects, pending updates are delivered automatically via WebRTC. |
| **RULE D** | When Player B finishes playing, B's new snapshot is pushed back to A. |
| **RULE E** | GitSync never overwrites a newer confirmed snapshot with an older one. |
| **RULE F** | Never synchronizes or overwrites while Minecraft is actively running on the destination device. |
| **RULE G** | If both players modify the same world from a common parent commit, divergence is detected immediately. |
| **RULE H** | Both branches are preserved until the user chooses how to resolve the conflict. |
| **RULE I** | If automatic folder replacement is restricted, GitSync notifies the user and stages the `.mcworld` for import. |
| **RULE J** | If a transfer is interrupted, the last working world is preserved and the transfer retries with chunk offset resumption. |

---

## 📦 Building `app-debug.apk`

### Option 1: Automated GitHub Actions Build (Recommended)
1. Push this repository to GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit for Minecraft GitSync"
   git branch -M main
   git remote add origin https://github.com/<your-username>/minecraft-gitsync.git
   git push -u origin main
   ```
2. Navigate to the **Actions** tab on your GitHub repository.
3. Select the **Build Minecraft GitSync Android APK** workflow.
4. When the build finishes (approx. 2–3 minutes), scroll down to **Artifacts** and download **`app-debug`**.
5. Extract the ZIP to obtain `app-debug.apk` and install it on your Android devices:
   ```bash
   adb install -r app-debug.apk
   ```

### Option 2: Local Gradle Build with Android Studio
1. Open the project folder in **Android Studio Hedgehog / Iguana / Jellyfish (2023.2+)**.
2. Allow Gradle sync to complete with JDK 17.
3. Run:
   ```bash
   ./gradlew assembleDebug
   ```
4. The generated APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Testing Without Minecraft (Test Mode)

GitSync includes a built-in **Test Mode & Simulator** (`TestModeScreen`):
1. Navigate to **Test Mode** on the dashboard.
2. Tap **CREATE SYNTHETIC TEST WORLD**.
3. GitSync generates a synthetic Bedrock LevelDB world hierarchy (`levelname.txt`, `db/CURRENT`, `db/MANIFEST-000001`, chunk binary tables), compresses it into a verified `.mcworld` container, computes its SHA-256 hash, and registers the commit in Room.
4. You can then test pairing, presence detection, and WebRTC streaming between two devices without having Minecraft installed.

---

## 🔒 Security Specifications
- **Device Identity:** Unique random UUIDs generated on first boot.
- **Mutual Authentication:** High-entropy Crockford-base32 pairing tokens stored in hardware-backed `EncryptedSharedPreferences` (AES-256-GCM / AES-256-SIV).
- **Encrypted Transport:** WebRTC DataChannels are end-to-end encrypted with DTLS-SRTP.
- **Archive Integrity:** Streaming SHA-256 calculation verifies file checksums before and after transfer. Corrupted or tampered archives are automatically rejected and discarded.
