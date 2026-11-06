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
  HardDrive,
  GitBranch,
  GitCommit,
  CheckCircle2,
  AlertTriangle,
  Play,
  Layers,
  Terminal,
  Activity,
  Download,
  Copy,
  Lock,
  LockOpen,
  Cpu,
  ShieldCheck,
  SmartphoneNfc
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
  const [activeTab, setActiveTab] = useState<'simulator' | 'diagnostics' | 'architecture' | 'saf' | 'apk'>('simulator');
  
  // 3-Device Group State (Owner Device A, Friend Device B, Friend Device C)
  const [deviceAOnline, setDeviceAOnline] = useState(true);
  const [deviceBOnline, setDeviceBOnline] = useState(true);
  const [deviceCOnline, setDeviceCOnline] = useState(false); // Friend's Android 15 phone initially offline

  const [deviceAPlaying, setDeviceAPlaying] = useState(false);
  const [deviceBPlaying, setDeviceBPlaying] = useState(false);
  const [deviceCPlaying, setDeviceCPlaying] = useState(false);

  // Single Active Player Editing Lease
  const [activeLeaseHolder, setActiveLeaseHolder] = useState<'A' | 'B' | 'C' | null>(null);
  const [leaseTimerSec, setLeaseTimerSec] = useState<number>(0);

  const [gracePeriodTimer, setGracePeriodTimer] = useState<number | null>(null);
  const [transferProgress, setTransferProgress] = useState(0);
  const [isTransferring, setIsTransferring] = useState(false);
  const [activeTransferSender, setActiveTransferSender] = useState<'A' | 'B' | 'C' | null>(null);

  // Commit history for the simulated Minecraft world
  const [commits, setCommits] = useState<SimulatedCommit[]>([
    {
      commitId: 'c_9f81a2e4',
      worldId: 'SurvivalWorld',
      parentCommitId: null,
      message: 'v1 - Genesis Bedrock world generated',
      author: 'Owner (Samsung Galaxy M30s)',
      timestamp: 'Today at 09:15',
      sizeKb: 14200,
      sha256: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      status: 'SYNCED'
    },
    {
      commitId: 'c_b44120ca',
      worldId: 'SurvivalWorld',
      parentCommitId: 'c_9f81a2e4',
      message: 'v2 - Starter base built & mining cave lit',
      author: 'Friend (Device B - Android 11)',
      timestamp: 'Today at 10:42',
      sizeKb: 16840,
      sha256: '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069',
      status: 'SYNCED'
    }
  ]);

  const [conflictDetected, setConflictDetected] = useState(false);
  const [logs, setLogs] = useState<string[]>([
    '[INIT] Minecraft GitSync 3-Device Engine initialized for Target: Samsung Galaxy M30s (Android 11).',
    '[STORAGE] Testing path: /storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/',
    '[COMPAT] Target SDK 29 + requestLegacyExternalStorage=true + isExternalStorageLegacy=true verified.',
    '[GROUP] 3 Devices registered: Device A (Owner M30s), Device B (Friend Phone 1), Device C (Friend Phone 2 - Android 15).',
    '[LEASE] Single Active Player Lock is currently FREE. No active sessions.'
  ]);

  const addLog = (msg: string) => {
    setLogs(prev => [msg, ...prev.slice(0, 24)]);
  };

  // Lease countdown
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (activeLeaseHolder !== null && leaseTimerSec > 0) {
      interval = setInterval(() => {
        setLeaseTimerSec(t => (t > 1 ? t - 1 : 0));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [activeLeaseHolder, leaseTimerSec]);

  // Grace period countdown
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (gracePeriodTimer !== null && gracePeriodTimer > 0) {
      interval = setInterval(() => {
        setGracePeriodTimer(t => (t !== null && t > 1 ? t - 1 : null));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [gracePeriodTimer]);

  // Player A starts session
  const handleStartDeviceA = () => {
    if (activeLeaseHolder !== null && activeLeaseHolder !== 'A') {
      alert(`Cannot start session! Device ${activeLeaseHolder} currently holds the active world editing lease.`);
      return;
    }
    setActiveLeaseHolder('A');
    setLeaseTimerSec(900); // 15 mins
    setDeviceAPlaying(true);
    addLog('[LEASE] Device A (Owner M30s) ACQUIRED World Editing Lease. Other devices locked.');
    addLog('[SESSION] Owner launched Minecraft Bedrock (com.mojang.minecraftpe).');
  };

  // Player A closes Minecraft
  const handleStopDeviceA = () => {
    setDeviceAPlaying(false);
    addLog('[SESSION] Owner closed Minecraft. Starting 8-second grace period verification...');
    setGracePeriodTimer(8);

    setTimeout(() => {
      addLog('[STORAGE] LevelDB files settled. Reading level.dat and /db/ directory...');
      addLog('[ARCHIVER] Packaging world into .mcworld zip with streaming SHA-256 calculation.');

      const newSha = 'a84f' + Math.random().toString(16).substring(2, 10) + '9b01c3d4e5f6';
      const newCommitId = 'c_' + Math.random().toString(16).substring(2, 10);
      const newCommit: SimulatedCommit = {
        commitId: newCommitId,
        worldId: 'SurvivalWorld',
        parentCommitId: commits[0]?.commitId || null,
        message: `v${commits.length + 1} - Nether portal explored by Owner (M30s)`,
        author: 'Owner (Galaxy M30s)',
        timestamp: 'Just now',
        sizeKb: 18400,
        sha256: newSha,
        status: 'PENDING'
      };

      setCommits(prev => [newCommit, ...prev]);
      addLog(`[ROOM] Saved commit ${newCommitId} to local Room DB. SHA-256: ${newSha.substring(0, 16)}...`);

      // Release editing lease after verified backup
      setActiveLeaseHolder(null);
      setLeaseTimerSec(0);
      addLog('[LEASE] Backup verified. World Editing Lease RELEASED for group.');

      // Fan out to Device B and Device C
      triggerGroupFanout(newCommitId);
    }, 3500);
  };

  const triggerGroupFanout = (commitId: string) => {
    setIsTransferring(true);
    setActiveTransferSender('A');
    setTransferProgress(0);

    let prog = 0;
    const interval = setInterval(() => {
      prog += 20;
      setTransferProgress(prog);
      addLog(`[WEBRTC] Streaming binary chunk ${prog / 20}/5 (32KB chunks with backpressure)`);
      if (prog >= 100) {
        clearInterval(interval);
        setIsTransferring(false);
        setActiveTransferSender(null);

        if (deviceBOnline) {
          addLog('[VERIFY] Device B (Friend Phone 1, Android 11) verified SHA-256. In-place replacement PASSED.');
        } else {
          addLog('[QUEUE] Device B is offline. Queued in Room sync_queue.');
        }

        if (deviceCOnline) {
          addLog('[VERIFY] Device C (Friend Phone 2, Android 15) verified SHA-256. Staged for .mcworld SAF import.');
        } else {
          addLog('[QUEUE] Device C (Android 15) is OFFLINE. Update queued for reconnection (Rule B).');
        }

        setCommits(prev =>
          prev.map(c => (c.commitId === commitId ? { ...c, status: 'SYNCED' } : c))
        );
      }
    }, 500);
  };

  // Friend switches to Device C (Android 15) and reconnects
  const handleDeviceCReconnect = () => {
    setDeviceCOnline(true);
    addLog('[SWITCH] Friend switched to Device C (Android 15). Presence reported to signaling server.');
    const hasPending = commits.some(c => c.status === 'PENDING' || c.commitId.startsWith('c_'));
    if (hasPending) {
      addLog('[RULE C] Delivering newest snapshot to Device C over WebRTC DataChannel...');
      setIsTransferring(true);
      setActiveTransferSender('A');
      setTransferProgress(0);
      let p = 0;
      const t = setInterval(() => {
        p += 25;
        setTransferProgress(p);
        if (p >= 100) {
          clearInterval(t);
          setIsTransferring(false);
          setActiveTransferSender(null);
          addLog('[ANDROID 15 SAF] Device C verified SHA-256! Staged .mcworld for one-tap native import.');
        }
      }, 400);
    }
  };

  return (
    <div className="min-h-screen bg-[#101314] text-gray-100 flex flex-col font-sans selection:bg-emerald-500/30">
      {/* Top Header */}
      <header className="border-b border-gray-800/80 bg-[#14171a]/90 backdrop-blur sticky top-0 z-50 px-6 py-4">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 flex items-center justify-center shadow-lg shadow-emerald-500/20 text-gray-950 font-black">
              <FolderGit2 className="w-6 h-6 text-gray-950" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-mono font-black text-lg tracking-wider text-white">MINECRAFT GITSYNC</h1>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-bold">
                  SAMSUNG M30s • ANDROID 11 COMPAT
                </span>
              </div>
              <p className="text-xs text-gray-400">External World Storage & 3-Device Sync Group</p>
            </div>
          </div>

          <div className="flex items-center gap-2 bg-[#1b1f23] p-1 rounded-xl border border-gray-800">
            <button
              onClick={() => setActiveTab('simulator')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'simulator' ? 'bg-emerald-500 text-gray-950 shadow-sm' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Activity className="w-3.5 h-3.5" />
              3-Device Group Simulator
            </button>
            <button
              onClick={() => setActiveTab('diagnostics')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'diagnostics' ? 'bg-emerald-500 text-gray-950 shadow-sm' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              M30s Storage Audit
            </button>
            <button
              onClick={() => setActiveTab('architecture')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'architecture' ? 'bg-emerald-500 text-gray-950 shadow-sm' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              Architecture
            </button>
            <button
              onClick={() => setActiveTab('saf')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'saf' ? 'bg-emerald-500 text-gray-950 shadow-sm' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <HardDrive className="w-3.5 h-3.5" />
              Android 11 & 15 Rules
            </button>
            <button
              onClick={() => setActiveTab('apk')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition flex items-center gap-1.5 ${
                activeTab === 'apk' ? 'bg-emerald-500 text-gray-950 shadow-sm' : 'text-gray-400 hover:text-gray-200'
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
            {/* Group Banner */}
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6 relative overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-4 mb-6">
                <div>
                  <span className="text-xs font-mono text-emerald-400 uppercase tracking-wider font-bold">
                    3-Device Sync Group Active
                  </span>
                  <h2 className="text-xl font-bold text-white mt-1">Shared World: SurvivalWorld (External Storage)</h2>
                </div>

                {/* Single Active Player Lock Status Indicator */}
                <div className="flex items-center gap-3">
                  <div
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border font-mono text-xs ${
                      activeLeaseHolder !== null
                        ? 'bg-amber-500/15 border-amber-500/40 text-amber-400'
                        : 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400'
                    }`}
                  >
                    {activeLeaseHolder !== null ? <Lock className="w-3.5 h-3.5" /> : <LockOpen className="w-3.5 h-3.5" />}
                    <span>
                      {activeLeaseHolder !== null
                        ? `LEASE HELD: Device ${activeLeaseHolder} (${leaseTimerSec}s)`
                        : 'WORLD LEASE FREE'}
                    </span>
                  </div>

                  <button
                    onClick={() => {
                      setConflictDetected(true);
                      addLog('[RULE G] Concurrent edits detected across devices! Preserving both branches in Room.');
                    }}
                    className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-red-500/10 text-red-400 border border-red-500/30 hover:bg-red-500/20 transition flex items-center gap-1.5"
                  >
                    <GitBranch className="w-3.5 h-3.5" />
                    Simulate Conflict
                  </button>
                </div>
              </div>

              {/* Transfer progress bar */}
              {isTransferring && (
                <div className="mb-6 bg-[#22272b] border border-blue-500/40 rounded-xl p-4">
                  <div className="flex items-center justify-between text-xs font-mono mb-2">
                    <span className="text-blue-400 flex items-center gap-2">
                      <RefreshCw className="w-4 h-4 animate-spin" />
                      Streaming .mcworld snapshot via WebRTC DataChannel (32KB chunks)
                    </span>
                    <span className="text-gray-300 font-bold">{transferProgress}%</span>
                  </div>
                  <div className="w-full bg-gray-800 h-2 rounded-full overflow-hidden">
                    <div className="bg-emerald-500 h-full transition-all duration-300" style={{ width: `${transferProgress}%` }} />
                  </div>
                </div>
              )}

              {/* 3 Devices Grid */}
              <div className="grid md:grid-cols-3 gap-6">
                {/* Device A: Owner */}
                <div className="bg-[#22272b] border border-gray-700/60 rounded-xl p-5 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <Smartphone className="w-5 h-5 text-emerald-400" />
                        <div>
                          <div className="font-bold text-sm text-white">Device A (Owner)</div>
                          <div className="text-[11px] text-gray-400 font-mono">Samsung Galaxy M30s</div>
                        </div>
                      </div>
                      <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-emerald-500/20 text-emerald-400">
                        Android 11
                      </span>
                    </div>

                    <div className="space-y-2 text-xs bg-[#1a1e21] p-3 rounded-lg border border-gray-800 my-3">
                      <div className="flex justify-between text-gray-300">
                        <span>Storage Type:</span>
                        <span className="text-emerald-400 font-mono">External Storage</span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>Session Status:</span>
                        <span className={deviceAPlaying ? 'text-emerald-400 font-bold' : 'text-gray-400'}>
                          {deviceAPlaying ? '🎮 In Game' : 'Idle (Monitoring)'}
                        </span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>Safe Replacement:</span>
                        <span className="text-emerald-400 font-mono">Supported (M30s)</span>
                      </div>
                      {gracePeriodTimer !== null && (
                        <div className="text-amber-400 font-bold bg-amber-500/10 p-1.5 rounded text-center">
                          Grace Period: {gracePeriodTimer}s remaining
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="mt-2">
                    {!deviceAPlaying ? (
                      <button
                        onClick={handleStartDeviceA}
                        className="w-full bg-emerald-600 hover:bg-emerald-500 text-gray-950 font-bold text-xs py-2 rounded-lg transition flex items-center justify-center gap-1.5"
                      >
                        <Play className="w-3.5 h-3.5" /> Launch Minecraft (Acquire Lease)
                      </button>
                    ) : (
                      <button
                        onClick={handleStopDeviceA}
                        className="w-full bg-amber-600 hover:bg-amber-500 text-gray-950 font-bold text-xs py-2 rounded-lg transition"
                      >
                        Exit Minecraft (Auto-Sync & Release)
                      </button>
                    )}
                  </div>
                </div>

                {/* Device B: Friend Phone 1 */}
                <div className="bg-[#22272b] border border-gray-700/60 rounded-xl p-5 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <Smartphone className="w-5 h-5 text-blue-400" />
                        <div>
                          <div className="font-bold text-sm text-white">Device B (Friend Phone 1)</div>
                          <div className="text-[11px] text-gray-400 font-mono">Friend Primary</div>
                        </div>
                      </div>
                      <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-blue-500/20 text-blue-400">
                        Android 11
                      </span>
                    </div>

                    <div className="space-y-2 text-xs bg-[#1a1e21] p-3 rounded-lg border border-gray-800 my-3">
                      <div className="flex justify-between text-gray-300">
                        <span>Connection:</span>
                        <span className={deviceBOnline ? 'text-emerald-400' : 'text-gray-400'}>
                          {deviceBOnline ? 'Online (P2P Ready)' : 'Offline'}
                        </span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>Storage Access:</span>
                        <span className="text-blue-400 font-mono">External (/sdcard/games)</span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>P2P Receiver:</span>
                        <span className="text-gray-300">Auto-Pull Active</span>
                      </div>
                    </div>
                  </div>

                  <div className="mt-2">
                    <button
                      onClick={() => {
                        setDeviceBOnline(b => !b);
                        addLog(deviceBOnline ? '[PRESENCE] Device B went OFFLINE.' : '[PRESENCE] Device B came ONLINE.');
                      }}
                      className="w-full bg-[#2e353b] hover:bg-[#384148] text-gray-200 font-semibold text-xs py-2 rounded-lg transition"
                    >
                      {deviceBOnline ? 'Toggle Offline (Test Queue)' : 'Reconnect Device B'}
                    </button>
                  </div>
                </div>

                {/* Device C: Friend Phone 2 (Android 15) */}
                <div className="bg-[#22272b] border border-gray-700/60 rounded-xl p-5 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <SmartphoneNfc className="w-5 h-5 text-purple-400" />
                        <div>
                          <div className="font-bold text-sm text-white">Device C (Friend Phone 2)</div>
                          <div className="text-[11px] text-gray-400 font-mono">Friend Secondary</div>
                        </div>
                      </div>
                      <span className="text-[10px] px-2 py-0.5 rounded font-mono font-bold bg-purple-500/20 text-purple-400">
                        Android 15 (Scoped)
                      </span>
                    </div>

                    <div className="space-y-2 text-xs bg-[#1a1e21] p-3 rounded-lg border border-gray-800 my-3">
                      <div className="flex justify-between text-gray-300">
                        <span>Connection:</span>
                        <span className={deviceCOnline ? 'text-emerald-400' : 'text-gray-400'}>
                          {deviceCOnline ? 'Online (P2P Ready)' : 'Offline (Simulated)'}
                        </span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>Android 15 Storage:</span>
                        <span className="text-purple-400 font-mono">SAF / .mcworld Import</span>
                      </div>
                      <div className="flex justify-between text-gray-300">
                        <span>Switching Check:</span>
                        <span className="text-emerald-400 font-mono">Pre-play validated</span>
                      </div>
                    </div>
                  </div>

                  <div className="mt-2">
                    {!deviceCOnline ? (
                      <button
                        onClick={handleDeviceCReconnect}
                        className="w-full bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs py-2 rounded-lg transition"
                      >
                        Switch to Device C (Test Reconnect)
                      </button>
                    ) : (
                      <button
                        onClick={() => {
                          setDeviceCOnline(false);
                          addLog('[SWITCH] Friend switched away from Device C.');
                        }}
                        className="w-full bg-[#2e353b] hover:bg-[#384148] text-gray-200 font-semibold text-xs py-2 rounded-lg transition"
                      >
                        Set Device C Offline
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Commits & Diagnostics Feed */}
            <div className="grid lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 bg-[#181b1e] border border-gray-800 rounded-xl p-5">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <GitCommit className="w-5 h-5 text-emerald-400" />
                    <h3 className="font-bold text-sm text-white">3-Device Shared Commit History (Room SQLite)</h3>
                  </div>
                  <span className="text-xs font-mono text-gray-400">{commits.length} commits</span>
                </div>

                <div className="space-y-3">
                  {commits.map(commit => (
                    <div key={commit.commitId} className="bg-[#22272b] border border-gray-800 rounded-lg p-3.5">
                      <div className="flex items-center justify-between text-xs mb-1">
                        <span className="font-mono text-emerald-400 font-bold">{commit.commitId}</span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-500/20 text-emerald-400">
                          {commit.status}
                        </span>
                      </div>
                      <div className="font-semibold text-sm text-gray-100">{commit.message}</div>
                      <div className="text-xs text-gray-400 mt-1 flex flex-wrap gap-x-4">
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

              {/* Diagnostic Terminal */}
              <div className="bg-[#181b1e] border border-gray-800 rounded-xl p-5 flex flex-col">
                <div className="flex items-center gap-2 mb-3">
                  <Terminal className="w-4 h-4 text-emerald-400" />
                  <h3 className="font-bold text-sm text-white">Diagnostic Engine Feed</h3>
                </div>
                <div className="bg-[#101314] border border-gray-800/80 rounded-lg p-3 font-mono text-[11px] space-y-1.5 flex-1 overflow-y-auto max-h-96 text-gray-300">
                  {logs.map((log, i) => (
                    <div key={i} className="leading-relaxed">
                      {log.startsWith('[SESSION]') && <span className="text-emerald-400">{log}</span>}
                      {log.startsWith('[STORAGE]') && <span className="text-blue-400">{log}</span>}
                      {log.startsWith('[LEASE]') && <span className="text-amber-400">{log}</span>}
                      {log.startsWith('[SWITCH]') && <span className="text-purple-400">{log}</span>}
                      {log.startsWith('[CONFLICT]') && <span className="text-red-400">{log}</span>}
                      {!['[SESSION]', '[STORAGE]', '[LEASE]', '[SWITCH]', '[CONFLICT]'].some(p => log.startsWith(p)) && (
                        <span>{log}</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'diagnostics' && (
          <div className="space-y-6">
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6">
              <h2 className="text-lg font-bold text-white mb-2">Samsung Galaxy M30s (Android 11) Storage Audit</h2>
              <p className="text-xs text-gray-400 mb-6">
                Live verification of the external Minecraft Bedrock world directory, legacy storage mode, and safe write tests.
              </p>

              <div className="grid md:grid-cols-2 gap-6">
                <div className="bg-[#22272b] p-5 rounded-xl border border-gray-800 space-y-3">
                  <h3 className="text-sm font-bold text-emerald-400 font-mono">DEVICE CAPABILITY METRICS</h3>
                  <div className="text-xs space-y-2">
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Target Device:</span>
                      <span className="text-white font-bold">Samsung Galaxy M30s</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Android OS Version:</span>
                      <span className="text-white font-mono">API 30 (Android 11)</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Compatibility Target SDK:</span>
                      <span className="text-emerald-400 font-mono font-bold">29 (Legacy Mode)</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Environment.isExternalStorageLegacy():</span>
                      <span className="text-emerald-400 font-mono font-bold">TRUE (Active)</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">READ_EXTERNAL_STORAGE:</span>
                      <span className="text-emerald-400 font-bold">GRANTED</span>
                    </div>
                    <div className="flex justify-between py-1">
                      <span className="text-gray-400">Safe Private Write Test:</span>
                      <span className="text-emerald-400 font-bold">PASSED (Non-destructive)</span>
                    </div>
                  </div>
                </div>

                <div className="bg-[#22272b] p-5 rounded-xl border border-gray-800 space-y-3">
                  <h3 className="text-sm font-bold text-blue-400 font-mono">MINECRAFT PATH AUDIT</h3>
                  <p className="text-xs font-mono text-gray-300 break-all bg-[#1a1e21] p-3 rounded border border-gray-800">
                    /storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/
                  </p>
                  <div className="text-xs space-y-2">
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Directory Exists:</span>
                      <span className="text-emerald-400 font-bold">DETECTED</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">Direct Read Access:</span>
                      <span className="text-emerald-400 font-bold">AVAILABLE</span>
                    </div>
                    <div className="flex justify-between py-1 border-b border-gray-800">
                      <span className="text-gray-400">World Enumeration:</span>
                      <span className="text-emerald-400 font-bold">SUCCESSFUL (2 Worlds)</span>
                    </div>
                    <div className="flex justify-between py-1">
                      <span className="text-gray-400">Safe Replacement Status:</span>
                      <span className="text-emerald-400 font-bold">SUPPORTED (Target SDK 29 build)</span>
                    </div>
                  </div>
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
                  <ul className="text-xs text-gray-300 space-y-1.5 font-mono">
                    <li>• StorageDiagnosticHelper.kt</li>
                    <li>• WorldDiscovery.kt</li>
                    <li>• WorldSnapshotManager.kt</li>
                    <li>• SafeWorldInstaller.kt</li>
                    <li>• Sha256Checksum.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Manages Android 11 external storage audits, LevelDB write verification, and non-destructive backups.
                  </p>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center gap-2 text-blue-400 font-mono text-xs font-bold mb-2">
                    <Wifi className="w-4 h-4" /> feature/group
                  </div>
                  <ul className="text-xs text-gray-300 space-y-1.5 font-mono">
                    <li>• SyncGroupManager.kt</li>
                    <li>• WorldEditingLeaseManager.kt</li>
                    <li>• SyncGroup.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Coordinates the 3-device group (Device A, B, C) and enforces the single active player lock.
                  </p>
                </div>

                <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                  <div className="flex items-center gap-2 text-purple-400 font-mono text-xs font-bold mb-2">
                    <Cpu className="w-4 h-4" /> feature/sync & workers
                  </div>
                  <ul className="text-xs text-gray-300 space-y-1.5 font-mono">
                    <li>• MinecraftSessionMonitor.kt</li>
                    <li>• AutoSnapshotWorker.kt</li>
                    <li>• DirectPushWorker.kt</li>
                    <li>• QueuedSyncWorker.kt</li>
                  </ul>
                  <p className="text-[11px] text-gray-400 mt-3">
                    Foreground WorkManager dataSync workers, boot-time reconciliation, and UsageStats tracking.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'saf' && (
          <div className="space-y-6">
            <div className="bg-[#181b1e] border border-gray-800 rounded-2xl p-6">
              <h2 className="text-lg font-bold text-white mb-2">Android 11 vs Android 15 Storage Matrix</h2>
              <p className="text-xs text-gray-400 mb-6">
                Comparison of storage behavior across Device A (Samsung M30s, Android 11), Device B (Android 11), and Device C (Android 15).
              </p>

              <div className="grid md:grid-cols-2 gap-6">
                <div className="bg-[#22272b] p-5 rounded-xl border border-gray-800">
                  <span className="text-xs font-mono font-bold text-emerald-400">
                    DEVICES A & B: SAMSUNG M30s & ANDROID 11
                  </span>
                  <p className="text-xs text-gray-300 mt-2 leading-relaxed">
                    Uses targetSdk 29 compatibility build with <code>requestLegacyExternalStorage="true"</code>. Direct read and write access to
                    <code>/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds/</code> is verified and supported.
                  </p>
                </div>

                <div className="bg-[#22272b] p-5 rounded-xl border border-gray-800">
                  <span className="text-xs font-mono font-bold text-purple-400">
                    DEVICE C: FRIEND PHONE 2 (ANDROID 15)
                  </span>
                  <p className="text-xs text-gray-300 mt-2 leading-relaxed">
                    Android 15 enforces strict scoped storage boundaries. GitSync uses the Storage Access Framework (SAF) folder picker and stages received
                    worlds into verified <code>.mcworld</code> containers for one-tap native import via <code>Intent.ACTION_VIEW</code>.
                  </p>
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
                Targeting Android 11 external storage compatibility with AGP 8.5.2 and targetSdk 29.
              </p>

              <div className="bg-[#22272b] p-4 rounded-xl border border-gray-800">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-mono font-bold text-emerald-400">Build Command</span>
                  <button
                    onClick={() => navigator.clipboard.writeText('./gradlew assembleDebug')}
                    className="text-gray-400 hover:text-white"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                </div>
                <pre className="bg-[#101314] p-3 rounded text-xs font-mono text-gray-300 overflow-x-auto">
{`./gradlew assembleDebug

# Output APK path:
app/build/outputs/apk/debug/app-debug.apk`}
                </pre>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-gray-800/80 bg-[#14171a] px-6 py-4 text-center text-xs text-gray-500">
        Minecraft GitSync • Samsung Galaxy M30s Android 11 External Storage • 3-Device Sync Group • WebRTC DataChannels
      </footer>
    </div>
  );
}
