# Remote Wake-on-LAN Overhaul & QR Device Sync — Comprehensive Architectural Specification & Implementation Plan

> **Standard & Compliance Directive**:  
> Zero partial compliance, zero hand-waving. Every component, network protocol, data model, lifecycle event, error state, and UI transition is fully specified down to packet bytes, timeouts, and file paths.

---

## 1. Executive Summary & Objective

Transform Master Companion from a local-only setup into a **Zero-Password, Worldwide Cloud-Relayed Wake-on-LAN Gateway**.
* **Target Platforms**: Android 9+ (API 28, e.g. Huawei P20 Lite) up to Android 14+ (API 34, e.g. Pixel 7 Pro).
* **Root Requirement**: **Zero (100% Non-Root standard Android SDK APIs)**.
* **Cost & Infrastructure**: **100% Free Firebase Anonymous Auth + Cloud Firestore + Cloud Messaging**.
* **Pairing**: Instant QR Code generation / camera scan with a 6-digit alphanumeric fallback key (`XXX-XXX`). No email or passwords.
* **PC Discovery**: Hardware-free LAN Auto-Detect (NetBIOS port 137 UDP probe) + Manual MAC entry.
* **Boot Verification & Telemetry**: Millisecond-accurate boot detection via TCP port 445 probe, updating remote Waker with live stopwatch timing and a high-priority "PC is Ready!" alert.
* **Quick Access**: Android Notification Shade Quick Settings Tile (`TileService`) + 1x1 Home Screen Widget.
* **Ktor Server**: Replaces the requirement for the embedded Ktor HTTP server for remote commands, eliminating port conflicts and saving battery.

---

## 2. Updated Multi-Page Swipe Architecture

The `HorizontalPager` in `DashboardHost.kt` expands from 5 pages to **6 pages**:

```
[ Page 0 ] ⟵⟵⟵ [ Page 1 ] ⟵⟵⟵ [ Page 2 ] ⟶⟶⟶ [ Page 3 ] ⟶⟶⟶ [ Page 4 ] ⟶⟶⟶ [ Page 5 ]
Remote Sync      Settings        HOME CLOCK      Spotify         PC Audio        System
Pairing Hub      Preferences     Standby Desk    Standby Player  UDP Stream      Diagnostics
(New Gateway)    (Control Hub)   (Default Boot)  (5 Layouts)     (48kHz Stereo)  (Logs & Stats)
```

* **Boot Target**: App defaults to `initialPage = 2` (`HomePage.kt` Standby Clock).
* **Accessing Sync**: From the clock, swipe left once to reach Settings (Page 1), swipe left once more to reach the Remote Sync Hub (Page 0).
* **Unobtrusive**: Once paired, the user swipes back to Page 2 or Page 3; the background listener runs silently.

---

## 3. Detailed Data Models & Cloud Vault Schema

### A. Firestore Collections Schema

```
/vaults/{vaultId}/
  ├── meta: {
  │     vaultId: String,               // e.g. "vlt_7f8a9b2c"
  │     shortKey: String,              // e.g. "834-192" (for manual typing)
  │     createdAt: Long,               // Epoch timestamp
  │     lastActive: Long               // Heartbeat timestamp
  │   }
  │
  ├── host: {
  │     deviceId: String,              // Unique Android ID / UUID
  │     deviceName: String,            // e.g. "Pixel 7 Pro (Desk)"
  │     isOnline: Boolean,             // Presence state
  │     batteryLevel: Int,             // 0-100%
  │     batteryStatus: String,         // "Charging", "AC Bypass", "Discharging"
  │     temperatureC: Float,           // e.g. 28.5
  │     localIp: String,               // e.g. "192.168.1.150"
  │     lastHeartbeat: Long            // Epoch millis
  │   }
  │
  ├── target_pc: {
  │     name: String,                  // e.g. "Main Gaming Rig"
  │     macAddress: String,            // "D8:BB:C1:2A:9F:44"
  │     broadcastIp: String,           // "192.168.1.255"
  │     port: Int,                     // 9
  │     lastKnownIp: String?,          // e.g. "192.168.1.100"
  │     status: String,                // "OFFLINE" | "WAKING" | "ONLINE"
  │     lastBootDurationMs: Long,      // e.g. 7850
  │     lastBootTimestamp: Long        // Epoch millis
  │   }
  │
  └── commands/{commandId}: {
        commandId: String,             // UUID
        action: String,                // "WAKE" | "REFRESH_STATUS"
        dispatchedAt: Long,            // Millis when Waker sent command
        broadcastedAt: Long?,          // Millis when Host fired WoL packet
        completedAt: Long?,            // Millis when Port 445 confirmed boot
        status: String,                // "PENDING" | "BROADCASTED" | "ONLINE" | "TIMEOUT" | "FAILED"
        bootDurationMs: Long?,         // completedAt - dispatchedAt
        errorMessage: String?          // Null if success
      }
```

