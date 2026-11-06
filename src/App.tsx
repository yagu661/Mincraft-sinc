/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import {
  Smartphone,
  FolderGit2,
  Wifi,
  WifiOff,
  RefreshCw,
  ShieldCheck,
  HardDrive,
  GitBranch,
  GitCommit,
  CheckCircle2,
  AlertTriangle,
  Play,
  RotateCcw,
  FileCode2,
  Layers,
  Terminal,
  Activity,
  ArrowRight,
  Download,
  Copy,
  ExternalLink,
  Cpu
} from 'lucide-react';

interface SimulatedCommit {
  commitId: string;
  worldId: string;
  parentCommitId: string | null;
  message: string;
  author: string;
  timestamp: string;
  sizeKb: number;
  sha256: string;
  status: 'SYNCED' | 'PENDING' | 'CONFLICT' | 'RECEIVING';
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'architecture' | 'saf' | 'apk' | 'server'>('simulator');
  
  // Simulation State for Device A (Alex) and Device B (Sam)
  const [deviceAOnline, setDeviceAOnline] = useState(true);
  const [deviceBOnline, setDeviceBOnline] = useState(true);
  const [deviceAPlaying, setDeviceAPlaying] = useState(false);
  const [deviceBPlaying, setDeviceBPlaying] = useState(false);
  const [gracePeriodTimer, setGracePeriodTimer] = useState<number | null>(null);
  const [transferProgress, setTransferProgress] = useState(0);
  const [isTransferring, setIsTransferring] = useState(false);
  const [activeTransferSender, setActiveTransferSender] = useState<'A' | 'B' | null>(null);

  // Commit history for the simulated Minecraft world
  const [commits, setCommits] = useState<SimulatedCommit[]>([
    {
      commitId: 'c_9f81a2e4',
      worldId: 'SurvivalWorld',
      parentCommitId: null,
      message: 'v1 - Genesis world generated',
      author: 'Alex (Pixel 8)',
      timestamp: 'Today at 09:15',
      sizeKb: 14200,
      sha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      status: 'SYNCED'
    },
    {
      commitId: 'c_b44120ca',
      worldId: 'SurvivalWorld',
      parentCommitId: 'c_9f81a2e4',
      message: 'v2 - Starter house built & mining cave lit',
      author: 'Sam (Galaxy S24)',
      timestamp: 'Today at 10:42',
      sizeKb: 16840,
      sha256: '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069',
      status: 'SYNCED'
    }
  ]);

  const [conflictDetected, setConflictDetected] = useState(false);
  const [logs, setLogs] = useState<string[]>([
    '[INIT] Minecraft GitSync Android Engine v1.0.0 initialized.',
    '[KEYSTORE] Hardware AES-256-GCM credentials loaded for Device A and Device B.',
    '[STORAGE] SAF folder authorized at tree: content://com.android.externalstorage.documents/tree/primary%3Agames%2Fcom.mojang',
    '[WEBRTC] STUN stun.l.google.com:19302 established peer candidate pool.'
  ]);

  const addLog = (msg: string) => {
    setLogs(prev => [msg, ...prev.slice(0, 19)]);
  };

