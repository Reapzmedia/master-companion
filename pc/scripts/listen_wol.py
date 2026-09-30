import socket
import sys

def listen_wol(port=9):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    except Exception:
        pass
    sock.bind(('0.0.0.0', port))
    print(f"[*] Listening for WoL magic packets on UDP port {port} across all interfaces...")
    print(f"[*] Waiting for packet from phone...")
    sys.stdout.flush()

    while True:
        data, addr = sock.recvfrom(1024)
        print(f"\n[+] RECEIVED UDP PACKET FROM {addr[0]}:{addr[1]} ({len(data)} bytes)")
        if len(data) >= 102 and data[:6] == b'\xff' * 6:
            mac_bytes = data[6:12]
            mac_str = ':'.join(f'{b:02X}' for b in mac_bytes)
            expected = b'\xff' * 6 + mac_bytes * 16
            if data[:102] == expected:
                print(f"    ==================================================")
                print(f"    >>> [SUCCESS] 100% VALID WAKE-ON-LAN MAGIC PACKET! <<<")
                print(f"    >>> Target MAC Address : {mac_str}")
                print(f"    >>> Payload Length     : {len(data)} bytes (Standard 102-byte frame)")
                print(f"    ==================================================")
            else:
                print(f"    [!] Starts with 6x 0xFF, but repetitions did not match.")
        else:
            print(f"    [!] Received non-WoL packet: {data[:20].hex()}...")
        sys.stdout.flush()

if __name__ == '__main__':
    p = int(sys.argv[1]) if len(sys.argv) > 1 else 9
    try:
        listen_wol(p)
    except KeyboardInterrupt:
        print("\n[*] Stopped.")
