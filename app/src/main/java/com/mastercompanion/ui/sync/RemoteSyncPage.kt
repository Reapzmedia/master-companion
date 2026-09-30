package com.mastercompanion.ui.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mastercompanion.data.remote.model.DeviceRole
import com.mastercompanion.data.remote.model.DiscoveredPc

@Composable
fun RemoteSyncPage(
    viewModel: RemoteSyncViewModel = hiltViewModel(),
    whiteTheme: Boolean = false
) {
    val context = LocalContext.current
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val discoveredPcs by viewModel.discoveredPcs.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val refreshCooldown by viewModel.refreshCooldown.collectAsStateWithLifecycle()
    val stopwatchSeconds by viewModel.stopwatchSeconds.collectAsStateWithLifecycle()

    var showAutoDetectDialog by remember { mutableStateOf(false) }
    var showManualEditDialog by remember { mutableStateOf(false) }
    var showWakerKeyDialog by remember { mutableStateOf(false) }

    // QR scanner launcher via ZXing ScanContract
    val qrScannerLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.pairAsWaker(result.contents) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val rootBg = if (whiteTheme) Color(0xFFF8F9FA) else Color.Black
    val surfaceColor = if (whiteTheme) Color.White else Color(0xFF111111)
    val borderColor = if (whiteTheme) Color(0xFFE0E0E0) else Color(0xFF222222)
    val primaryText = if (whiteTheme) Color.Black else Color.White
    val secondaryText = if (whiteTheme) Color(0xFF666666) else Color(0xFF888888)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBg)
    ) {
        when {
            // ═══ Mode 1: Role Chooser (Unset) ═══
            vaultState.role == DeviceRole.UNSET -> {
                RoleSelectionView(
                    onSelectHost = { viewModel.initAsHost() },
                    onSelectWaker = { viewModel.setRole(DeviceRole.WAKER) },
                    whiteTheme = whiteTheme
                )
            }

            // ═══ Mode 2: Desk Host (Gateway) ═══
            vaultState.role == DeviceRole.HOST -> {
                HostGatewayView(
                    vaultId = vaultState.vaultId,
                    shortKey = vaultState.shortKey,
                    targetPcName = vaultState.targetPc?.name ?: "Main PC",
                    targetMac = vaultState.targetPc?.macAddress ?: "",
                    targetBroadcast = vaultState.targetPc?.broadcastIp ?: "192.168.1.255",
                    targetIp = vaultState.targetPc?.lastKnownIp,
                    hostLocalIp = vaultState.hostPresence?.localIp,
                    onAutoDetect = {
                        viewModel.scanForPcs()
                        showAutoDetectDialog = true
                    },
                    onEditManual = { showManualEditDialog = true },
                    onTestWol = {
                        viewModel.testLocalWol { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onResetRole = { viewModel.unlink() },
                    whiteTheme = whiteTheme
                )
            }

            // ═══ Mode 3: Pocket Waker (Remote) ═══
            vaultState.role == DeviceRole.WAKER -> {
                if (!vaultState.isPaired || vaultState.vaultId.isBlank()) {
                    WakerPairingPromptView(
                        onScanQr = {
                            val options = ScanOptions().apply {
                                setPrompt("Scan QR Code from Desk Host")
                                setBeepEnabled(true)
                                setOrientationLocked(false)
                            }
                            qrScannerLauncher.launch(options)
                        },
                        onEnterKey = { showWakerKeyDialog = true },
                        onResetRole = { viewModel.unlink() },
                        whiteTheme = whiteTheme
                    )
                } else {
                    WakerRemoteView(
                        hostPresence = vaultState.hostPresence,
                        targetPc = vaultState.targetPc,
                        latestCommand = vaultState.latestCommand,
                        stopwatchSeconds = stopwatchSeconds,
                        isRefreshing = isRefreshing,
                        refreshCooldown = refreshCooldown,
                        onWakeClick = { viewModel.dispatchWake() },
                        onRefreshStatus = { viewModel.refreshStatus() },
                        onUnlinkClick = { viewModel.unlink() },
                        whiteTheme = whiteTheme
                    )
                }
            }
        }

        // ═══ Dialog 1: Auto-Detect PC Scanner ═══
        if (showAutoDetectDialog) {
            AlertDialog(
                onDismissRequest = { showAutoDetectDialog = false },
                containerColor = surfaceColor,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Discovered PCs on LAN", color = primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = primaryText, strokeWidth = 2.dp)
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isScanning) "Scanning subnet via NetBIOS UDP 137..." else "Select your PC to link MAC address:",
                            fontSize = 12.sp,
                            color = secondaryText
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (discoveredPcs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isScanning) "Probing network devices..." else "No PCs found yet. Make sure your PC is on and connected to the same Wi-Fi.",
                                    fontSize = 13.sp,
                                    color = secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(discoveredPcs) { pc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (whiteTheme) Color(0xFFF5F5F5) else Color(0xFF1E1E1E))
                                            .clickable {
                                                viewModel.selectDiscoveredPc(pc)
                                                showAutoDetectDialog = false
                                                Toast.makeText(context, "Linked to ${pc.name}!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(pc.name, fontWeight = FontWeight.Bold, color = primaryText, fontSize = 14.sp)
                                            Text("MAC: ${pc.macAddress}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secondaryText)
                                            Text("IP: ${pc.ipAddress}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = secondaryText)
                                        }
                                        Icon(Icons.Default.Check, contentDescription = "Select", tint = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.scanForPcs() }) {
                        Text("Rescan", color = primaryText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAutoDetectDialog = false }) {
                        Text("Close", color = secondaryText)
                    }
                }
            )
        }

        // ═══ Dialog 2: Manual Edit PC Dialog ═══
        if (showManualEditDialog) {
            var nameInput by remember { mutableStateOf(vaultState.targetPc?.name ?: "Gaming Rig") }
            var macInput by remember { mutableStateOf(vaultState.targetPc?.macAddress ?: "") }
            var bcastInput by remember { mutableStateOf(vaultState.targetPc?.broadcastIp ?: "192.168.1.255") }
            var ipInput by remember { mutableStateOf(vaultState.targetPc?.lastKnownIp ?: "") }

            AlertDialog(
                onDismissRequest = { showManualEditDialog = false },
                containerColor = surfaceColor,
                title = { Text("Edit PC Details", color = primaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("PC Nickname") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = macInput,
                            onValueChange = { macInput = it.uppercase() },
                            label = { Text("MAC Address (e.g. D8:BB:C1:2A:9F:44)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            label = { Text("PC LAN IP (Optional, e.g. 192.168.1.100)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bcastInput,
                            onValueChange = { bcastInput = it },
                            label = { Text("Subnet Broadcast (Default 192.168.1.255)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Tip: Open CMD on your PC and type 'getmac' to find your MAC.",
                            fontSize = 11.sp,
                            color = secondaryText
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updatePcManually(nameInput, macInput, bcastInput, ipInput)
                            showManualEditDialog = false
                            Toast.makeText(context, "Saved PC details", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualEditDialog = false }) {
                        Text("Cancel", color = secondaryText)
                    }
                }
            )
        }

        // ═══ Dialog 3: Waker Manual 6-Digit Key Input ═══
        if (showWakerKeyDialog) {
            var keyInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showWakerKeyDialog = false },
                containerColor = surfaceColor,
                title = { Text("Enter 6-Digit Pairing Key", color = primaryText, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Type the 6-digit key displayed on your Desk Host screen:", fontSize = 12.sp, color = secondaryText)
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = {
                                if (it.length <= 7) keyInput = it.uppercase()
                            },
                            placeholder = { Text("e.g. 834-192") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (keyInput.isNotBlank()) {
                                viewModel.pairAsWaker(keyInput) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (success) {
                                        showWakerKeyDialog = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Pair")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWakerKeyDialog = false }) {
                        Text("Cancel", color = secondaryText)
                    }
                }
            )
        }
    }
}

@Composable
private fun RoleSelectionView(
    onSelectHost: () -> Unit,
    onSelectWaker: () -> Unit,
    whiteTheme: Boolean
) {
    val primaryText = if (whiteTheme) Color.Black else Color.White
    val secondaryText = if (whiteTheme) Color(0xFF666666) else Color(0xFF888888)
    val surfaceColor = if (whiteTheme) Color.White else Color(0xFF111111)
    val borderColor = if (whiteTheme) Color(0xFFE0E0E0) else Color(0xFF222222)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DEVICE SETUP",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = Color(0xFF388E3C)
        )
        Text(
            text = "Select Device Role",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = primaryText
        )
        Text(
            text = "How are you using this Android device?",
            fontSize = 13.sp,
            color = secondaryText,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Option 1: Desk Host
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable { onSelectHost() }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Computer, contentDescription = "Host", tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp))
                    Text("Desk Host (Gateway)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    Text(
                        "Docked 24/7 on your desk. Connected to your home LAN to wake your PC.",
                        fontSize = 12.sp,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = onSelectHost, modifier = Modifier.fillMaxWidth()) {
                        Text("Set as Desk Host")
                    }
                }
            }

            // Option 2: Pocket Waker
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .clickable { onSelectWaker() }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = "Waker", tint = Color(0xFF2196F3), modifier = Modifier.size(36.dp))
                    Text("Pocket Waker (Remote)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    Text(
                        "Your everyday portable phone. Wakes your PC from anywhere via cellular or remote Wi-Fi.",
                        fontSize = 12.sp,
                        color = secondaryText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = onSelectWaker, modifier = Modifier.fillMaxWidth()) {
                        Text("Set as Waker")
                    }
                }
            }
        }
    }
}

