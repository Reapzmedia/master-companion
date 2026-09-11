# ⚡ Master Companion

<div align="center">

**Smart Standby Dashboard, Battery Guard & PC Companion Bridge for Rooted Android Devices**

[![Release](https://img.shields.io/github/v/release/reapzmedia/master-companion?color=brightgreen&label=Latest%20Release&style=flat-square)](https://github.com/reapzmedia/master-companion/releases/latest)
[![Download APK](https://img.shields.io/badge/Download%20APK-Latest%20Release-blue?style=flat-square&logo=android)](https://github.com/reapzmedia/master-companion/releases/latest/download/app-release.apk)
[![Android](https://img.shields.io/badge/Android-9.0%20(API%2028)%20--%2014.0%20(API%2034)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06.00-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Hilt](https://img.shields.io/badge/Dagger%20Hilt-2.51.1-008373?style=flat-square)](https://dagger.dev/hilt/)
[![Ktor](https://img.shields.io/badge/Ktor%20CIO-2.3.12-E01A4F?style=flat-square&logo=ktor&logoColor=white)](https://ktor.io)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square)](LICENSE)

*Master Companion turns a docked Android device (Google Pixel 7 Pro, Huawei P20 Lite, or any Android 9+ phone) into a permanent landscape desk dashboard for Spotify playback, synchronized lyrics, PC audio loopback streaming, and hardware battery bypass.*

### 🚀 [Download Latest APK](https://github.com/reapzmedia/master-companion/releases/latest/download/app-release.apk) • [View Release Notes](https://github.com/reapzmedia/master-companion/releases/latest)

</div>

---

## 📸 Highlights & Core Features

### 🕒 1. Standby Clock Suite (5 Styles)
- **Minimalist Digital**: Oversized typography with battery status and date.
- **Analog Precision Gauge**: Dial gauge with sweeping second hand, tick marks, and battery level arc.
- **Retro Split-Flap**: Mechanical split-flap card flip animations with time and battery metrics.
- **Cyberpunk Terminal**: Monospace digital readout with system diagnostics.
- **Word Clock Matrix**: Typographic matrix lighting up the time in words.
- **OLED Anti-Burn-In Protection**: Micro-pixel shifting every minute to protect OLED/AMOLED panels during 24/7 docking.
- **12h / 24h & Dark / True Black / White Themes**.

### 🎵 2. Fullscreen Spotify Companion (5 Layout Modes)
- **Standard Car View**: Landscape player layout with large touch targets and ambient artwork backdrop.
- **Vinyl Turntable**: Spinning vinyl record with tonearm that cues during playback and returns on pause.
- **Karaoke Synced Lyrics**: Progressive-blur karaoke lyrics engine. Active line stays centered; upcoming lines gently blur with depth of field.
- **Minimal Standby Clock**: `110sp` digital clock with compact track info; tap to expand.
- **Full-Bleed Edge-to-Edge**: Full-screen album art backdrop with translucent control overlay.
- **Spotify Web API Support**: Modern `/v1/me/library` endpoints for 1-tap Liked Songs toggle and remote device volume sync.

### 🎼 3. Multi-Source Synced Lyrics Engine
- **Primary**: [LRCLIB](https://lrclib.net) for synchronized LRC timestamps.
- **Fallback**: [Lyrics.ovh](https://lyricsovh.docs.apiary.io) for plain-text lyrics when synced LRCs are unavailable.
- **In-Memory Cache**: Caches fetched lyrics in memory for repeat listens.

### 🔋 4. Root Battery Guard & AC Power Bypass
- **Direct Kernel Sysfs Control**: Commands hardware charging ICs via root (`su -c`).
  - **Pixel 7 Pro**: `/sys/class/power_supply/battery/charging_enabled`
  - **Huawei P20 Lite / Legacy**: `/sys/class/power_supply/Battery/charging_enabled`
- **80% Charge Limit**: Automatically halts charging at 80% and powers the phone purely on AC bypass power to prevent heat and battery swelling. Resumes automatically below 75%.
- **Live Power Telemetry**: Reads micro-volts ($\mu V$), micro-amps ($\mu A$), and real-time wattage ($W$).

### 📅 5. Google Calendar Widget
- Queries the Android Calendar `ContentProvider` for current day events.
- Shows upcoming events, start/end times, and countdowns on the clock screen.

### 🌐 6. Embedded Web Remote Dashboard (`:8060`) & HTTP Command Server (`:8420`)
- **Web Remote Control**: Served directly by the phone. Open `http://<phone-ip>:8060` in any PC browser to control playback, volume, clock styles, and power limits.
- **Server-Sent Events (SSE)**: Pushes real-time battery wattage, track metadata, and volume updates directly to connected browsers.
- **REST Command API**: Token-authenticated HTTP endpoints (`POST /command`) for script automation.

### 🎧 7. Low-Latency PC Audio Passthrough Receiver (`:8421`)
- Streams 48kHz 16-bit stereo PCM audio from Windows WASAPI loopback into Android's low-latency `AudioTrack` via UDP port `8421`.
- Latency $< 25\text{ms}$ over local Wi-Fi or USB tethering.

### ⌨️ 8. Physical Macro-Pad & Rotary Encoder Bridge
- AutoHotkey v2 bridge script (`pc/ahk/companion_bridge.ahk`) maps physical macro keys (`F13`-`F24`, custom numpads, rotary encoders) to Android actions.
- Rotary knob support: updates Android and Spotify volume simultaneously.

---

## 🏛️ System Architecture & Network Topology

```mermaid
graph LR
    subgraph Windows PC
        MacroKeypad[Physical Macro Keypad / Knob] --> AHK[AutoHotkey v2: companion_bridge.ahk]
        AHK -->|HTTP POST :8420 /command| NetBridge
        SysAudio[WASAPI Loopback Audio] --> PyStream[Python: audio_streamer.py]
        PyStream -->|UDP :8421 48kHz PCM| NetBridge
        Browser[Any Web Browser] -->|HTTP GET :8060 Web UI & SSE| NetBridge
        ADB[ADB Reverse / Forward] -.->|USB Tethering / Wi-Fi| NetBridge
    end

    subgraph Android Device [Docked Standby Dashboard]
        NetBridge((Wi-Fi or USB Cable)) --> Ktor[Embedded Ktor Server :8420 & :8060]
        NetBridge --> AudioSvc[AudioReceiverService :8421]
        Ktor --> ActionHandler[Command Dispatcher]
        ActionHandler --> RootShell[RootShell: su -c sysfs]
        RootShell --> BatterySysfs["/sys/class/power_supply/battery/"]
        AudioSvc --> AudioTrack[AudioTrack 48kHz Stereo]
        SpotifyAPI[Spotify Web API + OAuth PKCE] --> ComposeUI[Jetpack Compose DashboardHost]
        LRCLIB[LRCLIB / Lyrics.ovh API] --> ComposeUI
        CalendarProvider[Android Calendar Provider] --> ComposeUI
    end
```

---

## 📁 Repository Structure

```
master-companion/
├── app/                                                # Android Native Application
│   ├── build.gradle.kts                                # minSdk 28, targetSdk 34, desugaring, Hilt, Ktor
│   ├── proguard-rules.pro                              # Proguard rules for Ktor, Netty, Serialization
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                     # Foreground services, permissions, deep links
│       │   ├── assets/commands.json                    # Command actions registry
│       │   ├── java/com/mastercompanion/
│       │   │   ├── MasterCompanionApp.kt               # Application class (@HiltAndroidApp)
│       │   │   ├── MainActivity.kt                     # Adaptive landscape/portrait entry point
│       │   │   ├── data/
│       │   │   │   ├── audio/                          # PacketParser, JitterBuffer, AudioPlayer
│       │   │   │   ├── battery/                        # RootBatteryDataSource, BatteryRepository
│       │   │   │   ├── calendar/                       # CalendarRepository (ContentProvider sync)
│       │   │   │   ├── command/                        # CommandExecutor, CommandRegistry
│       │   │   │   ├── lyrics/                         # LyricsRepository (LRCLIB + Lyrics.ovh)
│       │   │   │   ├── network/                        # WolSender (Wake-on-LAN)
│       │   │   │   ├── prefs/                          # PreferencesRepository (DataStore)
│       │   │   │   └── spotify/                        # SpotifyApi, SpotifyAuthManager (PKCE), SpotifyRepository
│       │   │   ├── di/                                 # Hilt Modules (AppModule, NetworkModule, RepositoryModule)
│       │   │   ├── domain/model/                       # BatteryData, SpotifyTrack, Lyrics, CalendarEvent
│       │   │   ├── platform/root/                      # RootShell (su execution & sysfs)
│       │   │   ├── server/                             # CommandBridgeServer (Ktor CIO), WebDashboardHtml
│       │   │   ├── service/                            # BatteryGuardService, CommandBridgeService, AudioReceiverService
│       │   │   └── ui/
│       │   │       ├── dashboard/                      # DashboardHost, DashboardViewModel (HorizontalPager)
│       │   │       ├── home/                           # HomePage, MusicPage, LyricsView, clock/ (5 Clock Styles)
│       │   │       ├── audio/                          # AudioPage (passthrough monitor & stream telemetry)
│       │   │       ├── system/                         # SystemPage (sysfs diagnostics & hardware stats)
│       │   │       ├── settings/                       # SettingsPage (drawer suite & config)
│       │   │       └── theme/                          # Theme.kt, Color.kt, Type.kt
│       │   └── res/                                    # M3 colors, vectors, themes, network_security_config
│       └── test/                                       # Complete unit test suite (13 suites)
│
├── pc/                                                 # Desktop Utilities (Windows)
│   ├── ahk/
│   │   ├── companion_bridge.ahk                        # AutoHotkey v2 macro bridge script
│   │   └── README.md
│   ├── audio/
│   │   ├── audio_streamer.py                           # Python WASAPI loopback UDP audio streamer
│   │   ├── requirements.txt                            # sounddevice, numpy, opuslib
│   │   └── README.md
│   └── scripts/
│       ├── setup_adb_reverse.bat                       # ADB port reverse (:8420/:8060) & forward (:8421)
│       └── test_command_bridge.ps1                     # PowerShell test client for Ktor server
│
├── docs/                                               # Complete Specifications
│   ├── 01_PRD.md                                       # Product Requirements Document
│   ├── 02_SRS.md                                       # Software Requirements Specification
│   ├── 03_UI_UX_Architecture.md                        # UI/UX & Design Tokens
│   ├── 04_Implementation_Plan.md                       # Implementation Milestones
│   ├── 05_Testing_Strategy.md                          # Testing Strategy
│   ├── 06_Error_Handling_Spec.md                       # Error Handling & Fallbacks
│   ├── 07_Project_Structure.md                         # Package Layout Guide
│   └── 08_PC_Companion_Setup.md                        # PC Companion Setup Guide
├── AGENTS.md                                           # Single Source of Truth Context for AI Agents
└── build.gradle.kts                                    # Root Gradle build script
```

---

## 🚀 Quick Start

### 1. Requirements
- **Android Phone**: Rooted (`Magisk` or `KernelSU`). Android 9.0 (API 28) through Android 14+ (API 34).
- **PC**: Windows 10/11 with Python 3.10+ and AutoHotkey v2 (optional for macro keypad).
- **Development**: Android Studio (Koala / Ladybug / Jellyfish) with JDK 17 or JDK 21.

### 2. Building & Installing the Android App
```powershell
# Clone the repository
git clone https://github.com/Reapzmedia/master-companion.git
cd master-companion

# Run unit tests
.\gradlew.bat testDebugUnitTest

# Build and install release APK to connected device
.\gradlew.bat assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 3. Granting Root & Connecting Spotify
1. Open **Master Companion** on the device.
2. Grant **Root permissions** when prompted (for Battery Guard charge bypass).
3. Grant **Calendar permission** (for upcoming schedule events).
4. Swipe to **Settings** > **Spotify Web API** > tap **Connect Spotify**.
   - Authenticate via Spotify OAuth PKCE in your browser.
   - Currently playing media will appear on the Music page.

### 4. Running the PC Companion Utilities

#### A. PC Audio Passthrough (WASAPI Loopback)
```powershell
cd pc/audio
pip install -r requirements.txt

# List audio devices to find your loopback device index
python audio_streamer.py --list-devices

# Start streaming to your phone's IP address
python audio_streamer.py --target-ip 192.168.1.42 --codec pcm
```

#### B. AutoHotkey Macro Keypad Bridge
1. Open `pc/ahk/companion_bridge.ahk` in a text editor.
2. Set `ANDROID_IP := "<your-phone-ip>"` and paste your `AUTH_TOKEN` from **Settings > Security**.
3. Run `companion_bridge.ahk`. Your `F13`-`F24` keys and volume knobs will now control the phone silently.

#### C. Web Remote Control Dashboard
Open any browser on your local network:
```
http://<PHONE_IP>:8060
```
Provides playback controls, track seeking, volume adjustment, and battery telemetry.

---

## 📡 REST API & Network Endpoints

The embedded Ktor server exposes the following endpoints on port `8420`:

| Method | Endpoint | Headers | Description |
|---|---|---|---|
| `GET` | `/ping` | None | Returns `{"status":"ok"}` health check |
| `GET` | `/status` | None | Returns full JSON system, battery, and playback status |
| `GET` | `/commands` | None | Returns list of all available commands |
| `POST` | `/command` | `X-Auth-Token: <token>` | Dispatches an action to the app |
| `GET` | `http://<ip>:8060/` | None | Browser remote control dashboard |
| `GET` | `http://<ip>:8060/api/events` | None | Server-Sent Events (SSE) telemetry stream |

### Example Command Payload
```bash
curl -X POST http://192.168.1.42:8420/command \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: master-companion-default-token" \
  -d '{"action":"spotify_play_pause", "params":{}}'
```

---

## 🧪 Testing & Verification

Unit tests cover domain logic, protocol parsers, and API integrations:
```powershell
.\gradlew.bat testDebugUnitTest
```
- **Test Suites**: `LyricsRepositoryTest`, `SpotifyRepositoryTest`, `PacketParserTest`, `JitterBufferTest`, `CommandExecutorTest`, `RootBatteryDataSourceTest`, `BatteryRepositoryTest`, `DeviceCompatTest`, `RootShellTest`, and more.

---

## 📄 License
Distributed under the **Apache License 2.0**. See `LICENSE` for details.
