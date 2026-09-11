# ⚡ Master Companion v1.0.5: Android 9 OTA Verification & Static Analysis

### What's New in v1.0.5
- **OTA Signature Verification on Android 9**:
  - Resolved upstream Android 9 / API 28 OEM limitation where `PackageManager.getPackageArchiveInfo` with `GET_SIGNING_CERTIFICATES` alone failed to populate archive certificate data on Huawei EMUI 9 and older ROMs.
  - Combined `PackageManager.GET_SIGNATURES` and `PackageManager.GET_SIGNING_CERTIFICATES` with multi-tier certificate byte matching to allow self-updating on both rooted and unrooted devices.
  - Eliminated false-positive signature mismatch errors during in-app updates.
- **Static Analysis & Quality Gate**:
  - Resolved high-severity complexity hotspots in the PC audio streaming engine (`audio_streamer.py`), modularizing device resolution and frame processing.
  - Added 60-second timeouts to release automation (`publish_release.py`) to prevent worker thread stalls.
  - Added repository-level `pyproject.toml` with `mypy`, `ruff`, and `[tool.skylos.gate]` policies along with `.pre-commit-config.yaml`.
- **Playback Device Selector**:
  - Added popup dialog to switch Spotify Connect endpoints.
  - Added active device indicator on the Home Clock screen.

### Installation & Updates
- **Direct Download**: Download `app-release.apk` below and install onto the device.
- **OTA Updater**: In Settings, tap **Check for Updates** to check, download, and install v1.0.5.
