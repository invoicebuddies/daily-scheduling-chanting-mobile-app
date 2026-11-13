package com.chantingscheduler.app.ui

import com.chantingscheduler.app.R
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.RoutineStepEntity
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.ScheduleWithSounds
import com.chantingscheduler.app.data.SoundItemEntity
import com.chantingscheduler.app.data.TriggerType
import com.chantingscheduler.app.playback.BuiltinSounds
import com.chantingscheduler.app.playback.SoundPreviewPlayer
import com.chantingscheduler.app.scheduling.SunTimesCalculator
import com.chantingscheduler.app.ui.motion.LocalMotion
import com.chantingscheduler.app.ui.motion.MotionTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_NAMES = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_FULL_NAME_KEYS = listOf(
    R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
    R.string.day_fri, R.string.day_sat, R.string.day_sun,
)
private val TODAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Fixed and lunar triggers fire at an explicit clock time; sun triggers don't. */
private val TriggerType.usesClockTime: Boolean
    get() = this == TriggerType.FIXED || this == TriggerType.FULL_MOON || this == TriggerType.NEW_MOON

/** Lunar triggers ignore the days-of-week mask. */
private val TriggerType.isLunar: Boolean
    get() = this == TriggerType.FULL_MOON || this == TriggerType.NEW_MOON

/** Editable shape of one routine step (silence = null uri/name). */
private data class StepDraft(
    val soundUri: String?,
    val soundName: String?,
    val durationMinutes: Int,
)

private enum class EditMode { PLAYLIST, ROUTINE }

private val AUTO_STOP_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
private val REPEAT_QUICK_CHOICES = listOf(1, 3, 7, 21, 108)
private val VOLUME_OPTIONS = listOf(25, 50, 70, 85, 100)