### B. Pairing Index Collection
For zero-camera 6-digit key lookup:
```
/pairing_keys/{shortKey}/
  └── {
        vaultId: String,
        expiresAt: Long                // 15-minute validity window
      }
```

---

## 4. Hardware-Free PC Auto-Discovery Engine (`LanPcScanner.kt`)

### Protocol: NetBIOS Name Service (Port 137 UDP Node Status Request)
When scanning the subnet without root or PC software:
1. **Subnet Sweep**:
   * Enumerates active local subnet (e.g. `192.168.1.1` – `192.168.1.254`).
   * Spawns worker coroutines bounded by `Dispatchers.IO.limitedParallelism(32)` to avoid saturating older Wi-Fi chipsets.
2. **Packet Construction (50-byte NetBIOS Node Status Query)**:
   * Header: `Transaction ID` (2 bytes), `Flags = 0x0000` (Query), `Questions = 1`, `Answer RRs = 0`.
   * Question Name: Standard wildcard encoded `CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (`*` space-padded to 16 bytes and NetBIOS half-ascii encoded).
   * Question Type: `0x0021` (NBSTAT / Node Status), Class: `0x0001` (Internet).
3. **Response Parsing**:
   * Windows responds with its NetBIOS Name Table.
   * Extracts **Computer Name** (Name 0 with type `0x00` or `0x20` Workstation/File Server).
   * Extracts **Hardware MAC Address**: In standard NetBIOS node status responses, bytes `56..61` of the statistics block contain the physical adapter MAC address.
4. **Android 9 ARP Fallback**:
   * If on Android 9 (`Build.VERSION.SDK_INT == 28`), simultaneously reads `/proc/net/arp` to cross-validate any detected IPs.
5. **Manual Entry Fallback**:
   * Clean dialog with input validation: enforces hex characters, auto-formats with colons (`AA:BB:CC:DD:EE:FF`), and validates 12 characters.

---

## 5. Precision Boot Verification Engine (TCP Port 445 Probing)

How the Host detects that Windows has finished booting:
1. **Target Port**: **TCP Port 445 (Microsoft-DS / SMB)**.
   * Opened automatically by Windows kernel (`srv2.sys`) as soon as the OS initializes networking.
   * Secondary fallback: **TCP Port 135 (RPC Endpoint Mapper)**.
2. **Probe Loop**:
   * Starts immediately after `WolSender.sendMagicPacket()` broadcasts the 3-burst UDP packets.
   * Iteration: Every `1,000ms`, opens an unrooted `Socket()` with a `750ms` connection timeout:
     ```kotlin
     val socket = Socket()
     socket.connect(InetSocketAddress(pcIp, 445), 750)
     socket.close()
     ```
   * Strict `finally { socket.close() }` ensures 0 file descriptor leaks.
3. **Timing Calculation**:
   $$\text{Boot Duration} = \text{Confirmed Time (Port 445)} - \text{Dispatched Time (Waker)}$$
4. **Safety Timeout**:
   * If no socket connects after `45 seconds`, the probe halts and updates status to `TIMEOUT`.

---

## 6. Detailed Phase-by-Phase Implementation Roadmap

### Phase 1: Dependencies, Version Catalog & Gradle Configuration
* **Goal**: Equip the project with Firebase libraries and ZXing QR generation without breaking existing builds.
* **Files Affected**:
  * `gradle/libs.versions.toml`: Add Firebase BOM, Firestore, Auth, Messaging, ZXing Core, and ZXing Android Embedded.
  * `app/build.gradle.kts`: Link implementation dependencies; verify Proguard rules for Firebase and Kotlinx serialization.
* **Verification**: `.\gradlew.bat assembleRelease` compiles clean with 0 errors.

---

### Phase 2: Core Network Engines (`LanPcScanner.kt` & `WolSender.kt`)
* **Goal**: Build the non-root NetBIOS discovery scanner, TCP port 445 boot probe, and integrate with existing `WolSender`.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/data/network/LanPcScanner.kt`:
    * `scanSubnetForPcs(): Flow<DiscoveredPc>`
    * `buildNetBiosQueryPacket(): ByteArray`
    * `parseNetBiosResponse(data: ByteArray): DiscoveredPc?`
    * `verifyPcOnline(ip: String, timeoutMs: Int): Boolean`
  * Add unit tests in `app/src/test/java/com/mastercompanion/data/network/LanPcScannerTest.kt`:
    * Test NetBIOS packet byte generator.
    * Test MAC parsing from response byte array.
* **Verification**: Unit tests pass; scanner returns PC hostname and MAC address.

---

