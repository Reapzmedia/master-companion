#!/usr/bin/env python3
"""
Master Companion — PC Audio Streamer
Captures system audio via Windows WASAPI loopback and streams it to the Android app.
Supports dual-transport (TCP for USB ADB forward, UDP for Wi-Fi LAN) with real-time VU meter.
Default codec: 16-bit 48kHz stereo uncompressed PCM (with optional Opus compression).
"""

import argparse
import socket
import struct
import sys
import time
import signal
from typing import Optional, Tuple, Any

try:
    import numpy as np
    import sounddevice as sd
except ImportError as e:
    print(f"ERROR: Missing required Python dependency: {e}", file=sys.stderr)
    print("Run: pip install sounddevice numpy", file=sys.stderr)
    sys.exit(1)

# Safe Opus import check
OPUS_AVAILABLE = False
opus_error_message = ""
try:
    import opuslib
    # Try dummy encoder to verify opus.dll actually loads
    _test_enc = opuslib.Encoder(48000, 2, opuslib.APPLICATION_AUDIO)
    OPUS_AVAILABLE = True
except Exception as e:
    OPUS_AVAILABLE = False
    opus_error_message = str(e)

# Audio Constants
SAMPLE_RATE = 48000
CHANNELS = 2
FRAME_SIZE = 960  # 20ms at 48kHz
CODEC_PCM = 0x01
CODEC_OPUS = 0x02


def list_audio_devices() -> None:
    """List all available audio input and output devices."""
    print("\nAvailable Audio Devices:")
    print("=" * 75)
    devices = sd.query_devices()
    for i, dev in enumerate(devices):
        direction = []
        if dev['max_input_channels'] > 0:
            direction.append("IN")
        if dev['max_output_channels'] > 0:
            direction.append("OUT")
        dir_str = "/".join(direction) if direction else "NONE"
        is_loopback = " [WASAPI LOOPBACK]" if "loopback" in dev['name'].lower() else ""
        is_mix = " [STEREO MIX]" if "stereo mix" in dev['name'].lower() else ""
        is_sonar = " [SONAR STREAM]" if "sonar" in dev['name'].lower() and "stream" in dev['name'].lower() else ""
        tags = f"{is_loopback}{is_mix}{is_sonar}"
        print(f" [{i:2d}] {dev['name']}{tags} ({dir_str}, HostAPI: {dev['hostapi']})")
    print("=" * 75)
    print("Tip: To capture PC audio, select the loopback or output device using --device <index>\n")


def create_packet(codec_flag: int, seq: int, timestamp: int, payload: bytes) -> bytes:
    """Pack header and audio payload: 1 byte codec flag, 4 bytes sequence, 4 bytes timestamp."""
    header = struct.pack('>BII', codec_flag, seq & 0xFFFFFFFF, timestamp & 0xFFFFFFFF)
    return header + payload


def resolve_input_device(device: Optional[int], *, verbose: bool = False) -> int:
    """Resolve audio input device index, automatically finding WASAPI loopback, stereo mix, or Sonar."""
    if device is not None:
        return device

    devices = sd.query_devices()
    # 1. Look for explicit WASAPI loopback
    for i, dev in enumerate(devices):
        name_lower = dev['name'].lower()
        if 'loopback' in name_lower and dev['max_input_channels'] >= 2:
            return i

    # 2. Look for Stereo Mix
    for i, dev in enumerate(devices):
        name_lower = dev['name'].lower()
        if 'stereo mix' in name_lower and dev['max_input_channels'] >= 2:
            return i

    # 3. Look for SteelSeries Sonar Stream / Media capture
    for i, dev in enumerate(devices):
        name_lower = dev['name'].lower()
        if 'sonar' in name_lower and 'stream' in name_lower and dev['max_input_channels'] >= 2:
            return i

    # 4. Check default output device
    try:
        default_out = sd.default.device[1]
        if default_out is not None and default_out >= 0:
            print(f"Default loopback not explicitly found, falling back to output device #{default_out}...")
            return default_out
    except (IndexError, TypeError, KeyError) as err:
        if verbose:
            print(f"Could not query default device index: {err}")

    print("ERROR: No default WASAPI loopback, Stereo Mix, or virtual audio device found.", file=sys.stderr)
    print("Run with --list-devices to inspect available hardware and specify with --device <num>.", file=sys.stderr)
    sys.exit(1)