@Composable
private fun HostGatewayView(
    vaultId: String,
    shortKey: String,
    targetPcName: String,
    targetMac: String,
    targetBroadcast: String,
    targetIp: String?,
    hostLocalIp: String? = null,
    onAutoDetect: () -> Unit,
    onEditManual: () -> Unit,
    onTestWol: () -> Unit,
    onResetRole: () -> Unit,
    whiteTheme: Boolean
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val primaryText = if (whiteTheme) Color.Black else Color.White
    val secondaryText = if (whiteTheme) Color(0xFF666666) else Color(0xFF888888)
    val surfaceColor = if (whiteTheme) Color.White else Color(0xFF111111)
    val borderColor = if (whiteTheme) Color(0xFFE0E0E0) else Color(0xFF222222)

    val qrContent = "mastercompanion://pair?v=$vaultId&k=$shortKey" + (if (!hostLocalIp.isNullOrBlank()) "&ip=$hostLocalIp" else "")
    val qrBitmap = remember(qrContent) { generateQrCodeBitmap(qrContent, 420) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ═══ Left Column: QR Code & Key ═══
        Column(
            modifier = Modifier
                .weight(0.9f)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("GATEWAY PAIRING", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color(0xFF4CAF50))
            Text("Scan to Pair Waker", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText)

            // High contrast QR Image
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Pairing QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 6-digit key badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Pairing Key", shortKey))
                        Toast.makeText(context, "Key copied: $shortKey", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "KEY: $shortKey",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = secondaryText, modifier = Modifier.size(16.dp))
            }

            Text(
                text = "Point your pocket phone's camera at this screen or type the 6-digit key to sync instantly.",
                fontSize = 11.sp,
                color = secondaryText,
                textAlign = TextAlign.Center
            )
        }

        // ═══ Right Column: Target PC Setup ═══
        Column(
            modifier = Modifier
                .weight(1.1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("TARGET COMPUTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = secondaryText)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetPcName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryText)
                    Text("MAC: ${targetMac.ifBlank { "Not configured" }}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = secondaryText)
                    if (!targetIp.isNullOrBlank()) {
                        Text("IP: $targetIp", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = secondaryText)
                    }
                    Text("Broadcast: $targetBroadcast", fontSize = 11.sp, color = secondaryText.copy(alpha = 0.7f))

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAutoDetect,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Detect", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onEditManual,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Manual", fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onTestWol,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test WoL Local Broadcast", fontSize = 12.sp)
                    }
                }
            }

            TextButton(
                onClick = onResetRole,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Switch Role / Unlink Gateway", color = secondaryText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WakerPairingPromptView(
    onScanQr: () -> Unit,
    onEnterKey: () -> Unit,
    onResetRole: () -> Unit,
    whiteTheme: Boolean
) {
    val primaryText = if (whiteTheme) Color.Black else Color.White
    val secondaryText = if (whiteTheme) Color(0xFF666666) else Color(0xFF888888)
    val surfaceColor = if (whiteTheme) Color.White else Color(0xFF111111)
    val borderColor = if (whiteTheme) Color(0xFFE0E0E0) else Color(0xFF222222)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.QrCode, contentDescription = "QR", tint = Color(0xFF2196F3), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Pair with Desk Host", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryText)
        Text(
            "Scan the QR code displayed on your Desk Companion or enter its 6-digit key.",
            fontSize = 13.sp,
            color = secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        )

        Button(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR Code")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onEnterKey,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Enter 6-Digit Key")
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onResetRole) {
            Text("Change Device Role", color = secondaryText, fontSize = 12.sp)
        }
    }
}

/**
 * Encodes text into a crisp QR Code ImageBitmap using ZXing.
 */
private fun generateQrCodeBitmap(content: String, sizePx: Int = 400): ImageBitmap {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp.asImageBitmap()
    } catch (_: Exception) {
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