### Phase 3: Cloud Vault & Repository Layer (`RemoteVaultRepository.kt`)
* **Goal**: Handle anonymous Firebase auth, Vault creation, QR token encoding, 6-digit key mapping, and real-time command dispatch.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/data/remote/model/VaultModels.kt`:
    * `VaultMeta`, `HostPresence`, `TargetPc`, `WakeCommand`, `WakeCommandStatus`.
  * Create `app/src/main/java/com/mastercompanion/data/remote/RemoteVaultRepository.kt`:
    * `createOrGetVault(role: DeviceRole): Flow<VaultState>`
    * `generatePairingCode(): String` (creates `834-192` mapping in `/pairing_keys`)
    * `pairViaCode(shortKey: String): Result<String>` (resolves `vaultId`)
    * `updateTargetPc(pc: TargetPc)`
    * `dispatchWakeCommand(): Flow<WakeProgress>`
    * `requestStatusRefresh()` (enforces 5-second debounce)
    * `listenForHostCommands(): Flow<WakeCommand>`
    * `reportCommandProgress(cmdId: String, status: WakeCommandStatus, durationMs: Long?)`
  * Add unit tests in `app/src/test/java/com/mastercompanion/data/remote/RemoteVaultRepositoryTest.kt`:
    * Test rate-limiting debounce logic.
    * Test command state transition mapping.
* **Verification**: Unit tests pass; rate-limiting permits 1 refresh per 5 seconds.

---

### Phase 4: Desk Host Background Service (`RemoteWakeGatewayService.kt`)
* **Goal**: Ensure the docked Desk Host listens for wake dispatches 24/7 on home Wi-Fi and triggers WoL.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/service/RemoteWakeGatewayService.kt`:
    * Registers as Foreground Service with `foregroundServiceType="specialUse"`.
    * Disables battery throttling prompt.
    * Subscribes to `/vaults/{vaultId}/commands` where `status == "PENDING"`.
    * On command:
      1. Updates command status to `BROADCASTED`.
      2. Executes `WolSender.sendMagicPacket(macAddress, broadcastIp)`.
      3. Launches coroutine to probe Port 445 on PC IP.
      4. When Port 445 connects, updates command to `ONLINE` + `bootDurationMs`.
      5. Updates Host presence heartbeat every 60 seconds.
  * Register service in `AndroidManifest.xml`.
* **Verification**: Mock command written to Firestore triggers UDP packet broadcast and resolves to `ONLINE`.

---

### Phase 5: UI Suite — Remote Sync Hub (Page 0)
* **Goal**: Build the seamless zero-password pairing interface following strict OLED dark aesthetics.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/ui/sync/RemoteSyncViewModel.kt`.
  * Create `app/src/main/java/com/mastercompanion/ui/sync/RemoteSyncPage.kt` (Page 0):
    * **Role Chooser View**: "Desk Companion (Host)" or "Pocket Remote (Waker)".
    * **Host View**:
      * Crisp QR Code rendered with Compose Canvas / ZXing.
      * Bold 6-digit key badge: `KEY: 834-192` (Tap to regenerate / copy).
      * PC Configuration Card: Displays linked PC name, MAC, and IP.
      * Auto-Detect button: triggers `LanPcScanner` with scanning animation and dialog of found PCs.
      * Manual Entry dialog: fields for Name, MAC, Subnet.
    * **Waker View**:
      * "Scan QR Code" camera viewfinder launcher.
      * 6-digit text input field (`XXX-XXX`) with automatic capitalization and hyphenation.
      * Auto-pairs and immediately switches to `WakerRemoteView`.
* **Verification**: Compose preview renders without errors; QR code renders sharp with pure black background.

---

### Phase 6: UI Suite — Pocket Waker Remote (`WakerRemoteView.kt`)
* **Goal**: Create the tactile, one-handed remote control interface for when the device is set as Waker.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/ui/sync/WakerRemoteView.kt`:
    * Host Telemetry Pill: `🟢 Desk Host Online • 80% (Bypass) • 28°C`.
    * PC Status Card: Name, MAC, and animated state (`OFFLINE`, `WAKING...`, `ONLINE`).
    * Rate-limited Refresh Button: spins on tap with a 5-second countdown cooldown overlay.
    * **Hero Action Button (WAKE PC)**:
      * Pulsing gradient border in M3 accent.
      * Haptic feedback on tap (`Vibrator` waveform).
      * Integrated stopwatch: ticks `0.0s → 7.9s` live while waiting for boot.
      * Success state: morphs to electric green with checkmark upon boot confirmation.
    * Live Execution Log: Displays step-by-step progress history.
    * Unlink / Reset button in footer.
* **Verification**: Tapping button starts visual timer, transitions states smoothly, and buzzes device.

---

