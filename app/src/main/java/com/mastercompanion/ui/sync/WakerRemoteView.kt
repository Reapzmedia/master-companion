package com.mastercompanion.ui.sync

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.HostPresence
import com.mastercompanion.data.remote.model.PcPowerStatus
import com.mastercompanion.data.remote.model.TargetPc
import com.mastercompanion.data.remote.model.WakeCommand

@Composable
fun WakerRemoteView(
    hostPresence: HostPresence?,
    targetPc: TargetPc?,
    latestCommand: WakeCommand?,
    stopwatchSeconds: Float,
    isRefreshing: Boolean,
    refreshCooldown: Int,
    onWakeClick: () -> Unit,
    onRefreshStatus: () -> Unit,
    onUnlinkClick: () -> Unit,
    whiteTheme: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pc = targetPc ?: TargetPc()
    val isWaking = latestCommand != null && (latestCommand.status == CommandProgressStatus.PENDING ||
            latestCommand.status == CommandProgressStatus.BROADCASTED ||
            latestCommand.status == CommandProgressStatus.VERIFYING)
    val isOnline = pc.status == PcPowerStatus.ONLINE || latestCommand?.status == CommandProgressStatus.ONLINE

    // Vibrator helper
    fun triggerHapticFeedback(isSuccess: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isSuccess) {
                    // Double pulse for success
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 100, 120), -1))
                } else {
                    vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(60)
            }
        } catch (_: Exception) {}
    }

    // Pulse animation for waking button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isWaking) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val surfaceColor = if (whiteTheme) Color.White else Color(0xFF111111)
    val borderColor = if (whiteTheme) Color(0xFFE0E0E0) else Color(0xFF222222)
    val primaryText = if (whiteTheme) Color.Black else Color.White
    val secondaryText = if (whiteTheme) Color(0xFF666666) else Color(0xFF888888)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ═══ Header ═══
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "POCKET REMOTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Color(0xFF388E3C)
                )
                Text(
                    text = "Wake Gateway",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText
                )
            }

            // Host Presence Pill
            val isHostOnline = hostPresence?.isOnline == true
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isHostOnline) Color(0xFF1B5E20).copy(alpha = 0.25f) else Color(0xFFB71C1C).copy(alpha = 0.25f))
                    .border(
                        1.dp,
                        if (isHostOnline) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFF44336).copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isHostOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                val presenceText = if (isHostOnline) {
                    val level = hostPresence?.batteryLevel ?: 0
                    val status = hostPresence?.batteryStatus?.takeIf { it.isNotBlank() } ?: "AC"
                    val temp = hostPresence?.temperatureC ?: 0f
                    if (temp > 0f) {
                        "Host Online • $level% ($status) • ${"%.1f".format(temp)}°C"
                    } else {
                        "Host Online • $level% ($status)"
                    }
                } else {
                    "Desk Host Offline"
                }

                Text(
                    text = presenceText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isHostOnline) Color(0xFF81C784) else Color(0xFFE57373)
                )
            }
        }

        // ═══ Target PC Card ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (whiteTheme) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "PC",
                            tint = if (isOnline) Color(0xFF4CAF50) else primaryText,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = pc.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryText
                        )
                        Text(
                            text = "MAC: ${pc.macAddress.ifBlank { "Not set" }}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = secondaryText
                        )
                        if (!pc.lastKnownIp.isNullOrBlank()) {
                            Text(
                                text = "IP: ${pc.lastKnownIp}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = secondaryText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Refresh Status Button (Instant, No Rate Limit)
                IconButton(
                    onClick = {
                        triggerHapticFeedback(false)
                        onRefreshStatus()
                    },
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = primaryText,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = primaryText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ═══ Giant Hero Wake Button ═══
        val buttonGlowColor by animateColorAsState(
            targetValue = when {
                isOnline -> Color(0xFF4CAF50)
                isWaking -> Color(0xFFFFB300)
                else -> Color(0xFFE53935)
            },
            animationSpec = tween(400),
            label = "btnColor"
        )

        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            buttonGlowColor.copy(alpha = 0.35f),
                            buttonGlowColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp)
                .clip(CircleShape)
                .background(if (whiteTheme) Color.White else Color(0xFF141414))
                .border(2.dp, buttonGlowColor, CircleShape)
                .clickable {
                    triggerHapticFeedback(false)
                    onWakeClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Wake",
                    tint = buttonGlowColor,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isWaking -> "WAKING..."
                        isOnline -> "PC ONLINE"
                        else -> "WAKE PC"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = primaryText
                )

                // Live stopwatch counter
                if (isWaking || (isOnline && stopwatchSeconds > 0)) {
                    Text(
                        text = "%.1fs".format(stopwatchSeconds),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = buttonGlowColor
                    )
                }
            }
        }

        // ═══ Live Execution Steps ═══
        AnimatedVisibility(visible = latestCommand != null) {
            val cmd = latestCommand ?: return@AnimatedVisibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceColor)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "STATUS TIMELINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = secondaryText
                    )

                    ExecutionStepRow(
                        label = "1. Dispatched to Cloud",
                        isDone = cmd.status != CommandProgressStatus.IDLE,
                        isActive = cmd.status == CommandProgressStatus.PENDING,
                        whiteTheme = whiteTheme
                    )
                    ExecutionStepRow(
                        label = "2. Desk Host Broadcasted WoL",
                        isDone = cmd.status == CommandProgressStatus.BROADCASTED || cmd.status == CommandProgressStatus.VERIFYING || cmd.status == CommandProgressStatus.ONLINE,
                        isActive = cmd.status == CommandProgressStatus.BROADCASTED,
                        whiteTheme = whiteTheme
                    )
                    ExecutionStepRow(
                        label = "3. Detecting Boot (Port 445 SMB)",
                        isDone = cmd.status == CommandProgressStatus.ONLINE,
                        isActive = cmd.status == CommandProgressStatus.VERIFYING,
                        whiteTheme = whiteTheme
                    )
                    ExecutionStepRow(
                        label = if (cmd.status == CommandProgressStatus.ONLINE) {
                            "4. PC is Awake! (Booted in ${cmd.bootDurationMs?.let { "%.1fs".format(it / 1000f) } ?: "8s"})"
                        } else if (cmd.status == CommandProgressStatus.TIMEOUT) {
                            "4. Timed out (PC did not answer in 45s)"
                        } else {
                            "4. Windows Ready"
                        },
                        isDone = cmd.status == CommandProgressStatus.ONLINE,
                        isActive = false,
                        isError = cmd.status == CommandProgressStatus.TIMEOUT || cmd.status == CommandProgressStatus.FAILED,
                        whiteTheme = whiteTheme
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // Footer: Unlink / Switch
        TextButton(
            onClick = onUnlinkClick,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "Unlink / Pair with Another Host",
                fontSize = 13.sp,
                color = secondaryText
            )
        }
    }
}

@Composable
private fun ExecutionStepRow(
    label: String,
    isDone: Boolean,
    isActive: Boolean,
    isError: Boolean = false,
    whiteTheme: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val dotColor = when {
            isError -> Color(0xFFF44336)
            isDone -> Color(0xFF4CAF50)
            isActive -> Color(0xFFFFB300)
            else -> if (whiteTheme) Color(0xFFDDDDDD) else Color(0xFF333333)
        }

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isDone || isActive) (if (whiteTheme) Color.Black else Color.White) else (if (whiteTheme) Color(0xFFAAAAAA) else Color(0xFF666666)),
            fontWeight = if (isActive || isDone) FontWeight.Medium else FontWeight.Normal
        )
    }
}
