@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lts.control.ui.screens

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lts.control.core.ble.BleViewModel
import com.lts.control.core.ble.model.DeviceState
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.res.stringResource
import com.lts.control.R

/* -------------------------------------------------------------------------- */
/*                                 Entry Point                                */
/* -------------------------------------------------------------------------- */

@Composable
fun SettingsScreen(
    vm: BleViewModel,
    onOpenRespoolAmount: () -> Unit
) {
    val status by vm.status.collectAsState()
    val deviceState by vm.deviceState.collectAsState()
    val highSpeed by vm.highSpeed.collectAsState(initial = false)

    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    var showFahrenheit by remember { mutableStateOf(prefs.temperatureInFahrenheit) }
    var notificationsOn by remember { mutableStateOf(prefs.notificationsEnabled) }

    val requestNotifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            notificationsOn = granted
            prefs.notificationsEnabled = granted
        }
    )
    fun ensureNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Ältere Android-Versionen: aktiv ist gleich "erlaubt"
            notificationsOn = true
            prefs.notificationsEnabled = true
        }
    }

    val chipTemp = status?.chipTemperatureC
    val tempCritical = (chipTemp ?: 0) >= 65

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 12.dp)
    ) {
        /* ------------------------------ Status-Kapsel ------------------------------ */
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            val (icon, tint) = when {
                status == null -> Icons.Rounded.SignalCellularConnectedNoInternet0Bar to LocalContentColor.current.copy(alpha = 0.75f)
                else -> when (deviceState) {
                    DeviceState.RUNNING   -> Icons.Rounded.PlayCircle   to Color(0xFF2E7D32)
                    DeviceState.PAUSED    -> Icons.Rounded.PauseCircle  to Color(0xFFFFA000)
                    DeviceState.UPDATING  -> Icons.Rounded.SystemUpdateAlt to Color(0xFF3F51B5)
                    DeviceState.DONE      -> Icons.Filled.Check         to Color(0xFF2ECC71)
                    DeviceState.AUTO_STOP -> Icons.Rounded.Warning      to Color(0xFFD32F2F)
                    DeviceState.IDLE,
                    DeviceState.ERROR     -> Icons.Rounded.PowerSettingsNew to Color(0xFF0C4C98)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(25))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .height(50.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(12.dp))
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.status), style = MaterialTheme.typography.titleSmall)
                    val subtitle = stringResource(deviceStateLabel(deviceState))
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "stateText"
                    ) { state ->
                        Text(state, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
        }

        /* -------------------------------- Konfiguration ------------------------------- */
        SectionHeader(stringResource(R.string.configuration))
        SettingsCard {
            // Jingle
            RowSetting(
                title = stringResource(R.string.sound_on_completion),
                trailing = {
                    var expanded by remember { mutableStateOf(false) }
                    val jingleLabels = mapOf(
                        0 to stringResource(R.string.jingle_off),
                        1 to stringResource(R.string.jingle_simple),
                        2 to stringResource(R.string.jingle_glissando),
                        3 to stringResource(R.string.jingle_star_wars)
                    )
                    val current = jingleLabels[status?.jingleStyle ?: 0] ?: jingleLabels[0]!!
                    Box {
                        Text(current, color = Color.Gray, modifier = Modifier
                            .clip(RoundedCornerShape(8))
                            .clickable { expanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            jingleLabels.forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        vm.setJingle(tag)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            // LED Helligkeit (0..100, Schritt 10)
            var ledLocal by remember { mutableStateOf((status?.ledBrightness ?: 50).toFloat()) }
            LaunchedEffect(status?.ledBrightness) { ledLocal = (status?.ledBrightness ?: 50).toFloat() }
            SliderRow(
                title = stringResource(R.string.led_brightness),
                valueText = "${ledLocal.toInt()} %",
                value = ledLocal,
                valueRange = 0f..100f,
                steps = 9,
                onChange = { ledLocal = it },
                onFinish = { vm.setLed(ledLocal.toInt()) }
            )

            // Respool-Menge (zeigt aktuelle Auswahl)
            RowSetting(
                title = stringResource(R.string.respool_amount),
                subtitle = stringResource(targetWeightLabel(status?.targetWeight ?: 0)),
                clickable = true,
                onClick = onOpenRespoolAmount
            )

            // Filament Sensor
            ToggleRow(
                title = stringResource(R.string.use_filament_sensor),
                checked = status?.useFilamentSensor ?: true,
                onChecked = { vm.setFilamentSensor(it) }
            )
        }
        SectionFooter(stringResource(R.string.filament_sensor_help))

        /* ----------------------------------- Motor ----------------------------------- */
        SectionHeader(stringResource(R.string.motor))
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.reverse_direction),
                checked = (status?.motorDirection ?: 0) == 1,
                onChecked = { on -> vm.setDirection(if (on) 1 else 0) }
            )

            // Stärke 80..120 in 10er Schritten
            var powLocal by remember { mutableStateOf((status?.motorStrength ?: 100).toFloat()) }
            LaunchedEffect(status?.motorStrength) { powLocal = (status?.motorStrength ?: 100).toFloat() }
            StepperRow(
                title = stringResource(R.string.strength),
                valueText = "${powLocal.toInt()} %",
                value = powLocal,
                range = 80f..120f,
                step = 10f,
                onChange = { powLocal = it },
                onFinish = { vm.setMotorStrength(powLocal.toInt()) }
            )

            // Auto-Stopp Empfindlichkeit (0..3)
            var tlLocal by remember { mutableStateOf((status?.torqueLimit ?: 0).toFloat()) }
            LaunchedEffect(status?.torqueLimit) { tlLocal = (status?.torqueLimit ?: 0).toFloat() }
            val hs = highSpeed
            PickerRow(
                title = stringResource(R.string.auto_stop_sensitivity),
                enabled = !hs,
                currentLabel = when (tlLocal.toInt()) {
                    1 -> stringResource(R.string.sensitivity_low)
                    2 -> stringResource(R.string.sensitivity_medium)
                    3 -> stringResource(R.string.sensitivity_high)
                    else -> stringResource(R.string.sensitivity_off)
                },
                onNext = {
                    val next = (tlLocal.toInt() + 1).coerceAtMost(3)
                    tlLocal = next.toFloat()
                    vm.setTorque(next)
                },
                onPrev = {
                    val prev = (tlLocal.toInt() - 1).coerceAtLeast(0)
                    tlLocal = prev.toFloat()
                    vm.setTorque(prev)
                }
            )
        }
        SectionFooter(stringResource(R.string.auto_stop_help))

        /* -------------------------------- High-Speed --------------------------------- */
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.high_speed),
                checked = highSpeed,
                onChecked = { vm.setHighSpeed(it) }
            )
        }
        SectionFooter(stringResource(R.string.high_speed_help))

        /* ----------------------------------- Lüfter ---------------------------------- */
        SectionHeader(stringResource(R.string.fan))
        SettingsCard {
            var fanLocal by remember { mutableStateOf((status?.fanSpeed ?: 60).toFloat()) }
            LaunchedEffect(status?.fanSpeed) { fanLocal = (status?.fanSpeed ?: 60).toFloat() }
            StepperRow(
                title = stringResource(R.string.speed),
                valueText = "${fanLocal.toInt()} %",
                value = fanLocal,
                range = 10f..100f,
                step = 10f,
                onChange = { fanLocal = it },
                onFinish = { vm.setFanSpeed(fanLocal.toInt()) }
            )
            ToggleRow(
                title = stringResource(R.string.fan_always_on),
                checked = status?.fanAlwaysOn ?: (status?.fanAlwaysOn == true),
                onChecked = { vm.setFanAlways(it) }
            )
            // Temperatur-Einheit (Segment)
            SegmentedRow(
                title = stringResource(R.string.temperature_unit),
                options = listOf(stringResource(R.string.celsius), stringResource(R.string.fahrenheit)),
                selectedIndex = if (showFahrenheit) 1 else 0
            ) { idx: Int ->
                showFahrenheit = (idx == 1)
                prefs.temperatureInFahrenheit = showFahrenheit
            }
        }
        SectionFooter(stringResource(R.string.fan_help))

        /* -------------------------------- Kalibrierung ------------------------------- */
        SectionHeader(stringResource(R.string.calibration))
        SettingsCard {
            var durLocal by remember { mutableStateOf((status?.durationAt80 ?: 895).toFloat()) }
            LaunchedEffect(status?.durationAt80) { durLocal = (status?.durationAt80 ?: 895).toFloat() }
            StepperRow(
                title = stringResource(R.string.duration),
                valueText = formatMinutesSeconds(durLocal.toInt()),
                value = durLocal,
                range = 5f..2000f,
                step = 5f,
                onChange = { durLocal = it },
                onFinish = { vm.setDurationAt80(durLocal.toInt()) }
            )
        }
        SectionFooter(stringResource(R.string.calibration_help))

        /* ------------------------------------- App ----------------------------------- */
        SectionHeader(stringResource(R.string.app_section))
        SettingsCard {
            ToggleRow(
                title = stringResource(R.string.notifications),
                checked = notificationsOn,
                onChecked = { on ->
                    val ctx = context
                    if (on) {
                        ensureNotificationPermission(ctx)
                    } else {
                        notificationsOn = false
                        prefs.notificationsEnabled = false
                    }
                }
            )
        }
        SectionFooter(stringResource(R.string.notifications_help))
        Spacer(Modifier.height(18.dp))
    }
}

