# ⚡ Master Companion v1.0.4 — Broadcast Device Selector & Spotify Remote

### 🌟 What's New in v1.0.4
- **📡 Interactive Broadcast Device Selector Menu**:
  - Tapping the Broadcast / Cast icon (`Icons.Filled.Cast`) in both Landscape and Portrait player views opens a responsive dropdown menu to view and select Spotify Connect playback devices.
  - Lists all active and detected devices on your local network (e.g. PC, phone, smart speakers, TVs) with device-type specific iconography.
  - Active device is highlighted with green text and checkmark (`Listening on this device`).
  - Tapping any target device seamlessly transfers Spotify playback to that device in real time.
  - Includes quick actions to **Scan for Devices** and open the **Device Settings Dialog...**.
- **🕒 Home Page "CURRENTLY SHOWING" Live Device Telemetry**:
  - The Standby Clock home screen now features an active device pill (`[Cast] CURRENTLY SHOWING: [DEVICE]`) in the top bar across both Landscape and Portrait views.
  - Tapping the pill allows immediate device selection directly from the Home clock page.
- **✨ Clean Player Footers**:
  - Removed the static "SPOTIFY CONNECT" text from the player footer.
  - Replaced with dynamic `PLAYING ON: [DEVICE]` when remote playback is active, and clean `DEVICES & AUDIO` when inactive.
- **🔊 Hardware Volume Remote Synchronization**:
  - Full support for hardware volume key control synchronizing bidirectional volume between device and active Spotify Connect endpoints.
  - Interactive volume HUD automatically dismisses after 5 seconds of inactivity.
- **⏭️ Interactive Timeline Scrubbing & "Up Next" Song Banner**:
  - Smooth seeking anywhere on the track timeline with real-time API position updates.
  - Elegant floating corner banner announcing upcoming songs before the current track finishes.

### 📦 Installation & OTA
- **Direct Download**: Download `app-release.apk` below and install directly onto your device.
- **OTA Auto-Updater**: If already running v1.0.3 or earlier, open the app, go to Settings, and tap **Check for Updates** to automatically download, cryptographically verify, and silently install v1.0.4 with zero hassle!
