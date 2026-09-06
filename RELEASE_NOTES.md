# ⚡ Master Companion v1.0.5 — OTA Signature Verification Fix & Codebase Hardening

### 🌟 What's New in v1.0.5
- **🔐 Robust OTA Signature Verification Engine**:
  - Resolved upstream Android 9 / API 28 OEM limitation where `PackageManager.getPackageArchiveInfo` with `GET_SIGNING_CERTIFICATES` alone failed to populate archive certificate data on Huawei EMUI 9 and older ROMs.
  - Combined `PackageManager.GET_SIGNATURES` and `PackageManager.GET_SIGNING_CERTIFICATES` with multi-tier certificate byte matching to ensure seamless self-updating on both rooted and unrooted devices.
  - Eliminated the false-positive *"Security verification failed: APK signature or package identity mismatch"* alert during in-app updates.
- **🛡️ Skylos Static Analysis & Quality Gate Compliance**:
  - Eliminated all high-severity complexity and cognitive hotspots in the PC audio streaming engine (`audio_streamer.py`), modularizing device resolution and frame processing.
  - Hardened network release automation (`publish_release.py`) with explicit 60-second timeouts to guard against worker thread stalls.
  - Added repository-level `pyproject.toml` with `mypy`, `ruff`, and `[tool.skylos.gate]` policies along with `.pre-commit-config.yaml`.
- **📡 Broadcast Playback Device Selector**:
  - Interactive popup to switch Spotify Connect endpoints on the fly.
  - Live "CURRENTLY SHOWING" active device pill on the Home Clock screen.

### 📦 Installation & OTA
- **Direct Download**: Download `app-release.apk` below and install directly onto your device.
- **OTA Auto-Updater**: Open Master Companion, go to Settings, and tap **Check for Updates** to automatically verify, download, and install v1.0.5!
