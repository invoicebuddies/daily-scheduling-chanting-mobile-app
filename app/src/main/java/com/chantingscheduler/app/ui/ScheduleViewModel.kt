package com.chantingscheduler.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chantingscheduler.app.data.AppTheme
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.LocationStore
import com.chantingscheduler.app.data.OnboardingStore
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.data.ScheduleWithSounds
import com.chantingscheduler.app.data.SoundItemEntity
import com.chantingscheduler.app.data.RoutineStepEntity
import com.chantingscheduler.app.data.BackupCodec
import com.chantingscheduler.app.data.ThemeStore
import com.chantingscheduler.app.playback.WidgetBridge
import com.chantingscheduler.app.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val alarmScheduler: AlarmScheduler,
    private val locationStore: LocationStore,
    private val themeStore: ThemeStore,
    private val onboardingStore: OnboardingStore,
    private val playbackSettingsStore: PlaybackSettingsStore,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    val schedules: StateFlow<List<ScheduleWithSounds>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val location: StateFlow<LatLng?> = locationStore.location

    val theme: StateFlow<AppTheme> = themeStore.theme

    val onboardingSeen: StateFlow<Boolean> = onboardingStore.seen

    val fadeInSeconds: StateFlow<Int> = playbackSettingsStore.fadeInSeconds

    val snoozeMinutes: StateFlow<Int> = playbackSettingsStore.snoozeMinutes

    val alarmVolumeEnabled: StateFlow<Boolean> = playbackSettingsStore.alarmVolumeEnabled

    val chantVolumePercent: StateFlow<Int> = playbackSettingsStore.chantVolumePercent

    /** Most recently deleted schedule, offered for undo for 5 seconds. */
    private val _lastDeleted = MutableStateFlow<ScheduleWithSounds?>(null)
    val lastDeleted: StateFlow<ScheduleWithSounds?> = _lastDeleted
    private var undoExpireJob: Job? = null

    fun markOnboardingSeen() {
        onboardingStore.markSeen()
    }

    fun setTheme(theme: AppTheme) {
        themeStore.set(theme)
    }

    fun setFadeInSeconds(seconds: Int) {
        playbackSettingsStore.setFadeInSeconds(seconds)
    }

    fun setSnoozeMinutes(minutes: Int) {
        playbackSettingsStore.setSnoozeMinutes(minutes)
    }

    fun setAlarmVolumeEnabled(enabled: Boolean) {
        playbackSettingsStore.setAlarmVolumeEnabled(enabled)
    }

    fun setChantVolumePercent(percent: Int) {
        playbackSettingsStore.setChantVolumePercent(percent)
    }

    fun saveLocation(latitude: Double, longitude: Double, address: String? = null) {
        locationStore.set(latitude, longitude, address)
        rearmAll()
    }

    fun clearLocation() {
        locationStore.clear()
        rearmAll()
    }

    /** Recompute all enabled alarms, e.g. after the location changes. */
    private fun rearmAll() {
        viewModelScope.launch {
            repository.getEnabled().forEach { alarmScheduler.schedule(it.schedule) }
        }
    }

    suspend fun load(id: Long): ScheduleWithSounds? = repository.getById(id)

    /** JSON snapshot of all schedules (with sounds/steps) and history. */
    suspend fun exportBackupJson(): String = BackupCodec.toJson(repository.snapshot())

    /**
     * Parses and imports a backup, then re-arms every enabled schedule.
     * `content://` URIs the app still holds a persisted grant for are
     * re-attached cleanly; the rest keep their re-pick marker. Returns the
     * number of schedules imported and skipped as duplicates, or null when
     * the file could not be parsed.
     */
    suspend fun importBackup(json: String): ScheduleRepository.ImportResult? {
        val granted = appContext.contentResolver.persistedUriPermissions
            .map { it.uri.toString() }
            .toSet()
        val backup = runCatching { BackupCodec.parse(json) { it in granted } }.getOrNull()
            ?: return null
        val result = repository.importBackup(backup)
        repository.getEnabled().forEach { alarmScheduler.schedule(it.schedule) }
        return result
    }

    /**
     * `content://` sound URIs whose persisted grant is gone (file deleted or
     * restored from a backup onto a device that never held the grant). The
     * list screen shows a "Re-pick" chip on affected schedules.
     */
    val missingSoundUris: StateFlow<Set<String>> = schedules
        .map { list ->
            val granted = appContext.contentResolver.persistedUriPermissions
                .map { it.uri.toString() }
                .toSet()
            list.flatMap { item ->
                (item.sortedSounds.map { it.uri } +
                    item.sortedSteps.mapNotNull { it.soundUri })
                    .filter { it.startsWith("content://") && it !in granted }
            }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun save(schedule: ScheduleEntity, sounds: List<SoundItemEntity>, steps: List<RoutineStepEntity> = emptyList()) {
        viewModelScope.launch {
            if (schedule.id != 0L) alarmScheduler.cancel(schedule.id)
            val id = repository.save(schedule, sounds, steps)
            repository.getById(id)?.let { saved ->
                if (saved.schedule.enabled) alarmScheduler.schedule(saved.schedule)
            }
            WidgetBridge.requestUpdate(appContext)
        }
    }

    fun setEnabled(item: ScheduleWithSounds, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(item.schedule, enabled)
            if (enabled) {
                alarmScheduler.schedule(item.schedule.copy(enabled = true))
            } else {
                alarmScheduler.cancel(item.schedule.id)
            }
        }
    }

    fun delete(item: ScheduleWithSounds) {
        viewModelScope.launch {
            alarmScheduler.cancel(item.schedule.id)
            repository.delete(item)
            WidgetBridge.requestUpdate(appContext)
            _lastDeleted.value = item
            undoExpireJob?.cancel()
            undoExpireJob = viewModelScope.launch {
                kotlinx.coroutines.delay(UNDO_WINDOW_MS)
                _lastDeleted.value = null
            }
        }
    }

    /** Restores the most recently deleted schedule (fresh ids) and re-arms it. */
    fun undoDelete() {
        val item = _lastDeleted.value ?: return
        undoExpireJob?.cancel()
        _lastDeleted.value = null
        viewModelScope.launch {
            val id = repository.save(
                item.schedule.copy(id = 0),
                item.sounds,
                item.steps,
            )
            repository.getById(id)?.let { restored ->
                if (restored.schedule.enabled) alarmScheduler.schedule(restored.schedule)
            }
            WidgetBridge.requestUpdate(appContext)
        }
    }

    /** Drops the pending undo token without restoring (snackbar dismissed/expired). */
    fun clearUndo() {
        undoExpireJob?.cancel()
        _lastDeleted.value = null
    }

    companion object {
        private const val UNDO_WINDOW_MS = 5_000L
    }
}
