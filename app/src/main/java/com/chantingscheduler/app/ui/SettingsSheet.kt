package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.app.NotificationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.chantingscheduler.app.BuildConfig
import com.chantingscheduler.app.data.AppTheme
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.scheduling.SunTimesCalculator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Mini-preview palette for the theme cards (matches the app themes).
private val PreviewLightBg = Color(0xFFF4F3EF)
private val PreviewLightAccent = Color(0xFF2E5E57)
private val PreviewDarkBg = Color(0xFF0E1B1A)
private val PreviewDarkAccent = Color(0xFF6E9A92)
private val PreviewDarkCard = Color(0xFF223936)

/**
 * Settings sheet: visual theme cards (Auto/Light/Dark), a status-first
 * location section with address search, alarm-reliability status rows, and an
 * about section. All state plumbing lives in the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    location: LatLng?,
    onSave: (Double, Double, String?) -> Unit,
    onClear: () -> Unit,
    fadeInSeconds: Int,
    onFadeInChange: (Int) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutesChange: (Int) -> Unit,
    alarmVolumeEnabled: Boolean,
    onAlarmVolumeChange: (Boolean) -> Unit,
    onReplayIntroduction: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // (text, isError) inline feedback under the device-location button.
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var fetching by remember { mutableStateOf(false) }

    // Resolved up front: the callbacks below are not composable contexts.
    val locationSetMessage = stringResource(R.string.location_set)
    val locationFailedMessage = stringResource(R.string.location_failed)
    val locationDeniedMessage = stringResource(R.string.location_denied)

    fun useDeviceLocation() {
        scope.launch {
            fetching = true
            val found = withContext(Dispatchers.IO) { fetchLastKnownLocation(context) }
            fetching = false
            if (found != null) {
                onSave(found.latitude, found.longitude, null)
                message = locationSetMessage to false
            } else {
                message = locationFailedMessage to true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            useDeviceLocation()
        } else {
            message = locationDeniedMessage to true
        }
    }

    val sheetColor = if (isDarkThemeActive()) Color(0xFF1C2D2B) else Color.White

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            // Header.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionOverline(stringResource(R.string.appearance_section))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeCard(
                    label = stringResource(R.string.theme_auto),
                    selected = theme == AppTheme.AUTO,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemeChange(AppTheme.AUTO)
                    },
                    modifier = Modifier.weight(1f),
                ) { AutoThemePreview() }
                ThemeCard(
                    label = stringResource(R.string.theme_light),
                    selected = theme == AppTheme.LIGHT,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemeChange(AppTheme.LIGHT)
                    },
                    modifier = Modifier.weight(1f),
                ) { LightThemePreview() }
                ThemeCard(
                    label = stringResource(R.string.theme_dark),
                    selected = theme == AppTheme.DARK,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemeChange(AppTheme.DARK)
                    },
                    modifier = Modifier.weight(1f),
                ) { DarkThemePreview() }
            }

            // Reduced motion: stops breathing/pulsing ambient loops (the system
            // animator scale being 0 forces the same behavior app-wide).
            val motionStore = remember { com.chantingscheduler.app.data.MotionStore.get(context) }
            var reduceMotion by remember { mutableStateOf(motionStore.reduceMotion.value) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.reduce_motion),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.reduce_motion_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reduceMotion,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        motionStore.setReduceMotion(it)
                        reduceMotion = it
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionOverline(stringResource(R.string.location_section))
            LocationStatusCard(location)

            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        useDeviceLocation()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
                enabled = !fetching,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.locating), style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(
                        AppIcons.Pin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_device_location), style = MaterialTheme.typography.labelLarge)
                }
            }

            message?.let { (text, isError) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            ManualEntrySection(
                onSave = onSave,
                canClear = location != null,
                onClear = onClear,
            )

            Spacer(Modifier.height(24.dp))
            SectionOverline(stringResource(R.string.playback_section))
            PlaybackSection(
                fadeInSeconds = fadeInSeconds,
                onFadeInChange = onFadeInChange,
                snoozeMinutes = snoozeMinutes,
                onSnoozeMinutesChange = onSnoozeMinutesChange,
                alarmVolumeEnabled = alarmVolumeEnabled,
                onAlarmVolumeChange = onAlarmVolumeChange,
            )

            Spacer(Modifier.height(24.dp))
            SectionOverline(stringResource(R.string.reliability_section))
            ReliabilitySection()

            Spacer(Modifier.height(24.dp))
            SectionOverline(stringResource(R.string.about_section))
            AboutSection(
                onReplayIntroduction = onReplayIntroduction,
                onExport = onExport,
                onImport = onImport,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.version_line, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// region Theme cards

@Composable
private fun ThemeCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        label = "themeCardBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(),
        label = "themeCardScale",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) { preview() }
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMock(accent: Color, background: Color, line: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(10.dp),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(line),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.65f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(line),
            )
        }
    }
}

@Composable
private fun LightThemePreview() {
    MiniMock(
        accent = PreviewLightAccent,
        background = PreviewLightBg,
        line = Color.White,
    )
}

@Composable
private fun DarkThemePreview() {
    MiniMock(
        accent = PreviewDarkAccent,
        background = PreviewDarkBg,
        line = PreviewDarkCard,
    )
}

@Composable
private fun AutoThemePreview() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    0.5f to PreviewLightBg,
                    0.5f to PreviewDarkBg,
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(PreviewDarkAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "A",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0B1514),
            )
        }
    }
}

// endregion

// region Location

@Composable
private fun LocationStatusCard(location: LatLng?) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (location != null) {
                val today = LocalDate.now()
                val sunrise = remember(location) { SunTimesCalculator.sunrise(today, location) }
                val sunset = remember(location) { SunTimesCalculator.sunset(today, location) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Pin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        location.address
                            ?: "%.4f, %.4f".format(location.latitude, location.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SunStatChip(
                        label = stringResource(R.string.sunrise_label),
                        value = sunrise?.format(TIME_FORMAT) ?: "—",
                        isSunrise = true,
                        modifier = Modifier.weight(1f),
                    )
                    SunStatChip(
                        label = stringResource(R.string.sunset_label),
                        value = sunset?.format(TIME_FORMAT) ?: "—",
                        isSunrise = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Alert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.no_location_set),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.no_location_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SunStatChip(label: String, value: String, isSunrise: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSunrise) AppIcons.Sunrise else AppIcons.Sunset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ManualEntrySection(
    onSave: (Double, Double, String?) -> Unit,
    canClear: Boolean,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchAvailable by remember { mutableStateOf(true) }
    var chosenAddress by remember { mutableStateOf<String?>(null) }

    val lat = latText.toDoubleOrNull()
    val lng = lngText.toDoubleOrNull()
    val latValid = lat != null && lat in -90.0..90.0
    val lngValid = lng != null && lng in -180.0..180.0
    val inputsValid = latValid && lngValid

    // Debounced address search.
    LaunchedEffect(query, expanded) {
        val q = query.trim()
        if (!expanded || q.length < 3 || !searchAvailable) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        if (!Geocoder.isPresent()) {
            searchAvailable = false
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        val found = geocode(context, q)
        searching = false
        results = found.orEmpty()
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "manualChevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                }
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Pencil,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.enter_manually),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(),
            exit = shrinkVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        chosenAddress = null
                    },
                    label = { Text(stringResource(R.string.search_for_place)) },
                    leadingIcon = {
                        Icon(
                            AppIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = manualFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!searchAvailable) {
                    Text(
                        stringResource(R.string.search_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                results.forEach { address ->
                    AddressSuggestionRow(
                        address = address,
                        onClick = {
                            latText = "%.5f".format(address.latitude)
                            lngText = "%.5f".format(address.longitude)
                            chosenAddress = address.getAddressLine(0)
                                ?: suggestionLabel(address)
                            results = emptyList()
                            query = ""
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            chosenAddress = null
                        },
                        label = { Text(stringResource(R.string.latitude)) },
                        isError = latText.isNotBlank() && !latValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = manualFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            lngText = it
                            chosenAddress = null
                        },
                        label = { Text(stringResource(R.string.longitude)) },
                        isError = lngText.isNotBlank() && !lngValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = manualFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { if (lat != null && lng != null) onSave(lat, lng, chosenAddress) },
                        enabled = inputsValid,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(stringResource(R.string.save_location), style = MaterialTheme.typography.labelLarge)
                    }
                    if (canClear) {
                        TextButton(onClick = onClear) {
                            Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun manualFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
)

@Composable
private fun AddressSuggestionRow(address: Address, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.Pin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                suggestionLabel(address),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun suggestionLabel(address: Address): String =
    listOfNotNull(address.featureName, address.locality, address.countryName)
        .distinct()
        .joinToString(", ")
        .ifBlank { address.getAddressLine(0) ?: "${address.latitude}, ${address.longitude}" }

// endregion

// region Playback

@Composable
private fun PlaybackSection(
    fadeInSeconds: Int,
    onFadeInChange: (Int) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutesChange: (Int) -> Unit,
    alarmVolumeEnabled: Boolean,
    onAlarmVolumeChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                Text(
                    stringResource(R.string.volume_fade_in),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.volume_fade_in_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OptionChips(
                    options = PlaybackSettingsStore.FADE_IN_OPTIONS.map {
                        it to fadeLabel(it)
                    },
                    selected = fadeInSeconds,
                    onSelect = onFadeInChange,
                )
            }
            Column {
                Text(
                    stringResource(R.string.snooze_length),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.snooze_length_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OptionChips(
                    options = PlaybackSettingsStore.SNOOZE_OPTIONS.map { it to stringResource(R.string.snooze_minutes, it) },
                    selected = snoozeMinutes,
                    onSelect = onSnoozeMinutesChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.alarm_volume_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.alarm_volume_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = alarmVolumeEnabled,
                    onCheckedChange = onAlarmVolumeChange,
                )
            }
        }
    }
}

@Composable
private fun fadeLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.off)
    seconds % 60 == 0 -> stringResource(R.string.fade_minutes, seconds / 60)
    else -> stringResource(R.string.fade_seconds, seconds)
}

@Composable
private fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(value)
                },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 9.dp),
                )
            }
        }
    }
}

// endregion

// region Alarms & reliability

@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    // Re-read statuses when the sheet resumes (e.g. back from system settings).
    var resumeTick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exactGranted = resumeTick.let {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager?.canScheduleExactAlarms() != false
    }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val batteryExempt = resumeTick.let {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    // Test-sound beeper.
    var testing by remember { mutableStateOf(false) }
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
    }
    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.stopTone()
            toneGenerator.release()
        }
    }
    LaunchedEffect(testing) {
        if (testing) {
            delay(3200)
            testing = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusRow(
            icon = AppIcons.Bell,
            title = stringResource(R.string.exact_alarms_row),
            status = if (exactGranted) stringResource(R.string.status_allowed) else stringResource(R.string.status_needed),
            statusIsError = !exactGranted,
            actionLabel = if (exactGranted) null else stringResource(R.string.fix),
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }
            },
        )
        StatusRow(
            icon = AppIcons.BatteryShield,
            title = stringResource(R.string.battery_row),
            status = if (batteryExempt) stringResource(R.string.status_unrestricted) else stringResource(R.string.status_restricted),
            statusIsError = !batteryExempt,
            actionLabel = if (batteryExempt) null else stringResource(R.string.fix),
            onAction = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ gates full-screen (locked-screen) alarm pages behind a
            // special permission; without it the ringing page degrades to a
            // heads-up notification.
            val canFullScreen = resumeTick.let {
                context.getSystemService(NotificationManager::class.java)
                    ?.canUseFullScreenIntent() == true
            }
            StatusRow(
                icon = AppIcons.Bell,
                title = stringResource(R.string.full_screen_row),
                status = if (canFullScreen) stringResource(R.string.status_allowed) else stringResource(R.string.status_needed),
                statusIsError = !canFullScreen,
                actionLabel = if (canFullScreen) null else stringResource(R.string.fix),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                },
            )
        }
        Text(
            stringResource(R.string.reliability_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
        )
        StatusRow(
            icon = AppIcons.MusicNote,
            title = stringResource(R.string.test_sound_row),
            status = if (testing) stringResource(R.string.test_sound_playing) else stringResource(R.string.test_sound_status),
            statusIsError = false,
            actionLabel = if (testing) stringResource(R.string.test_sound_stop) else stringResource(R.string.test_sound_play),
            onAction = {
                if (testing) {
                    toneGenerator.stopTone()
                    testing = false
                } else {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 3000)
                    testing = true
                }
            },
        )
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    status: String,
    statusIsError: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusIsError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

// endregion

// region About

@Composable
private fun AboutSection(
    onReplayIntroduction: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusRow(
            icon = AppIcons.Info,
            title = stringResource(R.string.app_name),
            status = "v${BuildConfig.VERSION_NAME}",
            statusIsError = false,
            actionLabel = null,
            onAction = {},
        )
        StatusRow(
            icon = AppIcons.Repeat,
            title = stringResource(R.string.replay_introduction),
            status = "",
            statusIsError = false,
            actionLabel = stringResource(R.string.replay),
            onAction = onReplayIntroduction,
        )
        StatusRow(
            icon = AppIcons.MusicNote,
            title = stringResource(R.string.export_row),
            status = stringResource(R.string.export_status),
            statusIsError = false,
            actionLabel = stringResource(R.string.export_action),
            onAction = onExport,
        )
        StatusRow(
            icon = AppIcons.Bell,
            title = stringResource(R.string.import_row),
            status = stringResource(R.string.import_status),
            statusIsError = false,
            actionLabel = stringResource(R.string.import_action),
            onAction = onImport,
        )
    }
}

// endregion

@SuppressLint("MissingPermission") // guarded by the caller's permission check
private fun fetchLastKnownLocation(context: Context): android.location.Location? {
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
    return listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).mapNotNull { provider ->
        runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}

/** Geocoder lookup, async overload on API 33+, sync (IO) below. Null on error. */
private suspend fun geocode(context: Context, query: String): List<Address>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                Geocoder(context, Locale.getDefault()).getFromLocationName(
                    query,
                    5,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(null)
                        }
                    },
                )
            }.onFailure { cont.resume(null) }
        }
    } else {
        withContext(Dispatchers.IO) { geocodeSync(context, query) }
    }

@Suppress("DEPRECATION") // sync overload, used only below API 33
private fun geocodeSync(context: Context, query: String): List<Address>? =
    runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5)
    }.getOrNull()