/* -------------------------------------------------------------------------- */
/*                        Respool Amount (Subscreen/Sheet)                    */
/* -------------------------------------------------------------------------- */

@Composable
fun RespoolAmountScreen(
    vm: BleViewModel,
    onBack: () -> Unit
) {
    val status by vm.status.collectAsState()
    val current = status?.targetWeight ?: 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.respool_amount)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Entire Spool
            SectionHeaderSpacer()
            SettingsCard {
                SingleChoiceRow(
                    title = stringResource(R.string.entire_spool),
                    selected = current == 0,
                    onClick = { if (current != 0) vm.setTargetWeight(0) }
                )
            }
            SectionFooter(stringResource(R.string.entire_spool_help))

            // Fixed weights
            SectionHeaderSpacer()
            SettingsCard {
                listOf(1 to stringResource(R.string.weight_1kg), 2 to stringResource(R.string.weight_05kg), 3 to stringResource(R.string.weight_025kg)).forEach { (tag, label) ->
                    SingleChoiceRow(
                        title = label,
                        selected = current == tag,
                        onClick = { if (current != tag) vm.setTargetWeight(tag) }
                    )
                }
            }
            SectionFooter(
                stringResource(R.string.fixed_weight_help)
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                   UI Bits                                  */
/* -------------------------------------------------------------------------- */

@Composable private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary
    )
}
@Composable private fun SectionHeaderSpacer() { Spacer(Modifier.height(8.dp)) }

