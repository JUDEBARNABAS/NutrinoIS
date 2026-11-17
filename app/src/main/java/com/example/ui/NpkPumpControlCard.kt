package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.NpkDosingPlan
import com.example.bluetooth.NpkThresholds
import com.example.bluetooth.NutrientPumpDecision
import com.example.bluetooth.PairedBluetoothDevice
import com.example.bluetooth.PumpActiveState
import java.util.Locale

@Composable
fun NpkPumpControlCard(
    connectionState: BluetoothConnectionState,
    connectedDeviceName: String,
    dosingPlan: NpkDosingPlan?,
    pumpActiveState: PumpActiveState,
    autoTriggerEnabled: Boolean,
    lastNotice: String,
    bluetoothLogs: List<String>,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onAutoTriggerChange: (Boolean) -> Unit,
    onTriggerNeededPumps: () -> Unit,
    onEmergencyStopAll: () -> Unit,
    onManualPumpToggle: (Char, Boolean) -> Unit,
    onEditThresholdsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBtLogs by remember { mutableStateOf(false) }

    val isConnected = connectionState == BluetoothConnectionState.CONNECTED
    val isConnecting = connectionState == BluetoothConnectionState.CONNECTING

    val statusColor = when (connectionState) {
        BluetoothConnectionState.CONNECTED -> Color(0xFF2E7D32)
        BluetoothConnectionState.CONNECTING -> Color(0xFFE65100)
        BluetoothConnectionState.ERROR -> Color(0xFFC62828)
        BluetoothConnectionState.DISCONNECTED -> Color(0xFF616161)
    }

    val statusLabel = when (connectionState) {
        BluetoothConnectionState.CONNECTED -> "HC-05: $connectedDeviceName"
        BluetoothConnectionState.CONNECTING -> "Connecting HC-05..."
        BluetoothConnectionState.ERROR -> "Bluetooth Error"
        BluetoothConnectionState.DISCONNECTED -> "HC-05 Disconnected"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, if (isConnected) Color(0xFFA5D6A7) else Color(0xFFDCE5D8))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                BluetoothConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                                BluetoothConnectionState.ERROR -> Icons.Default.BluetoothDisabled
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "HC-05 Bluetooth Pump Link",
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NPK Nutrient Pump Trigger",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF191D17)
                        )
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEditThresholdsClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure NPK Targets",
                            tint = Color(0xFF386A20),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Button(
                        onClick = { if (isConnected) onDisconnectClick() else onConnectClick() },
                        enabled = !isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) Color(0xFFC62828) else Color(0xFF1565C0),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bluetooth_connect_button")
                    ) {
                        Text(
                            text = if (isConnected) "Disconnect" else if (isConnecting) "Linking..." else "Connect HC-05",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Auto-trigger toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF2F5F0))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Dose on Ground Truth Read",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF191D17)
                    )
                    Text(
                        text = "Automatically triggers N/P/K relays that need adding",
                        fontSize = 10.sp,
                        color = Color(0xFF42493F)
                    )
                }
                Switch(
                    checked = autoTriggerEnabled,
                    onCheckedChange = onAutoTriggerChange,
                    modifier = Modifier.testTag("auto_trigger_pumps_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF386A20)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Three N-P-K Pump Relay Tiles
            if (dosingPlan != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NutrientPumpTile(
                        decision = dosingPlan.nitrogen,
                        isPumping = pumpActiveState.nActive,
                        isBtConnected = isConnected,
                        onToggleManual = { onManualPumpToggle('N', !pumpActiveState.nActive) },
                        testTag = "pump_n_toggle",
                        modifier = Modifier.weight(1f)
                    )
                    NutrientPumpTile(
                        decision = dosingPlan.phosphorus,
                        isPumping = pumpActiveState.pActive,
                        isBtConnected = isConnected,
                        onToggleManual = { onManualPumpToggle('P', !pumpActiveState.pActive) },
                        testTag = "pump_p_toggle",
                        modifier = Modifier.weight(1f)
                    )
                    NutrientPumpTile(
                        decision = dosingPlan.potassium,
                        isPumping = pumpActiveState.kActive,
                        isBtConnected = isConnected,
                        onToggleManual = { onManualPumpToggle('K', !pumpActiveState.kActive) },
                        testTag = "pump_k_toggle",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Evaluation / Last Trigger Notice Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (pumpActiveState.anyActive) Color(0xFFFFF3E0)
                        else if (dosingPlan?.anyPumpNeeded == true) Color(0xFFFCE4EC)
                        else Color(0xFFE8F5E9)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = lastNotice,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF191D17),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Trigger + Emergency Stop Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val anyNeeded = dosingPlan?.anyPumpNeeded == true
                Button(
                    onClick = onTriggerNeededPumps,
                    enabled = anyNeeded,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (anyNeeded) Color(0xFF386A20) else Color(0xFFBDBDBD),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
                        .testTag("trigger_npk_pumps_button")
                ) {
                    Icon(
                        imageVector = if (anyNeeded) Icons.Default.PlayArrow else Icons.Default.CheckCircle,
                        contentDescription = "Trigger Needed Nutrient Pumps",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (anyNeeded) "Dose Needed NPK" else "All NPK Optimal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onEmergencyStopAll,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("stop_all_pumps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Emergency Stop All Pumps",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "STOP ALL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Collapsible HC-05 Bluetooth TX/RX Log
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBtLogs = !showBtLogs }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HC-05 Relay Serial Log (${bluetoothLogs.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF42493F)
                )
                Icon(
                    imageVector = if (showBtLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Bluetooth Logs",
                    tint = Color(0xFF42493F),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = showBtLogs,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF191D17))
                        .padding(8.dp)
                ) {
                    if (bluetoothLogs.isEmpty()) {
                        Text(
                            text = "No HC-05 Bluetooth traffic yet.",
                            color = Color(0xFF81C784),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        LazyColumn {
                            items(bluetoothLogs) { line ->
                                Text(
                                    text = line,
                                    color = if (line.contains("failed", true) || line.contains("Error", true)) {
                                        Color(0xFFE57373)
                                    } else if (line.contains("TX >")) {
                                        Color(0xFFFFF59D)
                                    } else {
                                        Color(0xFFA5D6A7)
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientPumpTile(
    decision: NutrientPumpDecision,
    isPumping: Boolean,
    isBtConnected: Boolean,
    onToggleManual: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val tileBg = when {
        isPumping -> Color(0xFFFFF3E0)
        decision.needsAdding -> Color(0xFFFFEBEE)
        else -> Color(0xFFE8F5E9)
    }
    val tileBorder = when {
        isPumping -> Color(0xFFE65100)
        decision.needsAdding -> Color(0xFFEF9A9A)
        else -> Color(0xFFA5D6A7)
    }
    val accent = when {
        isPumping -> Color(0xFFE65100)
        decision.needsAdding -> Color(0xFFC62828)
        else -> Color(0xFF2E7D32)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = tileBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, tileBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Pump ${decision.nutrientCode}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${decision.currentValue}/${decision.targetMin}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191D17)
            )
            Text(
                text = when {
                    isPumping -> "PUMPING"
                    decision.needsAdding -> "ADD ${String.format(Locale.US, "%.1fs", decision.durationMs / 1000.0)}"
                    else -> "OPTIMAL"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onToggleManual,
                enabled = isBtConnected,
                border = BorderStroke(1.dp, accent),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = if (isPumping) "STOP" else "TEST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
    }
}

@Composable
fun BluetoothDevicePickerDialog(
    pairedDevices: List<PairedBluetoothDevice>,
    onRefresh: () -> Unit,
    onSelectDevice: (PairedBluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select HC-05 Bluetooth",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF191D17)
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Paired Devices",
                            tint = Color(0xFF386A20)
                        )
                    }
                }
                Text(
                    text = "Pair your HC-05 module (default PIN 1234 or 0000) in Android Bluetooth Settings, then tap it below.",
                    fontSize = 11.sp,
                    color = Color(0xFF42493F)
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFDCE5D8))
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.heightIn(max = 260.dp)) {
                    if (pairedDevices.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No paired Bluetooth devices found.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF42493F)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Bluetooth Settings", fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pairedDevices) { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectDevice(device) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (device.isLikelyHc05) Color(0xFFE8F5E9) else Color(0xFFF2F5F0)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (device.isLikelyHc05) Color(0xFF81C784) else Color(0xFFDCE5D8)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = device.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF191D17)
                                            )
                                            Text(
                                                text = device.address,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF42493F)
                                            )
                                        }
                                        if (device.isLikelyHc05) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF2E7D32))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "HC-05",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Pair New Device", fontSize = 11.sp, color = Color(0xFF1565C0))
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF72796F)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun NpkThresholdDialog(
    currentThresholds: NpkThresholds,
    onSave: (minN: Int, minP: Int, minK: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var nInput by remember { mutableStateOf(currentThresholds.minNitrogen.toString()) }
    var pInput by remember { mutableStateOf(currentThresholds.minPhosphorus.toString()) }
    var kInput by remember { mutableStateOf(currentThresholds.minPotassium.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NPK Auto-Dose Thresholds",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF191D17)
                )
                Text(
                    text = "If Ground Truth falls below these minimums (mg/kg), the corresponding pump triggers.",
                    fontSize = 11.sp,
                    color = Color(0xFF42493F)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nInput,
                    onValueChange = { if (it.all(Char::isDigit)) nInput = it },
                    label = { Text("Min Nitrogen N (mg/kg)", color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = blackTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pInput,
                    onValueChange = { if (it.all(Char::isDigit)) pInput = it },
                    label = { Text("Min Phosphorus P (mg/kg)", color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = blackTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = kInput,
                    onValueChange = { if (it.all(Char::isDigit)) kInput = it },
                    label = { Text("Min Potassium K (mg/kg)", color = Color.Black) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = blackTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF72796F))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                nInput.toIntOrNull() ?: currentThresholds.minNitrogen,
                                pInput.toIntOrNull() ?: currentThresholds.minPhosphorus,
                                kInput.toIntOrNull() ?: currentThresholds.minPotassium
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF386A20)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Save Targets")
                    }
                }
            }
        }
    }
}
