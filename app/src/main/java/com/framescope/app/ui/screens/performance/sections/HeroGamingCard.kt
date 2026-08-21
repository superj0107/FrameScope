package com.framescope.app.ui.screens.performance.sections

import com.framescope.app.i18n.tr

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framescope.app.gaming.GamingModeState
import com.framescope.app.gaming.VivoOptimizationResult
import com.framescope.app.ui.screens.performance.components.EsportsStatusRow

@Composable
fun HeroGamingCard(
    gamingState: GamingModeState,
    animatedProgress: Float,
    canActivate: Boolean,
    isActive: Boolean,
    isBusy: Boolean,
    activeColor: Color,
    primaryRed: Color,
    vivoOptResult: VivoOptimizationResult?,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(0.25f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            com.framescope.app.ui.components.WovenNetBackground(modifier = Modifier.matchParentSize())
            Column(modifier = Modifier.padding(20.dp)) {

                // ── Title + Status Badge ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            tr("GAMING MODE"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedContent(
                            targetState = gamingState,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                            label = "stateLabel"
                        ) { state ->
                            Text(
                                text = when (state) {
                                    is GamingModeState.Idle -> tr("Inactive")
                                    is GamingModeState.Enabling -> tr("Activating…")
                                    is GamingModeState.Active -> tr("Active")
                                    is GamingModeState.Disabling -> tr("Restoring…")
                                    is GamingModeState.Error -> tr("Error")
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                                color = Color.White
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = when {
                                    isActive -> activeColor.copy(0.1f)
                                    gamingState is GamingModeState.Error -> primaryRed.copy(0.1f)
                                    else -> Color.Gray.copy(0.1f)
                                },
                                shape = CircleShape
                            )
                            .border(
                                1.dp,
                                color = when {
                                    isActive -> activeColor.copy(0.25f)
                                    gamingState is GamingModeState.Error -> primaryRed.copy(0.25f)
                                    else -> Color.Gray.copy(0.2f)
                                },
                                shape = CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when {
                                            isActive -> activeColor
                                            gamingState is GamingModeState.Error -> primaryRed
                                            else -> Color.Gray
                                        },
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isActive -> tr("ACTIVE")
                                    gamingState is GamingModeState.Error -> tr("ERROR")
                                    else -> tr("INACTIVE")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isActive -> activeColor
                                    gamingState is GamingModeState.Error -> primaryRed
                                    else -> Color.Gray
                                }
                            )
                        }
                    }
                }

                // ── Progress Indicator (while busy) ──────────────────────────
                AnimatedVisibility(visible = isBusy) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isActive) activeColor else primaryRed,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val statusText = when (val s = gamingState) {
                            is GamingModeState.Enabling -> s.statusText
                            is GamingModeState.Disabling -> tr("Restoring system state…")
                            else -> ""
                        }
                        Text(
                            text = statusText,
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // ── Error Banner ──────────────────────────────────────────────
                AnimatedVisibility(visible = gamingState is GamingModeState.Error) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryRed.copy(0.08f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = primaryRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = (gamingState as? GamingModeState.Error)?.message ?: "",
                                color = primaryRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Status cards + action button ──────────────────────────────
                if (!isBusy) {
                    if (isActive) {
                        // Esports Optimization Engine status card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF10B981).copy(0.2f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        tr("Esports Optimization Engine Active"),
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF10B981).copy(0.15f))
                                EsportsStatusRow(tr("CPU Priority"), tr("Unrestricted (ACTIVE Bucket)"))
                                EsportsStatusRow(tr("Network Policy"), tr("Firewall & Force Doze Active"))
                                EsportsStatusRow(tr("Display & Touch"), tr("Locked Max Hz & Touch Boost"))
                                EsportsStatusRow(tr("PowerHAL Floor"), tr("Fixed Performance Mode"))
                            }
                        }

                        // Vivo / iQOO Hardware Boost — only visible on Vivo devices with vivoOpt enabled
                        if (vivoOptResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            VivoBoostStatusCard(result = vivoOptResult)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onDeactivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                            containerColor = primaryRed.copy(0.15f),
                                contentColor = primaryRed
                            )
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("Deactivate Gaming Mode"), fontWeight = FontWeight.Bold)
                        }
                    } else if (gamingState is GamingModeState.Error && gamingState.message.contains("Deactivation", ignoreCase = true)) {
                        Button(
                            onClick = onDeactivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryRed,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("Retry Deactivation"), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onActivate,
                            enabled = canActivate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(0.06f),
                                disabledContentColor = Color.Gray
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (canActivate) tr("Activate Gaming Mode") else tr("Complete setup first"),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color.White.copy(0.06f),
                            disabledContentColor = Color.Gray
                        )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Gray,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (gamingState is GamingModeState.Enabling) tr("Activating…") else tr("Restoring…"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Vivo / iQOO Hardware Boost status card ───────────────────────────────────

@Composable
private fun VivoBoostStatusCard(result: VivoOptimizationResult) {
    val accentColor = Color(0xFF3D9BE0) // Vivo brand blue
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.07f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    tr("Vivo T3 Ultra Hardware Boost"),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            HorizontalDivider(color = accentColor.copy(alpha = 0.15f))
            VivoStatusRow(
                tr("Display Mode Lock (Mode 4)"),
                "1080p @ ${result.maxHzApplied} Hz ${tr("Locked")}",
                result.displayModeLock
            )
            VivoStatusRow(tr("Touch Latency Boost"), tr("vtouch.persist Active"), result.touchBoost)
            VivoStatusRow(tr("OEM Game Whitelists"), tr("4 Whitelists Appended"), result.whitelistApplied)
        }
    }
}

@Composable
private fun VivoStatusRow(label: String, detail: String, ok: Boolean) {
    val okColor = Color(0xFF22C55E)
    val failColor = Color(0xFFEF4444)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ok) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (ok) okColor else failColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                detail,
                color = if (ok) Color.Gray else failColor.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
        Text(
            text = if (ok) tr("ON") else tr("FAIL"),
            color = if (ok) okColor else failColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