@Composable private fun SectionFooter(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable private fun RowSetting(
    title: String,
    subtitle: String? = null,
    clickable: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val base = Modifier.fillMaxWidth()
    val mod = if (clickable) base.clickable { onClick() } else base
    ListItem(
        modifier = mod,
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            when {
                trailing != null -> trailing()
                clickable -> Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
    )
    Divider()
}

@Composable private fun ToggleRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    RowSetting(title = title, trailing = {
        Switch(checked = checked, onCheckedChange = onChecked)
    })
}

@Composable private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(valueText, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        )
        Slider(
            value = value,
            onValueChange = { onChange(it) },
            onValueChangeFinished = onFinish,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Divider()
    }
}

@Composable private fun StepperRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(valueText, style = MaterialTheme.typography.bodySmall, color = Color.Gray) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val next = max(range.start, value - step)
                        if (next != value) { onChange(next); onFinish() }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) { Text("–") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val next = min(range.endInclusive, value + step)
                        if (next != value) { onChange(next); onFinish() }
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) { Text("+") }
            }
        }
    )
    Divider()
}


@Composable private fun PickerRow(
    title: String,
    enabled: Boolean,
    currentLabel: String,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev, enabled = enabled) { Text("–") }
                Text(currentLabel, color = if (enabled) LocalContentColor.current else Color.Gray)
                TextButton(onClick = onNext, enabled = enabled) { Text("+") }
            }
        }
    )
    Divider()
}

@Composable private fun SegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = idx == selectedIndex,
                        onClick = { onSelected(idx) },
                        label = { Text(label) },
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    )
    Divider()
}

@Composable private fun SingleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
    Divider()
}

/* -------------------------------------------------------------------------- */
/*                                   Utils                                    */
/* -------------------------------------------------------------------------- */

private fun deviceStateLabel(state: DeviceState): Int = when (state) {
    DeviceState.IDLE      -> R.string.state_ready
    DeviceState.RUNNING   -> R.string.state_running_short
    DeviceState.PAUSED    -> R.string.state_paused
    DeviceState.AUTO_STOP -> R.string.state_auto_stop
    DeviceState.UPDATING  -> R.string.state_updating
    DeviceState.DONE      -> R.string.state_done
    DeviceState.ERROR     -> R.string.state_error
}

private fun targetWeightLabel(tag: Int): Int = when (tag) {
    1 -> R.string.weight_1kg
    2 -> R.string.weight_05kg
    3 -> R.string.weight_025kg
    else -> R.string.entire_spool
}

@Composable
private fun formatMinutesSeconds(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return stringResource(R.string.duration_format, m, r)
}

/* ---------------------------- Simple App Prefs ----------------------------- */

private class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("lts_prefs", Context.MODE_PRIVATE)
    var temperatureInFahrenheit: Boolean
        get() = sp.getBoolean("temperatureInFahrenheit", false)
        set(v) { sp.edit().putBoolean("temperatureInFahrenheit", v).apply() }
    var notificationsEnabled: Boolean
        get() = sp.getBoolean("notificationsEnabled", false)
        set(v) { sp.edit().putBoolean("notificationsEnabled", v).apply() }
}