package com.chantingscheduler.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chantingscheduler.app.data.AppTheme
import com.chantingscheduler.app.data.ScheduleWithSounds
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.playback.PlaybackState
import com.chantingscheduler.app.ui.OnboardingScreen
import com.chantingscheduler.app.ui.ScheduleEditScreen
import com.chantingscheduler.app.ui.ScheduleListScreen
import com.chantingscheduler.app.ui.ScheduleViewModel
import com.chantingscheduler.app.ui.SettingsSheet
import com.chantingscheduler.app.ui.StatsScreen
import com.chantingscheduler.app.ui.theme.ChantingSchedulerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val SCREEN_LIST = -1L
private const val SCREEN_NEW = 0L
private const val SCREEN_STATS = -2L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            ChantingSchedulerApp()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Composable
    private fun ChantingSchedulerApp() {
        val viewModel: ScheduleViewModel = viewModel()
        val theme by viewModel.theme.collectAsStateWithLifecycle()

        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (theme) {
            AppTheme.AUTO -> systemDark
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
        }

        ChantingSchedulerTheme(darkTheme = darkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ChantingSchedulerContent(viewModel)
            }
        }
    }

    @Composable
    private fun ChantingSchedulerContent(viewModel: ScheduleViewModel) {
        val activity = this@MainActivity
        val schedules by viewModel.schedules.collectAsStateWithLifecycle()
        val nowPlaying by PlaybackState.state.collectAsStateWithLifecycle()
        val location by viewModel.location.collectAsStateWithLifecycle()
        val theme by viewModel.theme.collectAsStateWithLifecycle()
        val fadeInSeconds by viewModel.fadeInSeconds.collectAsStateWithLifecycle()
        val snoozeMinutes by viewModel.snoozeMinutes.collectAsStateWithLifecycle()
        val alarmVolumeEnabled by viewModel.alarmVolumeEnabled.collectAsStateWithLifecycle()
        val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()

        // -1 = schedule list, 0 = new schedule, >0 = edit schedule with that id
        var screen by rememberSaveable { mutableLongStateOf(SCREEN_LIST) }
        var editing by remember { mutableStateOf<ScheduleWithSounds?>(null) }
        var showExactAlarmHint by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var replayOnboarding by remember { mutableStateOf(false) }
        val onboardingSeen by viewModel.onboardingSeen.collectAsStateWithLifecycle()

        val scope = rememberCoroutineScope()
        var backupMessage by remember { mutableStateOf<String?>(null) }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching {
                        val json = viewModel.exportBackupJson()
                        activity.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray())
                        }
                        backupMessage = getString(R.string.backup_exported)
                    }.onFailure { backupMessage = getString(R.string.backup_export_failed) }
                }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val ok = runCatching {
                        val json = activity.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().decodeToString() } ?: ""
                        viewModel.importBackup(json)
                    }.getOrDefault(false)
                    backupMessage = if (ok) getString(R.string.backup_imported) else getString(R.string.backup_import_failed)
                }
            }
        }
        backupMessage?.let { message ->
            LaunchedEffect(message) {
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
                backupMessage = null
            }
        }

        LaunchedEffect(screen) {
            editing = if (screen > 0) viewModel.load(screen) else null
        }

        ExactAlarmPermissionCheck(onMissing = { showExactAlarmHint = true })

        if (showExactAlarmHint) {
            AlertDialog(
                onDismissRequest = { showExactAlarmHint = false },
                title = { Text(stringResource(R.string.exact_alarm_dialog_title)) },
                text = {
                    Text(stringResource(R.string.exact_alarm_dialog_body))
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExactAlarmHint = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:$packageName")
                                },
                            )
                        }
                    }) { Text(stringResource(R.string.open_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showExactAlarmHint = false }) { Text(stringResource(R.string.later)) }
                },
            )
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == SCREEN_LIST) {
                    // Navigating back to the list: slide in from the left.
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                } else {
                    // Navigating forward to the editor: slide in from the right.
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                }
            },
            label = "screenTransition",
        ) { targetScreen ->
            when {
                targetScreen == SCREEN_STATS -> StatsScreen(onBack = { screen = SCREEN_LIST })

                targetScreen == SCREEN_LIST -> ScheduleListScreen(
                    schedules = schedules,
                    nowPlaying = nowPlaying,
                    location = location,
                    onAdd = { screen = SCREEN_NEW },
                    onEdit = { screen = it },
                    onOpenSettings = { showSettings = true },
                    onOpenStats = { screen = SCREEN_STATS },
                    onToggle = viewModel::setEnabled,
                    deletedItem = lastDeleted,
                    onUndoDelete = viewModel::undoDelete,
                    onUndoExpired = viewModel::clearUndo,
                    onPlayToggle = { item ->
                        if (nowPlaying.isPlayingSchedule(item.schedule.id)) {
                            PlaybackService.stop(activity)
                        } else if (item.isRoutine) {
                            PlaybackService.start(
                                context = activity,
                                uris = emptyList(),
                                gapSeconds = 0,
                                label = item.schedule.label,
                                scheduleId = item.schedule.id,
                                stepUris = item.sortedSteps.map { it.soundUri.orEmpty() },
                                stepMinutes = item.sortedSteps.map { it.durationMinutes },
                            )
                        } else {
                            PlaybackService.start(
                                context = activity,
                                uris = item.sortedSounds.map { it.uri },
                                gapSeconds = item.schedule.gapSeconds,
                                label = item.schedule.label,
                                scheduleId = item.schedule.id,
                                repeatCount = item.schedule.repeatCount,
                                maxDurationMinutes = item.schedule.maxDurationMinutes ?: -1,
                            )
                        }
                    },
                    onStop = { PlaybackService.stop(activity) },
                    onOpenRinging = {
                        activity.startActivity(
                            Intent(activity, RingingActivity::class.java)
                                .putExtra(
                                    RingingActivity.EXTRA_SCHEDULE_ID,
                                    nowPlaying.scheduleId ?: -1L,
                                ),
                        )
                    },
                )

                targetScreen == SCREEN_NEW || editing != null -> ScheduleEditScreen(
                    initial = editing,
                    location = location,
                    resolveDisplayName = ::resolveDisplayName,
                    onOpenLocationSettings = { showSettings = true },
                    onSave = { schedule, sounds, steps ->
                        viewModel.save(schedule, sounds, steps)
                        screen = SCREEN_LIST
                    },
                    onDelete = {
                        editing?.let(viewModel::delete)
                        screen = SCREEN_LIST
                    },
                    onBack = { screen = SCREEN_LIST },
                )
            }
        }

        if (showSettings) {
            SettingsSheet(
                theme = theme,
                onThemeChange = viewModel::setTheme,
                location = location,
                onSave = { lat, lng, address -> viewModel.saveLocation(lat, lng, address) },
                onClear = { viewModel.clearLocation() },
                fadeInSeconds = fadeInSeconds,
                onFadeInChange = viewModel::setFadeInSeconds,
                snoozeMinutes = snoozeMinutes,
                onSnoozeMinutesChange = viewModel::setSnoozeMinutes,
                alarmVolumeEnabled = alarmVolumeEnabled,
                onAlarmVolumeChange = viewModel::setAlarmVolumeEnabled,
                onReplayIntroduction = {
                    showSettings = false
                    replayOnboarding = true
                },
                onExport = { exportLauncher.launch("chanting-buddy-backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onDismiss = { showSettings = false },
            )
        }

        if (!onboardingSeen || replayOnboarding) {
            OnboardingScreen(onFinish = {
                viewModel.markOnboardingSeen()
                replayOnboarding = false
            })
        }
    }

    @Composable
    private fun ExactAlarmPermissionCheck(onMissing: () -> Unit) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(AlarmManager::class.java)
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    onMissing()
                }
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String =
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        } ?: uri.lastPathSegment ?: uri.toString()
}
