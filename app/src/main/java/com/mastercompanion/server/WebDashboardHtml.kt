package com.mastercompanion.server

/**
 * Modern, responsive Embedded HTML/CSS/JS Web Dashboard served on port 8060.
 * Accessible from any browser at http://<device-ip>:8060.
 */
object WebDashboardHtml {

    fun getHtml(deviceIp: String, port: Int = 8060): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Master Companion | Web Dashboard</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-base: #08090C;
            --bg-card: #12141C;
            --bg-card-hover: #181B26;
            --border-subtle: rgba(255, 255, 255, 0.08);
            --border-glow: rgba(56, 189, 248, 0.3);
            --accent-spotify: #1DB954;
            --accent-spotify-hover: #1ed760;
            --accent-sky: #38BDF8;
            --accent-amber: #F59E0B;
            --accent-emerald: #10B981;
            --accent-crimson: #EF4444;
            --text-main: #FFFFFF;
            --text-secondary: #94A3B8;
            --text-muted: #64748B;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            background-color: var(--bg-base);
            color: var(--text-main);
            font-family: 'Plus Jakarta Sans', -apple-system, sans-serif;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 32px 16px;
        }

        .container {
            width: 100%;
            max-width: 860px;
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        /* Top Header */
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 8px;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .brand-icon {
            width: 40px;
            height: 40px;
            border-radius: 12px;
            background: linear-gradient(135deg, #1DB954, #059669);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 20px;
            box-shadow: 0 4px 14px rgba(29, 185, 84, 0.35);
        }

        .brand-title {
            font-size: 20px;
            font-weight: 800;
            letter-spacing: -0.5px;
        }

        .brand-subtitle {
            font-size: 12px;
            color: var(--text-secondary);
            font-family: 'JetBrains Mono', monospace;
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 8px;
            background: rgba(16, 185, 129, 0.12);
            border: 1px solid rgba(16, 185, 129, 0.25);
            padding: 6px 14px;
            border-radius: 999px;
            font-size: 12px;
            font-weight: 700;
            color: var(--accent-emerald);
            letter-spacing: 0.5px;
        }

        .pulse-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background-color: var(--accent-emerald);
            box-shadow: 0 0 10px var(--accent-emerald);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(0.85); }
        }

        /* Card System */
        .card {
            background-color: var(--bg-card);
            border: 1px solid var(--border-subtle);
            border-radius: 20px;
            padding: 24px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
            transition: border-color 0.2s;
        }

        .card:hover {
            border-color: rgba(255, 255, 255, 0.12);
        }

        .card-title {
            font-size: 11px;
            font-weight: 800;
            letter-spacing: 1.5px;
            color: var(--text-muted);
            text-transform: uppercase;
            margin-bottom: 16px;
        }

        /* Wake on LAN Hero Card */
        .wol-card {
            background: linear-gradient(135deg, #0F172A 0%, #111827 100%);
            border: 1px solid rgba(56, 189, 248, 0.2);
            display: flex;
            flex-direction: column;
            gap: 16px;
        }

        .wol-content {
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 16px;
        }

        .wol-info h2 {
            font-size: 24px;
            font-weight: 800;
            margin-bottom: 4px;
        }

        .wol-info p {
            font-size: 13px;
            color: var(--text-secondary);
        }

        .wol-target {
            font-family: 'JetBrains Mono', monospace;
            color: var(--accent-sky);
            background: rgba(56, 189, 248, 0.1);
            padding: 2px 8px;
            border-radius: 6px;
        }

        .btn-wol {
            background: linear-gradient(135deg, #0284C7, #0EA5E9);
            color: #FFFFFF;
            border: none;
            padding: 14px 28px;
            border-radius: 14px;
            font-size: 15px;
            font-weight: 700;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 10px;
            box-shadow: 0 4px 18px rgba(14, 165, 233, 0.35);
            transition: transform 0.15s, box-shadow 0.15s;
        }

        .btn-wol:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 24px rgba(14, 165, 233, 0.5);
        }

        .btn-wol:active {
            transform: translateY(1px);
        }

        /* Telemetry Grid */
        .telemetry-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
            gap: 14px;
        }

        .metric-tile {
            background: #181B26;
            border: 1px solid var(--border-subtle);
            border-radius: 14px;
            padding: 16px;
            display: flex;
            flex-direction: column;
            gap: 4px;
        }

        .metric-label {
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 1px;
            color: var(--text-muted);
            text-transform: uppercase;
        }

        .metric-value {
            font-size: 24px;
            font-weight: 800;
            color: var(--text-main);
            font-family: 'JetBrains Mono', monospace;
        }

        /* Spotify Media Card */
        .media-layout {
            display: flex;
            align-items: center;
            gap: 24px;
            flex-wrap: wrap;
        }

        .media-art {
            width: 100px;
            height: 100px;
            border-radius: 16px;
            object-fit: cover;
            background: #1C2030;
            box-shadow: 0 6px 20px rgba(0, 0, 0, 0.5);
        }

        .media-details {
            flex: 1;
            min-width: 200px;
            display: flex;
            flex-direction: column;
            gap: 6px;
        }

        .media-title {
            font-size: 20px;
            font-weight: 800;
            color: var(--text-main);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .media-artist {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-secondary);
        }

        .media-controls {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-top: 8px;
        }

        .btn-media {
            background: #1E2332;
            border: 1px solid var(--border-subtle);
            color: var(--text-main);
            width: 44px;
            height: 44px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 16px;
            transition: all 0.15s;
        }

        .btn-media:hover {
            background: #282E42;
            transform: scale(1.05);
        }

        .btn-media.play-pause {
            width: 52px;
            height: 52px;
            background: var(--accent-spotify);
            color: #000;
            font-size: 20px;
            box-shadow: 0 4px 16px rgba(29, 185, 84, 0.4);
        }

        .btn-media.play-pause:hover {
            background: var(--accent-spotify-hover);
            transform: scale(1.08);
        }

        .progress-bar-bg {
            width: 100%;
            height: 6px;
            background: #252B3B;
            border-radius: 3px;
            overflow: hidden;
            margin-top: 10px;
        }

        .progress-bar-fill {
            width: 0%;
            height: 100%;
            background: var(--accent-spotify);
            border-radius: 3px;
            transition: width 0.5s linear;
        }

        /* Toast notification */
        #toast {
            visibility: hidden;
            min-width: 250px;
            background-color: #10B981;
            color: #000;
            text-align: center;
            border-radius: 12px;
            padding: 14px 20px;
            position: fixed;
            z-index: 1000;
            bottom: 30px;
            font-weight: 700;
            box-shadow: 0 6px 20px rgba(16, 185, 129, 0.4);
            opacity: 0;
            transition: opacity 0.3s, visibility 0.3s;
        }

        #toast.show {
            visibility: visible;
            opacity: 1;
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <div class="header">
            <div class="brand">
                <div class="brand-icon">⚡</div>
                <div>
                    <div class="brand-title">Master Companion</div>
                    <div class="brand-subtitle">$deviceIp:$port</div>
                </div>
            </div>
            <div class="status-badge">
                <div class="pulse-dot"></div>
                <span>ONLINE</span>
            </div>
        </div>

        <!-- Wake on LAN Hero Card -->
        <div class="card wol-card">
            <div class="wol-content">
                <div class="wol-info">
                    <h2>Wake Desktop PC</h2>
                    <p>Broadcasts a UDP magic packet on port 9 to <span class="wol-target" id="wol-mac-label">Desktop PC</span></p>
                </div>
                <button class="btn-wol" onclick="sendWol()">
                    <span>⚡</span>
                    <span>WAKE PC NOW</span>
                </button>
            </div>
        </div>

        <!-- Spotify Media Player Card -->
        <div class="card">
            <div class="card-title">Now Playing (Spotify)</div>
            <div class="media-layout">
                <img id="track-art" class="media-art" src="https://misc.scdn.co/keep-calm/default-256.jpg" alt="Current album artwork">
                <div class="media-details">
                    <div id="track-title" class="media-title">No Track Active</div>
                    <div id="track-artist" class="media-artist">Open Spotify to begin playback</div>
                    
                    <div class="media-controls">
                        <button class="btn-media" onclick="controlMedia('prev')" aria-label="Previous track">⏮</button>
                        <button id="btn-play-pause" class="btn-media play-pause" onclick="controlMedia('play-pause')" aria-label="Play or pause">▶</button>
                        <button class="btn-media" onclick="controlMedia('next')" aria-label="Next track">⏭</button>
                    </div>

                    <div class="progress-bar-bg">
                        <div id="track-progress" class="progress-bar-fill"></div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Real-Time Hardware & Battery Telemetry -->
        <div class="card">
            <div class="card-title">Battery & Power Telemetry</div>
            <div class="telemetry-grid">
                <div class="metric-tile">
                    <span class="metric-label">Level</span>
                    <span id="metric-level" class="metric-value">--%</span>
                </div>
                <div class="metric-tile">
                    <span class="metric-label">Wattage</span>
                    <span id="metric-wattage" class="metric-value" style="color: #FACC15;">-- W</span>
                </div>
                <div class="metric-tile">
                    <span class="metric-label">Voltage</span>
                    <span id="metric-voltage" class="metric-value" style="color: #60A5FA;">-- V</span>
                </div>
                <div class="metric-tile">
                    <span class="metric-label">Current</span>
                    <span id="metric-current" class="metric-value">-- mA</span>
                </div>
                <div class="metric-tile">
                    <span class="metric-label">Temp</span>
                    <span id="metric-temp" class="metric-value" style="color: #34D399;">-- °C</span>
                </div>
                <div class="metric-tile">
                    <span class="metric-label">Bypass</span>
                    <span id="metric-bypass" class="metric-value" style="color: #F59E0B; font-size: 18px;">--</span>
                </div>
            </div>
        </div>
    </div>

    <!-- Feedback Toast -->
    <div id="toast" role="status" aria-live="polite">Message</div>

    <script>
        function showToast(text, color = '#10B981') {
            const toast = document.getElementById('toast');
            toast.innerText = text;
            toast.style.backgroundColor = color;
            toast.className = 'show';
            setTimeout(() => { toast.className = toast.className.replace('show', ''); }, 2800);
        }

        async function sendWol() {
            try {
                const res = await fetch('/api/wol', { method: 'POST' });
                const data = await res.json();
                if (data.status === 'ok') {
                    showToast('Wake-on-LAN packet sent');
                } else {
                    showToast('Failed to send: ' + (data.message || 'Unknown error'), '#EF4444');
                }
            } catch (err) {
                showToast('Error sending packet', '#EF4444');
            }
        }

        async function controlMedia(action) {
            try {
                await fetch('/api/media/' + action, { method: 'POST' });
                setTimeout(fetchStatus, 300);
            } catch (err) {
                console.error(err);
            }
        }

        async function fetchStatus() {
            try {
                const res = await fetch('/status');
                if (!res.ok) return;
                const data = await res.json();

                // Battery Telemetry
                if (data && data.battery) {
                    const b = data.battery;
                    const level = b.level !== undefined ? b.level : (b.percentage !== undefined ? b.percentage : '--');
                    const wattage = b.wattage !== undefined ? Math.abs(b.wattage).toFixed(1) : '--';
                    const volts = b.voltageVolts !== undefined ? b.voltageVolts.toFixed(2) : '--';
                    const current = b.currentMa !== undefined ? ((b.currentMa >= 0 ? '+' : '') + b.currentMa) : (b.currentAmperes !== undefined ? (b.currentAmperes * 1000).toFixed(0) : '--');
                    const temp = b.temperatureC !== undefined ? b.temperatureC.toFixed(1) : (b.temperatureCelsius !== undefined ? b.temperatureCelsius.toFixed(1) : '--');
                    const isBypassed = b.isBypassed !== undefined ? b.isBypassed : b.isChargeLimitActive;

                    const elLevel = document.getElementById('metric-level');
                    if (elLevel) elLevel.innerText = level + '%';
                    const elWatt = document.getElementById('metric-wattage');
                    if (elWatt) elWatt.innerText = wattage + ' W';
                    const elVolt = document.getElementById('metric-voltage');
                    if (elVolt) elVolt.innerText = volts + ' V';
                    const elCurr = document.getElementById('metric-current');
                    if (elCurr) elCurr.innerText = current + ' mA';
                    const elTemp = document.getElementById('metric-temp');
                    if (elTemp) elTemp.innerText = temp + ' °C';
                    const elBypass = document.getElementById('metric-bypass');
                    if (elBypass) elBypass.innerText = isBypassed ? 'ACTIVE' : 'OFF';
                }

                // Media Telemetry
                if (data && data.currentTrack) {
                    const t = data.currentTrack;
                    const elTitle = document.getElementById('track-title');
                    if (elTitle) elTitle.innerText = t.title || 'Untitled';
                    const elArtist = document.getElementById('track-artist');
                    if (elArtist) elArtist.innerText = (t.artist || 'Unknown') + (t.album ? ' • ' + t.album : '');
                    if (t.albumArtUrl) {
                        const elArt = document.getElementById('track-art');
                        if (elArt) elArt.src = t.albumArtUrl;
                    }
                    const elPlay = document.getElementById('btn-play-pause');
                    if (elPlay) elPlay.innerText = t.isPlaying ? '⏸' : '▶';

                    if (t.durationMs > 0) {
                        const pct = Math.min(100, Math.max(0, (t.progressMs / t.durationMs) * 100));
                        const elProgress = document.getElementById('track-progress');
                        if (elProgress) elProgress.style.width = pct + '%';
                    }
                }
            } catch (err) {
                console.warn('Polling error', err);
            }
        }

        // Live polling every 1.5 seconds
        setInterval(fetchStatus, 1500);
        fetchStatus();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
