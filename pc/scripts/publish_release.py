import subprocess
import json
import urllib.request
import urllib.error
import os
import sys
from typing import Optional, Dict, Any, Tuple

DEFAULT_ENCODING = "utf-8"
DEFAULT_TIMEOUT_SECONDS = 60


def get_git_token() -> Optional[str]:
    proc = subprocess.Popen(
        ['git', 'credential', 'fill'],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding=DEFAULT_ENCODING
    )
    out, _ = proc.communicate(input="protocol=https\nhost=github.com\n")
    token = None
    for line in out.splitlines():
        if line.startswith("password="):
            token = line.split("=", 1)[1].strip()
            break
    return token


def create_or_get_release(
    repo: str,
    tag: str,
    name: str,
    body: str,
    headers: Dict[str, str]
) -> Tuple[int, str]:
    payload = {
        "tag_name": tag,
        "name": name,
        "body": body,
        "draft": False,
        "prerelease": False
    }

    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases",
        data=json.dumps(payload).encode(DEFAULT_ENCODING),
        headers=headers,
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT_SECONDS) as resp:
            data = json.loads(resp.read().decode(DEFAULT_ENCODING))
            release_id = data["id"]
            html_url = data.get("html_url", "")
            print(f"Release created successfully (ID: {release_id})")
            print(f"Release URL: {html_url}")
            return release_id, html_url
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode(DEFAULT_ENCODING)
        print(f"HTTP Error creating release: {e.code} - {err_msg}", file=sys.stderr)
        if e.code == 422:
            print("Fetching existing release for tag...")
            req_get = urllib.request.Request(
                f"https://api.github.com/repos/{repo}/releases/tags/{tag}",
                headers=headers,
                method="GET"
            )
            with urllib.request.urlopen(req_get, timeout=DEFAULT_TIMEOUT_SECONDS) as resp_get:
                data = json.loads(resp_get.read().decode(DEFAULT_ENCODING))
                return data["id"], data.get("html_url", "")
        sys.exit(1)


def upload_release_asset(
    repo: str,
    release_id: int,
    apk_path: str,
    token: str
) -> None:
    if not os.path.exists(apk_path):
        print(f"ERROR: APK not found at {apk_path}", file=sys.stderr)
        sys.exit(1)

    size = os.path.getsize(apk_path)
    print(f"Uploading app-release.apk ({size:,} bytes) to release {release_id}...")

    upload_url = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name=app-release.apk"
    upload_headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "MasterCompanion-ReleaseScript",
        "Content-Type": "application/vnd.android.package-archive"
    }

    with open(apk_path, "rb") as apk_f:
        apk_bytes = apk_f.read()

    upload_req = urllib.request.Request(upload_url, data=apk_bytes, headers=upload_headers, method="POST")
    try:
        with urllib.request.urlopen(upload_req, timeout=DEFAULT_TIMEOUT_SECONDS) as upload_resp:
            upload_data = json.loads(upload_resp.read().decode(DEFAULT_ENCODING))
            print("Asset uploaded successfully!")
            print(f"Browser download URL: {upload_data.get('browser_download_url')}")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode(DEFAULT_ENCODING)
        print(f"HTTP Error uploading asset: {e.code} - {err_msg}", file=sys.stderr)
        sys.exit(1)


def main() -> None:
    token = get_git_token()
    if not token:
        print("ERROR: Could not retrieve GitHub token from git credentials.", file=sys.stderr)
        sys.exit(1)

    repo = "Reapzmedia/master-companionion"
    tag = sys.argv[1] if len(sys.argv) > 1 else "v1.0.4"
    name = sys.argv[2] if len(sys.argv) > 2 else f"Master Companion {tag} - Landscape Standby Perfection & Autonomous OTA"

    notes_file = os.path.join(os.path.dirname(__file__), "..", "..", "RELEASE_NOTES.md")
    body = "Release " + tag
    if os.path.exists(notes_file):
        with open(notes_file, "r", encoding=DEFAULT_ENCODING) as f:
            body = f.read()

    print(f"Publishing GitHub release {tag} on {repo}...")
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "MasterCompanion-ReleaseScript",
        "Content-Type": "application/json"
    }

    release_id, _ = create_or_get_release(repo, tag, name, body, headers)

    apk_path = os.path.join(
        os.path.dirname(__file__),
        "..", "..", "app", "build", "outputs", "apk", "release", "app-release.apk"
    )
    upload_release_asset(repo, release_id, apk_path, token)

    print(f"\nSUCCESS: Master Companion {tag} is published on GitHub with app-release.apk attached!")


if __name__ == "__main__":
    main()
