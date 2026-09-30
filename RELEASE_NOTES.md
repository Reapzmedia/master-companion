# ⚡ Master Companion v1.0.6: Cloud-Relayed Wake Gateway & Zero-Password QR Sync

### What's New in v1.0.6
- **Worldwide Cloud-Relayed Wake Gateway (Phase 8)**:
  - Zero-Password QR pairing linking Desk Host (docked tablet/phone) and Pocket Waker (everyday phone).
  - Encrypted cloud vault with 6-digit pairing key fallback (`XXX-XXX`) via Firebase Anonymous Auth & Cloud Firestore.
  - Zero-root requirement: standard Android SDK networking supported on Android 9+ (API 28–34).
- **Dual LAN + Cloud Wake Engine**:
  - Direct local LAN HTTP trigger (`:8420/api/wol`) with instant 2ms execution when connected to home Wi-Fi.
  - Automatic Cloud Firestore fallback when outside home Wi-Fi over 4G/5G mobile networks.
- **Hardware-Free PC Auto-Detection**:
  - Subnet scanner using NetBIOS Node Status queries (UDP port 137) to discover target PC hostname and MAC address without any companion software on the PC.
  - Subnet broadcast calculator automatically configures targeted Wake-on-LAN broadcast IP.
  - Boot verification via TCP port 445 probe, tracking boot duration down to the millisecond.
- **Tactile Pocket Remote & Quick Access**:
  - Dedicated tactile Pocket Remote view with live boot stopwatch and milestone progression.
  - Android Quick Settings Tile (`QuickWakeTileService`) to wake PC from the notification shade without opening the app.
  - 1x1 Home Screen Widget (`QuickWakeWidget`) for instant one-tap PC powering.
  - Natural portrait launch orientation for Pocket Waker devices.

### Installation & Updates
- **Direct Download**: Download `app-release.apk` below and install onto your device.
- **In-App Updater**: Open Settings (Page 1) and tap **Check for Updates** to download and install v1.0.6 over the air.
