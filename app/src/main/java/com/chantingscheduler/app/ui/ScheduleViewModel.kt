package com.chantingscheduler.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Build
import com.chantingscheduler.app.BuildConfig
import com.chantingscheduler.app.R
import com.chantingscheduler.app.data.AlarmLedgerStore
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
import com.chantingscheduler.app.data.UpdatePromptLogic
import com.chantingscheduler.app.data.UpdateStore
import com.chantingscheduler.app.data.VersionComparator
import com.chantingscheduler.app.playback.WidgetBridge
import com.chantingscheduler.app.scheduling.AlarmScheduler
import com.chantingscheduler.app.update.GitHubReleaseChecker
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
    private val updateStore: UpdateStore,
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

    // region Missed chants

    /**
     * One-shot card at the top of the list: scheduled chants that never
     * played (phone off, battery saver, revoked alarm permission). Filled in
     * init from [AlarmLedgerStore]'s pending misses, which rearmAll recorded
     * before re-arming; dismissed by the user.
     */
    data class MissedChantCard(
        /** Local time of the expectation, e.g. "6:00" (single miss only). */
        val time: String,
        /** Weekday of the miss, e.g. "Tue" (single miss only). */
        val weekday: String,
        /** Schedule label; blank falls back to the default name in the UI. */
        val label: String,
        /** Number of missed chants; > 1 renders the plural card. */
        val count: Int,
    )

    private val _missedChantCard = MutableStateFlow<MissedChantCard?>(null)
    val missedChantCard: StateFlow<MissedChantCard?> = _missedChantCard

    init {
        // Collect, don't one-shot read: rearmAll in the Application runs
        // detection asynchronously at process start, so the pending set may
        // fill a moment after this ViewModel is created. Collecting the flow
        // shows the card on the first app open after a miss.
        viewModelScope.launch {
            AlarmLedgerStore.get(appContext).missed.collect { missed ->
                if (missed.isEmpty()) {
                    if (_missedChantCard.value != null) _missedChantCard.value = null
                    return@collect
                }
                val labels = repository.getEnabled()
                    .associate { it.schedule.id to it.schedule.label }
                // Misses whose schedule vanished (deleted, or disabled since)
                // get no card; if nothing remains the pending list is stale.
                val visible = missed.filter { it.scheduleId in labels }
                if (visible.isEmpty()) {
                    AlarmLedgerStore.get(appContext).clearMissed()
                    _missedChantCard.value = null
                    return@collect
                }
                _missedChantCard.value = if (visible.size == 1) {
                    val entry = visible.first()
                    val zone = java.time.ZoneId.systemDefault()
                    val zdt = java.time.Instant.ofEpochMilli(entry.expectedAtMs).atZone(zone)
                    MissedChantCard(
                        time = android.text.format.DateFormat.getTimeFormat(appContext)
                            .format(java.util.Date(entry.expectedAtMs)),
                        weekday = zdt.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT,
                            java.util.Locale.getDefault(),
                        ),
                        label = labels[entry.scheduleId].orEmpty(),
                        count = 1,
                    )
                } else {
                    MissedChantCard(time = "", weekday = "", label = "", count = visible.size)
                }
            }
        }
    }

    /** "Dismiss": the misses were acknowledged; never show the card again. */
    fun dismissMissedChants() {
        AlarmLedgerStore.get(appContext).clearMissed()
        _missedChantCard.value = null
    }

    // endregion

    // region Update check (GitHub Releases)

    /** Card shown at the top of the schedule list when a newer version exists. */
    data class UpdateCard(
        /** New version, without the leading "v" (for display). */
        val version: String,
        /** Tag with leading "v" (for skip bookkeeping). */
        val tag: String,
        /** First ~3 lines of the release notes, markdown stripped. */
        val notes: String,
        /** Best download target: matching ABI asset, universal, or release page. */
        val downloadUrl: String,
    )

    private val _updateCard = MutableStateFlow<UpdateCard?>(null)
    val updateCard: StateFlow<UpdateCard?> = _updateCard

    /** One-shot status message (e.g. "You're up to date") for the caller to toast. */
    private val _updateStatusMessage = MutableStateFlow<Int?>(null)
    val updateStatusMessage: StateFlow<Int?> = _updateStatusMessage

    private val _updateAutoCheck = MutableStateFlow(updateStore.autoCheckEnabled)
    val updateAutoCheck: StateFlow<Boolean> = _updateAutoCheck

    fun setUpdateAutoCheck(enabled: Boolean) {
        updateStore.autoCheckEnabled = enabled
        _updateAutoCheck.value = enabled
    }

    fun consumeUpdateStatusMessage() {
        _updateStatusMessage.value = null
    }

    /**
     * Anonymous check against the latest GitHub release. Automatic checks
     * (force=false) run at most once per 24 h, respect the auto-check toggle,
     * and honor "Skip this version"; a forced check ignores the throttle and
     * the skipped version. Every failure is silent; only a forced check on a
     * current version reports "up to date". Gated on
     * [com.chantingscheduler.app.BuildConfig.GITHUB_UPDATE_CHECK] so a future
     * Play build can switch the whole feature off.
     */
    fun checkForUpdates(force: Boolean = false) {
        if (!BuildConfig.GITHUB_UPDATE_CHECK) return
        val now = System.currentTimeMillis()
        if (!force) {
            if (!_updateAutoCheck.value) return
            if (now - updateStore.lastCheckMs < UpdateStore.CHECK_INTERVAL_MS) return
        }
        viewModelScope.launch {
            updateStore.lastCheckMs = System.currentTimeMillis()
            val release = GitHubReleaseChecker.fetchLatest(
                "ChantingBuddy/${BuildConfig.VERSION_NAME}",
            ) ?: return@launch
            if (!VersionComparator.isNewer(release.tag, BuildConfig.VERSION_NAME)) {
                if (force) _updateStatusMessage.value = R.string.update_up_to_date
                return@launch
            }
            if (!force && updateStore.skippedVersion == release.tag) return@launch
            _updateCard.value = UpdateCard(
                version = release.tag.removePrefix("v"),
                tag = release.tag,
                notes = UpdatePromptLogic.stripMarkdown(release.body),
                downloadUrl = UpdatePromptLogic.pickAssetUrl(
                    release.assetUrls,
                    Build.SUPPORTED_ABIS.toList(),
                    release.htmlUrl,
                ),
            )
        }
    }

    /** "Later": hide the card until the next (24 h) check. */
    fun dismissUpdateLater() {
        _updateCard.value = null
    }

    /** "Skip this version": hide until an even newer tag appears. */
    fun skipThisUpdateVersion() {
        _updateCard.value?.let { updateStore.skippedVersion = it.tag }
        _updateCard.value = null
    }

    // endregion

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