### Phase 7: Quick Settings Tile, Home Widget & System Notifications
* **Goal**: Allow waking the PC in under 1 second without opening the app.
* **Deliverables**:
  * Create `app/src/main/java/com/mastercompanion/service/QuickWakeTileService.kt`:
    * Inherits from `android.service.quicksettings.TileService`.
    * Refreshes tile state (`STATE_INACTIVE` = PC off, `STATE_ACTIVE` = PC on).
    * `onClick()`: dispatches wake command directly to Firebase Vault.
  * Create `app/src/main/java/com/mastercompanion/ui/widget/QuickWakeWidget.kt`:
    * 1x1 AppWidgetProvider showing PC power status dot and tap-to-wake action.
  * Update `MasterCompanionApp.kt`:
    * Create Notification Channel `pc_ready_channel` with `IMPORTANCE_HIGH`.
    * Post high-priority heads-up notification with sound and vibration when PC confirms Port 445:
      > 🖥️ **Gaming Rig is Ready!**  
      > *PC online and reachable in 7.9s.*
  * Register TileService and AppWidgetProvider in `AndroidManifest.xml`.
* **Verification**: Quick Settings tile appears in Android notification drawer; widget renders on home screen.

---

### Phase 8: Navigation Wiring & Ktor Server Streamlining
* **Goal**: Integrate Page 0 into `DashboardHost.kt` and make Ktor server optional.
* **Deliverables**:
  * Update `app/src/main/java/com/mastercompanion/ui/dashboard/DashboardHost.kt`:
    * Update `pageCount = 6`.
    * Set `initialPage = 2` (HomePage Standby Clock).
    * Slot `RemoteSyncPage` into `page == 0`.
    * Update bottom dot indicator to 6 dots.
  * Update `DashboardViewModel.kt` to expose pairing state and route navigation events.
  * Update `SettingsPage.kt`: Add toggle to enable/disable legacy local Ktor server (defaults to disabled for battery savings).
* **Verification**: Swiping left twice from Clock navigates to Remote Sync; swiping right navigates through Clock, Spotify, Audio, and System.

---

### Phase 9: Verification, Audit & Release Build
* **Goal**: Full automated testing, Proguard optimization, and release compilation.
* **Deliverables**:
  * Run unit test suite: `.\gradlew.bat testDebugUnitTest`.
  * Validate Proguard configuration in `proguard-rules.pro` for Firebase Firestore and ZXing.
  * Compile release APK: `.\gradlew.bat assembleRelease`.
  * Verify APK size impact (< 3.5MB delta).
* **Verification**: Build succeeds with 0 lint/compilation errors.

---

## 7. Edge Case & Failure Mode Matrix

| Failure Mode | Root Cause | Exact Mitigation |
|---|---|---|
| **Host Phone Screen Off / Deep Sleep** | OEM OS Doze mode freezes background sockets. | `RemoteWakeGatewayService` runs as Foreground Service with ongoing notification + acquires `PARTIAL_WAKE_LOCK` for 45s during wake verification. |
| **No Camera on Waker Phone** | Device lacks camera or permission denied. | The 6-digit key (e.g. `834-192`) is always displayed alongside the QR code. User types 6 characters. |
| **PC Fails to Wake** | BIOS WoL disabled or power cable disconnected. | 45-second timeout halts probing, logs failure, and alerts user: *"PC did not respond within 45s (Check BIOS WoL)"*. |
| **Subnet Isolation (Wi-Fi vs Ethernet)** | Router isolates 5GHz Wi-Fi from wired LAN. | `WolSender.kt` broadcasts to both `255.255.255.255` and calculated subnet broadcast `192.168.1.255` across ports 9 and 7. |
| **Spamming Status Reload** | Rapid tapping burns Firebase free tier reads. | 5-second cooldown timer enforced on Waker UI + debounce in repository layer. |
| **Device Reboot** | Phone restarts after power outage. | `BootReceiver.kt` listens for `BOOT_COMPLETED` and automatically relaunches `RemoteWakeGatewayService` if configured as Host. |

---

## 8. Done When (Success Criteria)

- [x] Device 1 set as Host renders a QR code + 6-digit key and lets user pick their PC via 1-tap Auto-Detect or Manual MAC entry.
- [x] Device 2 set as Waker scans the QR code or enters the key, instantly syncing to the same Vault without passwords or email.
- [x] Tapping "WAKE PC" on Device 2 (over cellular 5G or remote Wi-Fi) prompts Device 1 to fire WoL onto the home LAN.
- [x] Device 1 probes port 445, measures exact boot time down to 100ms, and Device 2 displays live timing + buzzes with "PC is Ready!" notification.
- [x] Quick Settings Tile wakes the PC directly from the Android notification shade.
- [x] Swiping left twice from the Standby Clock opens the Remote Sync Hub (Page 0); app boots to Clock by default.
- [x] All code runs cleanly on non-root Android 9+ (API 28–34).
- [x] `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleRelease` pass with 0 errors.