  // Grace period countdown effect
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (gracePeriodTimer !== null && gracePeriodTimer > 0) {
      interval = setInterval(() => {
        setGracePeriodTimer(t => (t !== null && t > 1 ? t - 1 : null));
      }, 1000);
    } else if (gracePeriodTimer === 0 || gracePeriodTimer === null) {
      // Trigger snapshot when grace period finishes
    }
    return () => clearInterval(interval);
  }, [gracePeriodTimer]);

  // Handle Alex stopping Minecraft
  const handleStopMinecraftA = () => {
    setDeviceAPlaying(false);
    addLog('[SESSION] Player Alex left Minecraft. Starting 8-second grace period verification...');
    setGracePeriodTimer(8);

    setTimeout(() => {
      addLog('[STORAGE] Grace period elapsed. Verifying LevelDB lock on /db/MANIFEST-000001...');
      addLog('[ARCHIVER] Packaging world into .mcworld zip container with 64KB streaming buffers.');
      
      const newSha = 'a84f' + Math.random().toString(16).substring(2, 10) + '9b01c3d4e5f6';
      const newCommitId = 'c_' + Math.random().toString(16).substring(2, 10);
      const newCommit: SimulatedCommit = {
        commitId: newCommitId,
        worldId: 'SurvivalWorld',
        parentCommitId: commits[0]?.commitId || null,
        message: `v${commits.length + 1} - Nether portal explored by Alex`,
        author: 'Alex (Pixel 8)',
        timestamp: 'Just now',
        sizeKb: 18400,
        sha256: newSha,
        status: deviceBOnline ? 'PENDING' : 'PENDING'
      };

      setCommits(prev => [newCommit, ...prev]);
      addLog(`[ROOM] Saved commit ${newCommitId} to local Room SQLite. SHA-256: ${newSha.substring(0, 16)}...`);

      if (deviceBOnline) {
        addLog('[WEBRTC] Player Sam is ONLINE. Negotiating SDP Offer/Answer over signaling WebSocket...');
        triggerTransfer('A', newCommitId);
      } else {
        addLog('[QUEUE] Player Sam is OFFLINE (Rule B). Update enqueued in Room sync_queue. Will auto-deliver on reconnect.');
      }
    }, 4000);
  };

  const triggerTransfer = (sender: 'A' | 'B', commitId: string) => {
    setIsTransferring(true);
    setActiveTransferSender(sender);
    setTransferProgress(0);

    let prog = 0;
    const interval = setInterval(() => {
      prog += 20;
      setTransferProgress(prog);
      addLog(`[DATACHANNEL] Streaming binary chunk ${prog / 20}/5 (32KB chunk). Backpressure safe.`);
      if (prog >= 100) {
        clearInterval(interval);
        setIsTransferring(false);
        setActiveTransferSender(null);
        addLog('[VERIFY] Receiving device calculated SHA-256 matching header! Verification PASSED.');
        addLog('[REPLACE] Destination device safely staged .mcworld. Storage acknowledged.');
        setCommits(prev =>
          prev.map(c => (c.commitId === commitId ? { ...c, status: 'SYNCED' } : c))
        );
      }
    }, 600);
  };

  const handleDeviceBReconnect = () => {
    setDeviceBOnline(true);
    addLog('[SIGNALING] Device B (Sam) reconnected! Signaling server notified of presence.');
    // Check pending queue (Rule C)
    const hasPending = commits.some(c => c.status === 'PENDING');
    if (hasPending) {
      addLog('[RULE C] Delivering pending update to Sam over WebRTC DataChannel automatically...');
      const target = commits.find(c => c.status === 'PENDING');
      if (target) {
        triggerTransfer('A', target.commitId);
      }
    }
  };

  const simulateDivergence = () => {
    setConflictDetected(true);
    addLog('[RULE G] Concurrent edits detected! Both Alex and Sam advanced from parent commit c_b44120ca.');
    addLog('[CONFLICT] Branch divergence identified. Preserving both snapshots to avoid LevelDB binary corruption.');
  };

  return (
    <div className="min-h-screen bg-[#101314] text-gray-100 flex flex-col font-sans selection:bg-emerald-500/30">
      {/* Top Navigation Bar */}
      <header className="border-b border-gray-800/80 bg-[#14171a]/90 backdrop-blur sticky top-0 z-50 px-6 py-4">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 flex items-center justify-center shadow-lg shadow-emerald-500/20 text-gray-950 font-black">
              <FolderGit2 className="w-6 h-6 text-gray-950" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-mono font-black text-lg tracking-wider text-white">MINECRAFT GITSYNC</h1>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  NATIVE ANDROID
                </span>
              </div>
              <p className="text-xs text-gray-400">Automatic Bedrock World P2P Synchronization via WebRTC</p>
            </div>
          </div>

          <div className="flex items-center gap-2 bg-[#1b1f23] p-1 rounded-xl border border-gray-800">
            <button
              onClick={() => setActiveTab('simulator')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'simulator'
                  ? 'bg-emerald-500 text-gray-950 shadow-sm'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Activity className="w-3.5 h-3.5" />
              Live P2P Simulator
            </button>
            <button
              onClick={() => setActiveTab('architecture')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'architecture'
                  ? 'bg-emerald-500 text-gray-950 shadow-sm'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              Android Architecture
            </button>
            <button
              onClick={() => setActiveTab('saf')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'saf'
                  ? 'bg-emerald-500 text-gray-950 shadow-sm'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <HardDrive className="w-3.5 h-3.5" />
              SAF & Bedrock Storage
            </button>
            <button
              onClick={() => setActiveTab('apk')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'apk'
                  ? 'bg-emerald-500 text-gray-950 shadow-sm'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Download className="w-3.5 h-3.5" />
              Build APK (.apk)
            </button>
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="max-w-7xl mx-auto w-full px-6 py-8 flex-1">
        {activeTab === 'simulator' && (
          <div className="space-y-6">
            {/* Status Banner */}
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6 relative overflow-hidden">
              <div className="absolute top-0 right-0 w-96 h-96 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />
              
              <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
                <div>
                  <span className="text-xs font-mono text-emerald-400 uppercase tracking-wider font-bold">
                    Active Two-Player Session
                  </span>
                  <h2 className="text-xl font-bold text-white mt-1">World: SurvivalWorld (Bedrock Edition)</h2>
                </div>
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-2 bg-[#22272b] px-3 py-1.5 rounded-lg border border-gray-700/60">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                    <span className="text-xs font-mono text-gray-300">Auto-Sync ACTIVE</span>
                  </div>
                  <button
                    onClick={simulateDivergence}
                    className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-red-500/10 text-red-400 border border-red-500/30 hover:bg-red-500/20 transition flex items-center gap-1.5"
                  >
                    <GitBranch className="w-3.5 h-3.5" />
                    Simulate Branch Divergence
                  </button>
                </div>
              </div>

              {/* Transfer Progress bar if syncing */}
              {isTransferring && (
                <div className="mb-6 bg-[#22272b] border border-blue-500/40 rounded-xl p-4">
                  <div className="flex items-center justify-between text-xs font-mono mb-2">
                    <span className="text-blue-400 flex items-center gap-2">
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      Transferring .mcworld snapshot ({activeTransferSender === 'A' ? 'Alex ➔ Sam' : 'Sam ➔ Alex'})
                    </span>
                    <span className="text-gray-300 font-bold">{transferProgress}%</span>
                  </div>
                  <div className="w-full bg-gray-800 h-2 rounded-full overflow-hidden">
                    <div
                      className="bg-emerald-500 h-full transition-all duration-300"
                      style={{ width: `${transferProgress}%` }}
                    />
                  </div>
                  <div className="text-[11px] text-gray-400 mt-2">
                    WebRTC DataChannel • Flow controlled backpressure (2MB ceiling) • 32KB chunk payload
                  </div>
                </div>
              )}

              {/* Two Phone Cards */}
              <div className="grid md:grid-cols-2 gap-6">
                {/* Phone A: Alex */}
                <div className="bg-[#22272b] border border-gray-700/60 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2.5">
                      <Smartphone className="w-5 h-5 text-emerald-400" />
                      <div>
                        <div className="font-bold text-sm text-white">Player 1: Alex</div>
                        <div className="text-xs text-gray-400 font-mono">Google Pixel 8 • dev_a1b2c3</div>
                      </div>
                    </div>
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-mono flex items-center gap-1.5 ${
                        deviceAOnline
                          ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                          : 'bg-gray-800 text-gray-400'
                      }`}
                    >
                      {deviceAOnline ? <Wifi className="w-3 h-3" /> : <WifiOff className="w-3 h-3" />}
                      {deviceAOnline ? 'Online' : 'Offline'}
                    </span>
                  </div>

                  <div className="space-y-3 text-xs bg-[#1a1e21] p-3.5 rounded-lg border border-gray-800">
                    <div className="flex justify-between text-gray-300">
                      <span>Minecraft Status:</span>
                      <span className={deviceAPlaying ? 'text-emerald-400 font-bold' : 'text-gray-400'}>
                        {deviceAPlaying ? '🎮 In Game (Active Session)' : 'Idle (Monitoring via UsageStats)'}
                      </span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>SAF World Folder:</span>
                      <span className="text-emerald-400 font-mono">Authorized (Read/Write)</span>
                    </div>
                    {gracePeriodTimer !== null && (
                      <div className="flex justify-between text-amber-400 font-bold bg-amber-500/10 p-2 rounded">
                        <span>Grace Period Countdown:</span>
                        <span>{gracePeriodTimer}s remaining</span>
                      </div>
                    )}
                  </div>

                  <div className="mt-4 flex gap-2">
                    {!deviceAPlaying ? (
                      <button
                        onClick={() => {
                          setDeviceAPlaying(true);
                          addLog('[SESSION] Player Alex launched Minecraft Bedrock (com.mojang.minecraftpe).');
                        }}
                        className="flex-1 bg-emerald-600 hover:bg-emerald-500 text-gray-950 font-bold text-xs py-2 rounded-lg transition flex items-center justify-center gap-1.5"
                      >
                        <Play className="w-3.5 h-3.5" /> Launch Minecraft
                      </button>
                    ) : (
                      <button
                        onClick={handleStopMinecraftA}
                        className="flex-1 bg-amber-600 hover:bg-amber-500 text-gray-950 font-bold text-xs py-2 rounded-lg transition"
                      >
                        Close Minecraft (Trigger Auto-Sync)
                      </button>
                    )}
                  </div>
                </div>

                {/* Phone B: Sam */}
                <div className="bg-[#22272b] border border-gray-700/60 rounded-xl p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2.5">
                      <Smartphone className="w-5 h-5 text-blue-400" />
                      <div>
                        <div className="font-bold text-sm text-white">Player 2: Sam (Friend)</div>
                        <div className="text-xs text-gray-400 font-mono">Samsung Galaxy S24 • dev_d4e5f6</div>
                      </div>
                    </div>
                    <span
                      className={`text-xs px-2.5 py-1 rounded-full font-mono flex items-center gap-1.5 ${
                        deviceBOnline
                          ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                          : 'bg-red-500/15 text-red-400 border border-red-500/30'
                      }`}
                    >
                      {deviceBOnline ? <Wifi className="w-3 h-3" /> : <WifiOff className="w-3 h-3" />}
                      {deviceBOnline ? 'Online' : 'Offline'}
                    </span>
                  </div>

                  <div className="space-y-3 text-xs bg-[#1a1e21] p-3.5 rounded-lg border border-gray-800">
                    <div className="flex justify-between text-gray-300">
                      <span>Minecraft Status:</span>
                      <span className={deviceBPlaying ? 'text-emerald-400 font-bold' : 'text-gray-400'}>
                        {deviceBPlaying ? '🎮 In Game' : 'Idle'}
                      </span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>Storage Access:</span>
                      <span className="text-blue-400 font-mono">SAF / Scoped Storage Import</span>
                    </div>
                    <div className="flex justify-between text-gray-300">
                      <span>P2P Receiver:</span>
                      <span className="text-gray-300">No manual PULL required</span>
                    </div>
                  </div>

                  <div className="mt-4 flex gap-2">
                    <button
                      onClick={() => {
                        if (deviceBOnline) {
                          setDeviceBOnline(false);
                          addLog('[PRESENCE] Device B (Sam) went OFFLINE. Testing Rule B (Queueing).');
                        } else {
                          handleDeviceBReconnect();
                        }
                      }}
                      className="flex-1 bg-[#2e353b] hover:bg-[#384148] text-gray-200 font-semibold text-xs py-2 rounded-lg transition"
                    >
                      {deviceBOnline ? 'Toggle Offline (Test Rule B)' : 'Reconnect Online (Test Rule C)'}
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Conflict Warning if triggered */}
            {conflictDetected && (
              <div className="bg-red-500/10 border border-red-500/40 rounded-xl p-5">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="w-6 h-6 text-red-400 flex-shrink-0 mt-0.5" />
                  <div>
                    <h4 className="text-sm font-bold text-red-400">
                      Git-Style Divergence Detected (Rule G & Rule H)
                    </h4>
                    <p className="text-xs text-gray-300 mt-1 leading-relaxed">
                      Both Alex and Sam edited the world concurrently from the same parent commit. Minecraft Bedrock
                      LevelDB files cannot be automatically merged without risking terrain corruption. Both branches are
                      safely preserved in Room. Choose an action:
                    </p>
                    <div className="mt-3 flex gap-2">
                      <button
                        onClick={() => {
                          setConflictDetected(false);
                          addLog('[RESOLVED] User chose Alex’s timeline. Preserved Sam’s version as backup.');
                        }}
                        className="text-xs bg-emerald-600 hover:bg-emerald-500 text-gray-950 font-bold px-3 py-1.5 rounded-lg"
                      >
                        Keep Alex's Version
                      </button>
                      <button
                        onClick={() => {
                          setConflictDetected(false);
                          addLog('[RESOLVED] User chose Sam’s timeline. Restored Sam’s snapshot.');
                        }}
                        className="text-xs bg-blue-600 hover:bg-blue-500 text-white font-bold px-3 py-1.5 rounded-lg"
                      >
                        Keep Sam's Version
                      </button>
                      <button
                        onClick={() => {
                          setConflictDetected(false);
                          addLog('[RESOLVED] Exported both branches as separate .mcworld files.');
                        }}
                        className="text-xs bg-gray-700 hover:bg-gray-600 text-gray-200 font-bold px-3 py-1.5 rounded-lg"
                      >
                        Keep Both (Branch Clone)
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Commit History & Log Feed */}
            <div className="grid lg:grid-cols-3 gap-6">
              {/* Commit History */}
              <div className="lg:col-span-2 bg-[#181b1e] border border-gray-800 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <GitCommit className="w-5 h-5 text-emerald-400" />
                    <h3 className="font-bold text-sm text-white">Immutable World Commit History (Room DB)</h3>
                  </div>
                  <span className="text-xs font-mono text-gray-400">{commits.length} commits</span>
                </div>

                <div className="space-y-3">
                  {commits.map((commit, idx) => (
                    <div
                      key={commit.commitId}
                      className="bg-[#22272b] border border-gray-800 rounded-lg p-3.5 hover:border-gray-700 transition"
                    >
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="font-mono text-emerald-400 font-bold">{commit.commitId}</span>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold ${
                            commit.status === 'SYNCED'
                              ? 'bg-emerald-500/20 text-emerald-400'
                              : 'bg-amber-500/20 text-amber-400'
                          }`}
                        >
                          {commit.status}
                        </span>
                      </div>
                      <div className="font-semibold text-sm text-gray-100">{commit.message}</div>
                      <div className="text-xs text-gray-400 mt-1 flex flex-wrap gap-x-4 gap-y-1">
                        <span>Author: {commit.author}</span>
                        <span>Size: {(commit.sizeKb / 1024).toFixed(1)} MB</span>
                        <span>{commit.timestamp}</span>
                      </div>
                      <div className="mt-2 text-[10px] font-mono text-gray-500 truncate">
                        SHA-256: {commit.sha256}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Live Diagnostic Logs */}
              <div className="bg-[#181b1e] border border-gray-800 rounded-xl p-5 flex flex-col">
                <div className="flex items-center gap-2 mb-3">
                  <Terminal className="w-4 h-4 text-emerald-400" />
                  <h3 className="font-bold text-sm text-white">Android Engine Diagnostic Log</h3>
                </div>
                <div className="bg-[#101314] border border-gray-800/80 rounded-lg p-3 font-mono text-[11px] space-y-1.5 flex-1 overflow-y-auto max-h-96 text-gray-300">
                  {logs.map((log, i) => (
                    <div key={i} className="leading-relaxed">
                      {log.startsWith('[SESSION]') && <span className="text-emerald-400">{log}</span>}
                      {log.startsWith('[STORAGE]') && <span className="text-blue-400">{log}</span>}
                      {log.startsWith('[WEBRTC]') && <span className="text-purple-400">{log}</span>}
                      {log.startsWith('[QUEUE]') && <span className="text-amber-400">{log}</span>}
                      {log.startsWith('[CONFLICT]') && <span className="text-red-400">{log}</span>}
                      {!['[SESSION]', '[STORAGE]', '[WEBRTC]', '[QUEUE]', '[CONFLICT]'].some(p => log.startsWith(p)) && (
                        <span>{log}</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'architecture' && (
          <div className="space-y-6">
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6">
              <h2 className="text-lg font-bold text-white mb-2">Native Android Clean Architecture</h2>
              <p className="text-xs text-gray-400 mb-6">
                All components are written in Kotlin using Jetpack Compose Material 3, AndroidX Room, WorkManager, and
                hardware-backed Android Keystore.
              </p>

              <div className="grid md:grid-cols-3 gap-4">
                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center gap-2 text-emerald-400 font-mono text-xs font-bold mb-2">
                    <FolderGit2 className="w-4 h-4" /> core/storage
                  </div>
                  <ul className="text-xs text-gray-300 space-y-2 font-mono">
                    <li>• MinecraftStorageDetector.kt</li>
                    <li>• StorageAccessHelper.kt</li>
                    <li>• WorldArchiver.kt</li>
                    <li>• Sha256Checksum.kt</li>
                    <li>• McWorldImporter.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Manages SAF folder grants, LevelDB write verification, streaming zip compression, and SHA-256 calculation.
                  </p>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center gap-2 text-blue-400 font-mono text-xs font-bold mb-2">
                    <Wifi className="w-4 h-4" /> core/networking
                  </div>
                  <ul className="text-xs text-gray-300 space-y-2 font-mono">
                    <li>• WebRtcDataChannelManager.kt</li>
                    <li>• SignalingClient.kt</li>
                    <li>• ChunkProtocol.kt</li>
                    <li>• FileTransferSession.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Direct P2P binary transport over WebRTC DataChannels with STUN negotiation and flow-controlled backpressure.
                  </p>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center gap-2 text-purple-400 font-mono text-xs font-bold mb-2">
                    <Cpu className="w-4 h-4" /> feature/sync & workers
                  </div>
                  <ul className="text-xs text-gray-300 space-y-2 font-mono">
                    <li>• MinecraftSessionMonitor.kt</li>
                    <li>• AutoSnapshotWorker.kt</li>
                    <li>• DirectPushWorker.kt</li>
                    <li>• QueuedSyncWorker.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Foreground WorkManager dataSync workers, boot-time reconciliation, and UsageStatsManager session tracking.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'saf' && (
          <div className="space-y-6">
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6">
              <h2 className="text-lg font-bold text-white mb-2">Storage Access Framework & Scoped Storage</h2>
              <p className="text-xs text-gray-400 mb-6">
                How Minecraft GitSync accesses Bedrock world files under strict modern Android 11+ (API 30+) security boundaries.
              </p>

              <div className="space-y-4">
                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800 flex gap-4">
                  <div className="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center flex-shrink-0 font-bold">
                    1
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-white">Storage Capability Detection</h3>
                    <p className="text-xs text-gray-300 mt-1 leading-relaxed">
                      <code>MinecraftStorageDetector</code> verifies whether the storage is Application (internal private sandbox), Legacy External (`/sdcard/games/com.mojang/`), or Scoped External (`Android/data/com.mojang.minecraftpe/`).
                    </p>
                  </div>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800 flex gap-4">
                  <div className="w-8 h-8 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center flex-shrink-0 font-bold">
                    2
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-white">Folder Authorization via SAF</h3>
                    <p className="text-xs text-gray-300 mt-1 leading-relaxed">
                      Uses <code>ACTION_OPEN_DOCUMENT_TREE</code> so the player can authorize their Minecraft folder in one tap. The granted URI permission is persisted indefinitely using <code>ContentResolver.takePersistableUriPermission</code>.
                    </p>
                  </div>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800 flex gap-4">
                  <div className="w-8 h-8 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center flex-shrink-0 font-bold">
                    3
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-white">Safe .mcworld Import Fallback</h3>
                    <p className="text-xs text-gray-300 mt-1 leading-relaxed">
                      If Android prevents direct write replacement into `Android/data`, GitSync stages the verified `.mcworld` file and triggers an <code>Intent.ACTION_VIEW</code> with MIME type <code>application/mcworld</code>, allowing Minecraft to import the world natively.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'apk' && (
          <div className="space-y-6">
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6">
              <h2 className="text-lg font-bold text-white mb-2">Build app-debug.apk via GitHub Actions</h2>
              <p className="text-xs text-gray-400 mb-6">
                The repository is fully configured with Gradle, Android Gradle Plugin 8.5.2, and GitHub Actions.
              </p>

              <div className="space-y-4">
                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-mono font-bold text-emerald-400">Step 1: Push Repository to GitHub</span>
                    <button
                      onClick={() => {
                        navigator.clipboard.writeText('git init\ngit add .\ngit commit -m "Initial commit"\ngit push -u origin main');
                      }}
                      className="text-gray-400 hover:text-white"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                  </div>
                  <pre className="bg-[#101314] p-3 rounded text-xs font-mono text-gray-300 overflow-x-auto">
{`git init
git add .
git commit -m "Minecraft GitSync native Android project"
git branch -M main
git remote add origin https://github.com/<your-username>/minecraft-gitsync.git
git push -u origin main`}
                  </pre>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <span className="text-xs font-mono font-bold text-emerald-400">
                    Step 2: Automated CI/CD Workflow (.github/workflows/build-apk.yml)
                  </span>
                  <p className="text-xs text-gray-300 mt-2 leading-relaxed">
                    Upon push, GitHub Actions automatically spins up an Ubuntu runner, configures JDK 17, runs unit tests
                    (`Sha256ChecksumTest`, `DivergenceDetectorTest`, `ChunkProtocolTest`), builds the debug APK via
                    <code>./gradlew assembleDebug</code>, and publishes <strong>app-debug.apk</strong> as a downloadable build artifact.
                  </p>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <span className="text-xs font-mono font-bold text-emerald-400">Step 3: Direct Local Build (Android Studio)</span>
                  <pre className="bg-[#101314] p-3 rounded text-xs font-mono text-gray-300 mt-2 overflow-x-auto">
{`# Open project in Android Studio or run directly via terminal:
./gradlew assembleDebug

# Output APK path:
app/build/outputs/apk/debug/app-debug.apk`}
                  </pre>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-gray-800/80 bg-[#14171a] px-6 py-4 text-center text-xs text-gray-500">
        Minecraft GitSync Native Android Project • Peer-to-Peer Bedrock Synchronization • WebRTC DataChannels • Room • SAF
      </footer>
    </div>
  );
}