def setup_encoder(codec: str) -> Tuple[Optional[Any], bool, int]:
    """Configure and initialize audio encoder (Opus or uncompressed PCM)."""
    global OPUS_AVAILABLE

    use_opus = (codec.lower() == 'opus')
    if use_opus and not OPUS_AVAILABLE:
        print("\n[NOTE] Opus library (opus.dll) not detected on Windows.")
        if opus_error_message:
            print(f"       Details: {opus_error_message}")
        print("       Automatically falling back to uncompressed 16-bit PCM streaming (high quality, ~1.5 Mbps).")
        print("       Tip: Install opus.dll in PATH or use '--codec pcm' to suppress this message.\n")
        use_opus = False

    encoder = None
    codec_flag = CODEC_OPUS if use_opus else CODEC_PCM

    if use_opus:
        try:
            encoder = opuslib.Encoder(SAMPLE_RATE, CHANNELS, opuslib.APPLICATION_AUDIO)
        except Exception as e:
            print(f"Failed to initialize Opus encoder ({e}). Falling back to PCM.")
            use_opus = False
            codec_flag = CODEC_PCM

    return encoder, use_opus, codec_flag


def process_audio_frame(
    indata: np.ndarray,
    encoder: Optional[Any],
    *,
    use_opus: bool = False,
    verbose: bool = False
) -> Optional[bytes]:
    """Convert float32 samples to int16 PCM and optionally compress using Opus."""
    audio_int16 = (np.clip(indata, -1.0, 1.0) * 32767.0).astype(np.int16)
    if use_opus and encoder is not None:
        try:
            return encoder.encode(audio_int16.tobytes(), FRAME_SIZE)
        except Exception as e:
            if verbose:
                print(f"\n  [ERR] Opus encode error: {e}")
            return None
    return audio_int16.tobytes()


def print_streaming_banner(device_id: int, dev_name: str, target: str, port: int, transport: str, use_opus: bool) -> None:
    """Display active connection telemetry header."""
    print("═══════════════════════════════════════════════════════════════════════")
    print(" 🎧 Master Companion — PC Audio Streamer Running")
    print("═══════════════════════════════════════════════════════════════════════")
    print(f" Audio Device : [{device_id}] {dev_name}")
    print(f" Target       : {target}:{port}")
    print(f" Transport    : {transport.upper()} ({'USB ADB Forward' if target in ('127.0.0.1', 'localhost') else 'Wi-Fi LAN'})")
    print(f" Codec        : {'Opus (48kHz stereo, 20ms frame)' if use_opus else 'PCM 16-bit stereo uncompressed'}")
    print(f" Frame Size   : {FRAME_SIZE} samples ({FRAME_SIZE / SAMPLE_RATE * 1000:.1f}ms)")
    print(" Press Ctrl+C in this terminal to stop streaming.")
    print("═══════════════════════════════════════════════════════════════════════\n")