/** Volume previews audition the first sound for this long. */
private const val VOLUME_PREVIEW_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditScreen(
    initial: ScheduleWithSounds?,
    location: LatLng?,
    globalChantVolumePercent: Int = 100,
    resolveDisplayName: (Uri) -> String,
    onOpenLocationSettings: () -> Unit,
    onSave: (ScheduleEntity, List<SoundItemEntity>, List<RoutineStepEntity>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var label by remember { mutableStateOf(initial?.schedule?.label ?: "") }
    var triggerType by remember { mutableStateOf(initial?.schedule?.triggerType ?: TriggerType.FIXED) }
    var offsetMinutes by remember { mutableIntStateOf(initial?.schedule?.offsetMinutes ?: 0) }
    var hour by remember { mutableIntStateOf(initial?.schedule?.hour ?: 6) }
    var minute by remember { mutableIntStateOf(initial?.schedule?.minute ?: 0) }
    var daysOfWeek by remember { mutableIntStateOf(initial?.schedule?.daysOfWeek ?: 0b1111111) }
    var gapText by remember { mutableStateOf(initial?.schedule?.gapSeconds?.toString() ?: "0") }
    var maxDurationMinutes by remember { mutableIntStateOf(initial?.schedule?.maxDurationMinutes ?: -1) }
    var repeatCount by remember { mutableIntStateOf(initial?.schedule?.repeatCount ?: 1) }
    var reminderMinutes by remember { mutableIntStateOf(initial?.schedule?.reminderMinutes ?: -1) }
    /** Per-schedule chant volume; -1 = Default (follow the global setting). */
    var volumePercent by remember { mutableIntStateOf(initial?.schedule?.volumePercent ?: -1) }
    var mode by remember { mutableStateOf(if (initial?.isRoutine == true) EditMode.ROUTINE else EditMode.PLAYLIST) }
    var expandedRow by remember { mutableStateOf(EditRow.NONE) }
    val sounds = remember {
        mutableStateListOf<SoundItemEntity>().apply {
            initial?.sortedSounds?.let { addAll(it) }
            // New schedules start with a built-in sound so the app works
            // out of the box; the user can remove or reorder it freely.
            if (initial == null && isEmpty()) {
                BuiltinSounds.all(context).firstOrNull()?.let { builtin ->
                    add(
                        SoundItemEntity(
                            scheduleId = 0,
                            uri = builtin.uri,
                            // Stable key, not the localized label — see BuiltinSounds.
                            displayName = builtin.nameKey,
                            position = 0,
                        ),
                    )
                }
            }
        }
    }
    val steps = remember {
        mutableStateListOf<StepDraft>().apply {
            initial?.sortedSteps?.forEach { add(StepDraft(it.soundUri, it.soundName, it.durationMinutes)) }
        }
    }

    val previewingUri by SoundPreviewPlayer.previewingUri.collectAsStateWithLifecycle()

    // Stop any preview when leaving this screen.
    DisposableEffect(Unit) {
        onDispose { SoundPreviewPlayer.stop() }
    }

    var pickTargetIsStep by remember { mutableStateOf(false) }
    var showSoundSourceDialog by remember { mutableStateOf(false) }

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            persistUri(context, uri)
            if (pickTargetIsStep) {
                steps.add(StepDraft(uri.toString(), resolveDisplayName(uri), 5))
            } else {
                sounds.add(
                    SoundItemEntity(
                        scheduleId = 0,
                        uri = uri.toString(),
                        displayName = resolveDisplayName(uri),
                        position = sounds.size,
                    ),
                )
            }
        }
        pickTargetIsStep = false
    }
    val singleSoundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            persistUri(context, uri)
            steps.add(StepDraft(uri.toString(), resolveDisplayName(uri), 5))
        }
        pickTargetIsStep = false
    }

    val canSave = when (mode) {
        EditMode.PLAYLIST ->
            (daysOfWeek != 0 || triggerType.isLunar) && sounds.isNotEmpty() &&
                (triggerType.usesClockTime || location != null)
        EditMode.ROUTINE ->
            (daysOfWeek != 0 || triggerType.isLunar) && steps.isNotEmpty() &&
                (triggerType.usesClockTime || location != null)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_schedule_title)) },
            text = { Text(stringResource(R.string.delete_schedule_body)) },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showSoundSourceDialog) {
        val builtins = BuiltinSounds.all(context)
        AlertDialog(
            onDismissRequest = { showSoundSourceDialog = false },
            title = { Text(stringResource(R.string.add_sound_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    builtins.forEach { builtin ->
                        Surface(
                            onClick = {
                                steps.add(StepDraft(builtin.uri, builtin.nameKey, 5))
                                showSoundSourceDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                builtin.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            showSoundSourceDialog = false
                            singleSoundPicker.launch(arrayOf("audio/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.pick_from_phone),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundSourceDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.set_schedule_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.content_desc_schedule_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.delete_schedule_menu),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TriggerTypeSelector(
                selected = triggerType,
                onSelect = { triggerType = it },
            )
            if (triggerType.usesClockTime) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    TimeDialPicker(
                        hour = hour,
                        minute = minute,
                        onTimeChange = { h, m ->
                            hour = h
                            minute = m
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                SunTriggerEditor(
                    triggerType = triggerType,
                    offsetMinutes = offsetMinutes,
                    location = location,
                    onOffsetChange = { offsetMinutes = it },
                    onOpenLocationSettings = onOpenLocationSettings,
                )
            }

            ModeSelector(
                mode = mode,
                onSelect = { mode = it },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                // Smooth height when the mode switch changes the content.
                Column(Modifier.animateContentSize(animationSpec = MotionTokens.springSlide())) {
                    if (mode == EditMode.PLAYLIST) {
                        if (!triggerType.isLunar) {
                            SettingRow(
                                icon = AppIcons.Repeat,
                                label = stringResource(R.string.repeat_row),
                                summary = repeatSummaryText(daysOfWeek),
                                summaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                expanded = expandedRow == EditRow.REPEAT,
                                onToggle = { expandedRow = EditRow.REPEAT.toggleFrom(expandedRow) },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    DAY_NAMES.forEachIndexed { index, name ->
                                        DayChip(
                                            name = name,
                                            active = daysOfWeek and (1 shl index) != 0,
                                            size = 48.dp,
                                            contentDescription = stringResource(DAY_FULL_NAME_KEYS[index]),
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                daysOfWeek = daysOfWeek xor (1 shl index)
                                            },
                                        )
                                    }
                                }
                            }
                            RowDivider()
                        }

                        SettingRow(
                            icon = AppIcons.MusicNote,
                            label = stringResource(R.string.sounds_row),
                            summary = if (sounds.isEmpty()) stringResource(R.string.not_set) else stringResource(R.string.sound_count_summary, sounds.size),
                            summaryColor = if (sounds.isEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            expanded = expandedRow == EditRow.SOUNDS,
                            onToggle = { expandedRow = EditRow.SOUNDS.toggleFrom(expandedRow) },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (sounds.isEmpty()) {
                                    Text(
                                        stringResource(R.string.no_sounds_yet),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        sounds.forEachIndexed { index, sound ->
                                            SoundItemRow(
                                                index = index,
                                                sound = sound,
                                                isPreviewing = previewingUri == sound.uri,
                                                canMoveUp = index > 0,
                                                canMoveDown = index < sounds.lastIndex,
                                                onPreviewToggle = {
                                                    SoundPreviewPlayer.toggle(context, sound.uri)
                                                },
                                                onMoveUp = {
                                                    sounds.add(index - 1, sounds.removeAt(index))
                                                },
                                                onMoveDown = {
                                                    sounds.add(index + 1, sounds.removeAt(index))
                                                },
                                                onRemove = { sounds.removeAt(index) },
                                            )
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { soundPicker.launch(arrayOf("audio/*")) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                    ) {
                                        Text(stringResource(R.string.add_sounds))
                                    }
                                }
                                AddBuiltinSection(
                                    onAdd = { builtin ->
                                        sounds.add(
                                            SoundItemEntity(
                                                scheduleId = 0,
                                                uri = builtin.uri,
                                                // Stable key, not the localized label.
                                                displayName = builtin.nameKey,
                                                position = sounds.size,
                                            ),
                                        )
                                    },
                                )
                                if (sounds.isEmpty()) {
                                    Text(
                                        stringResource(R.string.need_sound_to_save),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        RowDivider()
                    } else {
                        // Routine mode: ordered steps of sound-or-silence with durations.
                        SettingRow(
                            icon = AppIcons.MusicNote,
                            label = stringResource(R.string.steps_row),
                            summary = if (steps.isEmpty()) stringResource(R.string.not_set) else stringResource(R.string.step_count_summary, steps.size),
                            summaryColor = if (steps.isEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            expanded = expandedRow == EditRow.SOUNDS,
                            onToggle = { expandedRow = EditRow.SOUNDS.toggleFrom(expandedRow) },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                steps.forEachIndexed { index, step ->
                                    StepDraftRow(
                                        index = index,
                                        step = step,
                                        isPreviewing = step.soundUri != null && previewingUri == step.soundUri,
                                        canMoveUp = index > 0,
                                        canMoveDown = index < steps.lastIndex,
                                        onPreviewToggle = {
                                            step.soundUri?.let { SoundPreviewPlayer.toggle(context, it) }
                                        },
                                        onDurationChange = { minutes ->
                                            steps[index] = step.copy(durationMinutes = minutes)
                                        },
                                        onMoveUp = { steps.add(index - 1, steps.removeAt(index)) },
                                        onMoveDown = { steps.add(index + 1, steps.removeAt(index)) },
                                        onRemove = { steps.removeAt(index) },
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showSoundSourceDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        ),
                                    ) {
                                        Text(stringResource(R.string.add_sound_step))
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            steps.add(StepDraft(null, null, 1))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        ),
                                    ) {
                                        Text(stringResource(R.string.add_silence_step))
                                    }
                                }
                                if (steps.isEmpty()) {
                                    Text(
                                        stringResource(R.string.need_step_to_save),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        RowDivider()
                    }

                    SettingRow(
                        icon = AppIcons.Tag,
                        label = stringResource(R.string.label_row),
                        summary = label.trim().ifBlank { stringResource(R.string.not_set) },
                        summaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        expanded = expandedRow == EditRow.LABEL,
                        onToggle = { expandedRow = EditRow.LABEL.toggleFrom(expandedRow) },
                    ) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(stringResource(R.string.schedule_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (mode == EditMode.PLAYLIST) {
                        RowDivider()
                        SettingRow(
                            icon = AppIcons.Sliders,
                            label = stringResource(R.string.options_row),
                            summary = optionsSummary(gapText, maxDurationMinutes, repeatCount, volumePercent),
                            summaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            expanded = expandedRow == EditRow.OPTIONS,
                            onToggle = { expandedRow = EditRow.OPTIONS.toggleFrom(expandedRow) },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OutlinedTextField(
                                    value = gapText,
                                    onValueChange = { gapText = it.filter(Char::isDigit) },
                                    label = { Text(stringResource(R.string.gap_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = optionsFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Column {
                                    Text(
                                        stringResource(R.string.volume_row),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        VolumeChip(
                                            label = stringResource(R.string.volume_default),
                                            selected = volumePercent < 0,
                                            onClick = { volumePercent = -1 },
                                            modifier = Modifier.weight(1.2f),
                                        )
                                        VOLUME_OPTIONS.forEach { percent ->
                                            VolumeChip(
                                                label = stringResource(R.string.chant_volume_percent, percent),
                                                selected = volumePercent == percent,
                                                onClick = { volumePercent = percent },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    val previewUri = sounds.firstOrNull()?.uri
                                    Surface(
                                        onClick = {
                                            val uri = previewUri ?: return@Surface
                                            if (previewingUri == uri) {
                                                SoundPreviewPlayer.stop()
                                            } else {
                                                // Default follows the global
                                                // chant volume, like playback.
                                                SoundPreviewPlayer.toggle(
                                                    context,
                                                    uri,
                                                    volumePercent = volumePercent.takeIf { it > 0 }
                                                        ?: globalChantVolumePercent,
                                                    autoStopMs = VOLUME_PREVIEW_MS,
                                                )
                                            }
                                        },
                                        enabled = previewUri != null,
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                if (previewingUri == previewUri && previewUri != null) {
                                                    AppIcons.Stop
                                                } else {
                                                    Icons.Default.PlayArrow
                                                },
                                                contentDescription = stringResource(R.string.content_desc_volume_preview),
                                                tint = if (previewUri != null) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                },
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.volume_preview),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                Column {
                                    Text(
                                        stringResource(R.string.auto_stop_after),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AutoStopChip(
                                            label = stringResource(R.string.off),
                                            selected = maxDurationMinutes < 0,
                                            onClick = { maxDurationMinutes = -1 },
                                            modifier = Modifier.weight(1f),
                                        )
                                        AUTO_STOP_OPTIONS.forEach { minutes ->
                                            AutoStopChip(
                                                label = "$minutes",
                                                selected = maxDurationMinutes == minutes,
                                                onClick = { maxDurationMinutes = minutes },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                                Column {
                                    Text(
                                        stringResource(R.string.repeat_playlist),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        REPEAT_QUICK_CHOICES.forEach { count ->
                                            AutoStopChip(
                                                label = stringResource(R.string.options_repeat, count),
                                                selected = repeatCount == count,
                                                onClick = { repeatCount = count },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                                Column {
                                    Text(
                                        stringResource(R.string.remind_me_before),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AutoStopChip(
                                            label = stringResource(R.string.off),
                                            selected = reminderMinutes < 0,
                                            onClick = { reminderMinutes = -1 },
                                            modifier = Modifier.weight(1f),
                                        )
                                        listOf(5, 10).forEach { minutes ->
                                            AutoStopChip(
                                                label = stringResource(R.string.duration_minutes, minutes),
                                                selected = reminderMinutes == minutes,
                                                onClick = { reminderMinutes = minutes },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val saveInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    SoundPreviewPlayer.stop()
                    val entity = (initial?.schedule ?: ScheduleEntity(hour = 0, minute = 0)).copy(
                        label = label.trim(),
                        hour = hour,
                        minute = minute,
                        daysOfWeek = daysOfWeek,
                        gapSeconds = gapText.toIntOrNull() ?: 0,
                        triggerType = triggerType,
                        offsetMinutes = if (triggerType.usesClockTime) 0 else offsetMinutes,
                        maxDurationMinutes = maxDurationMinutes.takeIf { it > 0 },
                        repeatCount = repeatCount.coerceAtLeast(1),
                        reminderMinutes = reminderMinutes.takeIf { it > 0 },
                        volumePercent = volumePercent.takeIf { it > 0 },
                    )
                    if (mode == EditMode.PLAYLIST) {
                        onSave(entity, sounds.toList(), emptyList())
                    } else {
                        onSave(entity, emptyList(), steps.toRoutineEntities())
                    }
                },
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                interactionSource = saveInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .accentGlow(RoundedCornerShape(16.dp), enabled = canSave)
                    .pressScale(saveInteractionSource),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.set_schedule_title), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun persistUri(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    } catch (_: SecurityException) {
        // Some providers do not support persistable grants; the URI may
        // still work while the app is running.
    }
}

private fun List<StepDraft>.toRoutineEntities(): List<RoutineStepEntity> =
    mapIndexed { index, step ->
        RoutineStepEntity(
            scheduleId = 0,
            position = index,
            soundUri = step.soundUri,
            soundName = step.soundName,
            durationMinutes = step.durationMinutes,
        )
    }

@Composable
private fun optionsSummary(
    gapText: String,
    maxDurationMinutes: Int,
    repeatCount: Int,
    volumePercent: Int,
): String {
    val parts = mutableListOf<String>()
    gapText.toIntOrNull()?.takeIf { it > 0 }?.let {
        parts.add(stringResource(R.string.options_gap, it))
    }
    if (maxDurationMinutes > 0) {
        parts.add(stringResource(R.string.options_stops_at, maxDurationMinutes))
    }
    if (repeatCount > 1) parts.add(stringResource(R.string.options_repeat, repeatCount))
    if (volumePercent > 0) parts.add(stringResource(R.string.options_volume, volumePercent))
    return parts.joinToString(" · ")
        .ifBlank { stringResource(R.string.options_no_gap) }
}

/** Identifies the expandable setting rows; NONE = all collapsed. */
private enum class EditRow {
    NONE, REPEAT, SOUNDS, LABEL, OPTIONS;

    /** Toggles this row: expands it, or collapses if it is already expanded. */
    fun toggleFrom(current: EditRow): EditRow = if (current == this) NONE else this
}

@Composable
private fun optionsFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
)

@Composable
private fun ModeSelector(
    mode: EditMode,
    onSelect: (EditMode) -> Unit,
) {
    val level = LocalMotion.current
    val options = EditMode.entries
    val selectedIndex = options.indexOf(mode)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / options.size
        // The indicator pill slides between options on a spring.
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = MotionTokens.springSlide(),
            label = "modeIndicator",
        )
        Box(
            Modifier
                .graphicsLayer { translationX = indicatorOffset.toPx() }
                .width(itemWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row {
            options.forEach { option ->
                val isSelected = mode == option
                val content by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MotionTokens.enterTween(level, MotionTokens.DurationShort),
                    label = "modeContent",
                )
                Box(
                    Modifier
                        .width(itemWidth)
                        .clickable { onSelect(option) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (option == EditMode.PLAYLIST) stringResource(R.string.mode_playlist) else stringResource(R.string.mode_routine),
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                    )
                }
            }
        }
    }
}

/** Built-in chime picker shown inside the Sounds row. */
@Composable
private fun AddBuiltinSection(onAdd: (com.chantingscheduler.app.playback.BuiltinSound) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val builtins = remember { BuiltinSounds.all(context) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.built_in_sounds),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                builtins.forEach { builtin ->
                    Surface(
                        onClick = { onAdd(builtin) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                builtin.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoStopChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChipSurface(label, selected, onClick, modifier)
}

@Composable
private fun VolumeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChipSurface(label, selected, onClick, modifier)
}

@Composable
private fun ChipSurface(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun StepDraftRow(
    modifier: Modifier = Modifier,
    index: Int,
    step: StepDraft,
    isPreviewing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPreviewToggle: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onPreviewToggle,
                    enabled = step.soundUri != null,
                    shape = CircleShape,
                    color = if (isPreviewing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPreviewing) AppIcons.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewing) stringResource(R.string.content_desc_stop_preview) else stringResource(R.string.content_desc_preview),
                            tint = if (isPreviewing) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    "${index + 1}. ${step.soundName?.let { BuiltinSounds.labelFor(LocalContext.current, it) } ?: stringResource(R.string.silence_step)}",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.content_desc_move_up),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.content_desc_move_down),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            ) {
                Text(
                    stringResource(R.string.duration_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = { onDurationChange((step.durationMinutes - 1).coerceAtLeast(1)) },
                    enabled = step.durationMinutes > 1,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (step.durationMinutes > 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        },
                    ),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "−",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (step.durationMinutes > 1) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.duration_minutes, step.durationMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Surface(
                    onClick = { onDurationChange((step.durationMinutes + 1).coerceAtMost(180)) },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "+",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact setting row: label left, value summary + chevron right. Tapping the
 * header expands the row in place (only one row expanded at a time, enforced
 * by the caller's [expanded] state) to reveal [content]. Designed to be
 * grouped inside a single card with [RowDivider] hairlines between rows.
 */
@Composable
private fun SettingRow(
    label: String,
    summary: String,
    summaryColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(12.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = summaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                },
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = MotionTokens.springSlide(),
            ) + fadeIn(MotionTokens.enterTween(LocalMotion.current, MotionTokens.DurationShort)),
            exit = shrinkVertically(
                animationSpec = MotionTokens.springSlide(),
            ) + fadeOut(MotionTokens.exitTween(LocalMotion.current)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
            ) {
                content()
            }
        }
    }
}

/** Hairline divider between grouped setting rows. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 18.dp),
    )
}

@Composable
private fun TriggerTypeSelector(
    selected: TriggerType,
    onSelect: (TriggerType) -> Unit,
) {
    val options = listOf(
        TriggerType.FIXED to stringResource(R.string.trigger_fixed),
        TriggerType.SUNRISE to stringResource(R.string.trigger_sunrise),
        TriggerType.SUNSET to stringResource(R.string.trigger_sunset),
        TriggerType.FULL_MOON to stringResource(R.string.trigger_full_moon),
        TriggerType.NEW_MOON to stringResource(R.string.trigger_new_moon),
    )
    val level = LocalMotion.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / options.size
        val selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        // The indicator pill slides between options on a spring.
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = MotionTokens.springSlide(),
            label = "triggerIndicator",
        )
        Box(
            Modifier
                .graphicsLayer { translationX = indicatorOffset.toPx() }
                .width(itemWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row {
            options.forEach { (type, text) ->
                val isSelected = selected == type
                val content by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MotionTokens.enterTween(level, MotionTokens.DurationShort),
                    label = "segmentContent",
                )
                Box(
                    Modifier
                        .width(itemWidth)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(type) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = MaterialTheme.typography.labelLarge, color = content)
                }
            }
        }
    }
}

@Composable
private fun SunTriggerEditor(
    triggerType: TriggerType,
    offsetMinutes: Int,
    location: LatLng?,
    onOffsetChange: (Int) -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val sunName = stringResource(
        if (triggerType == TriggerType.SUNRISE) R.string.trigger_sunrise else R.string.trigger_sunset,
    ).lowercase(java.util.Locale.getDefault())
    val todayTime = remember(triggerType, offsetMinutes, location) {
        if (location == null) {
            null
        } else {
            val base = if (triggerType == TriggerType.SUNRISE) {
                SunTimesCalculator.sunrise(LocalDate.now(), location)
            } else {
                SunTimesCalculator.sunset(LocalDate.now(), location)
            }
            base?.plusMinutes(offsetMinutes.toLong())
        }
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StepperButton(
                    text = "−",
                    enabled = offsetMinutes > -60,
                    onClick = { onOffsetChange((offsetMinutes - 5).coerceAtLeast(-60)) },
                    contentDescription = stringResource(R.string.decrease_offset),
                )
                Text(
                    when {
                        offsetMinutes == 0 -> stringResource(R.string.at_sun_time, sunName)
                        offsetMinutes < 0 -> stringResource(R.string.offset_negative, -offsetMinutes)
                        else -> stringResource(R.string.offset_positive, offsetMinutes)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                StepperButton(
                    text = "+",
                    enabled = offsetMinutes < 60,
                    onClick = { onOffsetChange((offsetMinutes + 5).coerceAtMost(60)) },
                    contentDescription = stringResource(R.string.increase_offset),
                )
            }
            Text(
                when {
                    offsetMinutes == 0 -> stringResource(R.string.at_sun_time, sunName)
                    offsetMinutes < 0 -> stringResource(R.string.offset_before, -offsetMinutes, sunName)
                    else -> stringResource(R.string.offset_after, offsetMinutes, sunName)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            when {
                location == null -> TextButton(
                    onClick = onOpenLocationSettings,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.set_location_for_sun, sunName),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                todayTime != null -> Text(
                    stringResource(R.string.today_at, todayTime.format(TODAY_FORMAT)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.5.dp,
            if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            },
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                },
            )
        }
    }
}

@Composable
private fun SoundItemRow(
    modifier: Modifier = Modifier,
    index: Int,
    sound: SoundItemEntity,
    isPreviewing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPreviewToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onPreviewToggle,
                shape = CircleShape,
                color = if (isPreviewing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPreviewing) AppIcons.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPreviewing) stringResource(R.string.content_desc_stop_preview) else stringResource(R.string.content_desc_preview),
                        tint = if (isPreviewing) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                "${index + 1}. ${BuiltinSounds.labelFor(LocalContext.current, sound.displayName)}",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.content_desc_move_up),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.content_desc_move_down),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.content_desc_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