def stream_audio(
    target_ip: str,
    port: int,
    device: Optional[int] = None,
    codec: str = 'pcm',
    transport: str = 'auto',
    verbose: bool = False
) -> None:
    """Main audio streaming loop capturing from WASAPI loopback and transmitting via TCP or UDP."""
    # Resolve transport
    if transport == 'auto':
        transport = 'tcp' if target_ip in ('127.0.0.1', 'localhost') else 'udp'

    encoder, use_opus, codec_flag = setup_encoder(codec)
    input_device = resolve_input_device(device, verbose=verbose)
    device_info = sd.query_devices(input_device)
    print_streaming_banner(input_device, device_info['name'], target_ip, port, transport, use_opus)

    state = {
        "seq": 0,
        "timestamp": 0,
        "packets": 0,
        "start": time.time(),
        "running": True,
        "last_rms": 0.0
    }

    # Prepare socket
    udp_sock = None
    tcp_sock = None

    if transport == 'tcp':
        try:
            tcp_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            tcp_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            tcp_sock.connect((target_ip, port))
            print(f" Connected to TCP receiver at {target_ip}:{port} (USB Cable Mode)")
        except Exception as e:
            print(f" [WARN] Could not immediately connect TCP to {target_ip}:{port}: {e}")
            print("        Will attempt transmission in streaming loop.")
    else:
        udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    def audio_callback(indata, frames, time_info, status):
        nonlocal tcp_sock
        if status and verbose:
            print(f"\n  [STREAM WARNING] {status}")

        # Compute volume level for VU meter
        rms = float(np.sqrt(np.mean(indata**2)))
        state["last_rms"] = rms

        payload = process_audio_frame(indata, encoder, use_opus=use_opus, verbose=verbose)
        if payload is None:
            return

        packet = create_packet(codec_flag, state["seq"], state["timestamp"], payload)

        if transport == 'tcp':
            # Frame with 2-byte length prefix (Big-Endian)
            framed = struct.pack('>H', len(packet)) + packet
            try:
                if tcp_sock is None:
                    tcp_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    tcp_sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                    tcp_sock.connect((target_ip, port))
                tcp_sock.sendall(framed)
            except Exception as e:
                if verbose:
                    print(f"\n  [ERR] TCP send error: {e}")
                if tcp_sock:
                    try:
                        tcp_sock.close()
                    except Exception:
                        pass
                tcp_sock = None
                return
        else:
            try:
                if udp_sock:
                    udp_sock.sendto(packet, (target_ip, port))
            except OSError as e:
                if verbose:
                    print(f"\n  [ERR] UDP socket send error: {e}")
                return

        state["seq"] += 1
        state["timestamp"] += FRAME_SIZE
        state["packets"] += 1

        # Real-time terminal VU meter update (every 25 packets ~500ms)
        if state["packets"] % 25 == 0:
            elapsed = time.time() - state["start"]
            pps = state["packets"] / elapsed if elapsed > 0 else 0
            cur_rms = state["last_rms"]
            db = 20.0 * np.log10(max(cur_rms, 1e-5))
            bars = int(np.clip((db + 50.0) / 50.0 * 14.0, 0, 14))
            vu_str = "█" * bars + "░" * (14 - bars)
            codec_label = "Opus" if use_opus else "PCM"
            print(f"\r [STREAMING] Sent {state['packets']:6d} pkts ({pps:4.1f} pkt/s) | VU: [{vu_str}] {db:5.1f} dB | {transport.upper()} | {codec_label}   ", end="", flush=True)

    signal.signal(signal.SIGINT, lambda s, f: state.update({"running": False}))

    try:
        with sd.InputStream(
            device=input_device,
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            blocksize=FRAME_SIZE,
            dtype='float32',
            callback=audio_callback
        ):
            while state["running"]:
                time.sleep(0.1)
    except Exception as e:
        print(f"\nAudio capture error: {e}")
        print("Tip: If capture fails, verify your output device or run with --list-devices to select another.")
    finally:
        if udp_sock:
            udp_sock.close()
        if tcp_sock:
            tcp_sock.close()
        total_time = time.time() - state["start"]
        print(f"\n\nStreaming stopped. Sent {state['packets']} packets over {total_time:.1f} seconds.")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Master Companion PC Audio Streamer (WASAPI Loopback to Android)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Wi-Fi streaming to phone IP:
  python audio_streamer.py --target-ip 192.168.1.75

  # USB Cable streaming (after running setup_adb_reverse.bat):
  python audio_streamer.py --target-ip 127.0.0.1

  # Force TCP transport:
  python audio_streamer.py --target-ip 192.168.1.75 --transport tcp

  # List devices and select specific device:
  python audio_streamer.py --list-devices
  python audio_streamer.py --target-ip 192.168.1.75 --device 45
        """
    )
    parser.add_argument('--target-ip', type=str, help='Android device IP address (e.g. 192.168.1.75 or 127.0.0.1 for USB)')
    parser.add_argument('--port', type=int, default=8421, help='Target port (default: 8421)')
    parser.add_argument('--device', type=int, default=None, help='Audio capture device index')
    parser.add_argument('--codec', choices=['pcm', 'opus'], default='pcm', help='Audio codec (default: pcm)')
    parser.add_argument('--transport', choices=['auto', 'tcp', 'udp'], default='auto', help='Transport protocol (default: auto - tcp for 127.0.0.1, udp for LAN)')
    parser.add_argument('--list-devices', action='store_true', help='List available audio devices and exit')
    parser.add_argument('--verbose', action='store_true', help='Print continuous transfer statistics and warnings')

    args = parser.parse_args()

    if args.list_devices:
        list_audio_devices()
        sys.exit(0)

    if not args.target_ip:
        parser.error("Missing --target-ip argument. Specify your Android phone's IP address (e.g. 192.168.1.75 or 127.0.0.1 for USB).")

    stream_audio(
        target_ip=args.target_ip,
        port=args.port,
        device=args.device,
        codec=args.codec,
        transport=args.transport,
        verbose=args.verbose
    )


if __name__ == '__main__':
    main()
