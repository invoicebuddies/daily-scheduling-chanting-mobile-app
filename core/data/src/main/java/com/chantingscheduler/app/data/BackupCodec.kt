package com.chantingscheduler.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON codec for exporting / importing schedules (with sounds and
 * routine steps) and session history. Pure data transformation — callers own
 * file I/O via SAF.
 *
 * Built-in `android.resource://` URIs survive an import untouched. Phone-file
 * (`content://`) grants never survive a backup, so those sounds are marked
 * with [REPICK_MARKER] on import; they still attempt to play (the service
 * falls back to the bundled chime when the grant is dead) and the marker
 * tells the user to re-pick the file.
 */
object BackupCodec {

    const val VERSION = 3
    const val REPICK_MARKER = "⚠ "

    data class Backup(
        val schedules: List<ScheduleWithSounds>,
        val sessions: List<SessionLogEntity>,
    )

    fun toJson(backup: Backup): String {
        val root = JSONObject()
            .put("version", VERSION)
            .put("app", "chanting-buddy")
        root.put("schedules", JSONArray().apply {
            backup.schedules.forEach { item ->
                put(
                    JSONObject()
                        .put("schedule", scheduleToJson(item.schedule))
                        .put("sounds", JSONArray().apply {
                            item.sortedSounds.forEach { put(soundToJson(it)) }
                        })
                        .put("steps", JSONArray().apply {
                            item.sortedSteps.forEach { put(stepToJson(it)) }
                        }),
                )
            }
        })
        root.put("sessions", JSONArray().apply {
            backup.sessions.forEach { put(sessionToJson(it)) }
        })
        return root.toString(2)
    }

    fun parse(
        json: String,
        /**
         * Same-device imports: `content://` URIs the app still holds a
         * persisted grant for play fine and keep their names; only URIs
         * without a grant get the re-pick marker. Defaults to "nothing is
         * granted" (restore-on-a-new-device behavior).
         */
        stillGranted: (String) -> Boolean = { false },
    ): Backup {
        val root = JSONObject(json)
        val schedules = mutableListOf<ScheduleWithSounds>()
        val scheduleArray = root.optJSONArray("schedules") ?: JSONArray()
        for (i in 0 until scheduleArray.length()) {
            val entry = scheduleArray.getJSONObject(i)
            val schedule = scheduleFromJson(entry.getJSONObject("schedule"))
            val sounds = mutableListOf<SoundItemEntity>()
            val soundArray = entry.optJSONArray("sounds") ?: JSONArray()
            for (j in 0 until soundArray.length()) {
                sounds.add(soundFromJson(soundArray.getJSONObject(j), schedule.id, stillGranted))
            }
            val steps = mutableListOf<RoutineStepEntity>()
            val stepArray = entry.optJSONArray("steps") ?: JSONArray()
            for (j in 0 until stepArray.length()) {
                steps.add(stepFromJson(stepArray.getJSONObject(j), schedule.id, stillGranted))
            }
            schedules.add(ScheduleWithSounds(schedule, sounds, steps))
        }
        val sessions = mutableListOf<SessionLogEntity>()
        val sessionArray = root.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sessionArray.length()) {
            sessions.add(sessionFromJson(sessionArray.getJSONObject(i)))
        }
        return Backup(schedules, sessions)
    }

    private fun needsRepick(uri: String): Boolean =
        uri.isNotBlank() && !uri.startsWith("android.resource://")

    /**
     * True when [candidate] is identical to one of [existing] — same label,
     * trigger, time, days, and sounds/steps — i.e. re-importing the same
     * backup would create a duplicate.
     */
    fun isDuplicate(candidate: ScheduleWithSounds, existing: List<ScheduleWithSounds>): Boolean =
        existing.any { other ->
            other.schedule.label == candidate.schedule.label &&
                other.schedule.triggerType == candidate.schedule.triggerType &&
                other.schedule.hour == candidate.schedule.hour &&
                other.schedule.minute == candidate.schedule.minute &&
                other.schedule.offsetMinutes == candidate.schedule.offsetMinutes &&
                other.schedule.daysOfWeek == candidate.schedule.daysOfWeek &&
                other.sortedSounds.map { it.uri } == candidate.sortedSounds.map { it.uri } &&
                other.sortedSteps.map { Triple(it.soundUri, it.soundName, it.durationMinutes) } ==
                candidate.sortedSteps.map { Triple(it.soundUri, it.soundName, it.durationMinutes) }
        }

    private fun mark(name: String?, uri: String?, stillGranted: (String) -> Boolean): String? {
        if (name == null) return null
        val stripped = name.removePrefix(REPICK_MARKER)
        return if (needsRepick(uri.orEmpty()) && !stillGranted(uri.orEmpty())) {
            REPICK_MARKER + stripped
        } else {
            stripped
        }
    }

    private fun scheduleToJson(s: ScheduleEntity): JSONObject = JSONObject()
        .put("label", s.label)
        .put("hour", s.hour)
        .put("minute", s.minute)
        .put("daysOfWeek", s.daysOfWeek)
        .put("gapSeconds", s.gapSeconds)
        .put("enabled", s.enabled)
        .put("triggerType", s.triggerType.name)
        .put("offsetMinutes", s.offsetMinutes)
        .put("maxDurationMinutes", s.maxDurationMinutes ?: JSONObject.NULL)
        .put("repeatCount", s.repeatCount)
        .put("reminderMinutes", s.reminderMinutes ?: JSONObject.NULL)
        .put("volumePercent", s.volumePercent ?: JSONObject.NULL)

    private fun scheduleFromJson(o: JSONObject): ScheduleEntity = ScheduleEntity(
        id = 0, // imports always become fresh rows
        label = o.optString("label"),
        hour = o.optInt("hour"),
        minute = o.optInt("minute"),
        daysOfWeek = o.optInt("daysOfWeek", ALL_DAYS),
        gapSeconds = o.optInt("gapSeconds"),
        enabled = o.optBoolean("enabled", true),
        triggerType = runCatching { TriggerType.valueOf(o.optString("triggerType")) }
            .getOrDefault(TriggerType.FIXED),
        offsetMinutes = o.optInt("offsetMinutes"),
        maxDurationMinutes = if (o.isNull("maxDurationMinutes")) null else o.optInt("maxDurationMinutes"),
        repeatCount = o.optInt("repeatCount", 1).coerceAtLeast(1),
        reminderMinutes = if (o.isNull("reminderMinutes")) null else o.optInt("reminderMinutes"),
        // v1 backups predate the per-schedule volume: missing field = null
        // (follow the global chant volume), never a crash.
        volumePercent = if (o.isNull("volumePercent")) null else o.optInt("volumePercent"),
    )

    private fun soundToJson(s: SoundItemEntity): JSONObject = JSONObject()
        .put("uri", s.uri)
        .put("displayName", s.displayName)
        .put("position", s.position)

    private fun soundFromJson(
        o: JSONObject,
        scheduleId: Long,
        stillGranted: (String) -> Boolean,
    ): SoundItemEntity = SoundItemEntity(
        id = 0,
        scheduleId = scheduleId,
        uri = o.optString("uri"),
        displayName = mark(o.optString("displayName"), o.optString("uri"), stillGranted).orEmpty(),
        position = o.optInt("position"),
    )

    private fun stepToJson(s: RoutineStepEntity): JSONObject = JSONObject()
        .put("soundUri", s.soundUri ?: JSONObject.NULL)
        .put("soundName", s.soundName ?: JSONObject.NULL)
        .put("durationMinutes", s.durationMinutes)
        .put("position", s.position)

    private fun stepFromJson(
        o: JSONObject,
        scheduleId: Long,
        stillGranted: (String) -> Boolean,
    ): RoutineStepEntity = RoutineStepEntity(
        id = 0,
        scheduleId = scheduleId,
        soundUri = if (o.isNull("soundUri")) null else o.optString("soundUri"),
        soundName = if (o.isNull("soundName")) {
            null
        } else {
            mark(o.optString("soundName"), o.optString("soundUri"), stillGranted)
        },
        durationMinutes = o.optInt("durationMinutes"),
        position = o.optInt("position"),
    )

    private fun sessionToJson(s: SessionLogEntity): JSONObject = JSONObject()
        .put("scheduleId", s.scheduleId ?: JSONObject.NULL)
        .put("startedAtEpochMs", s.startedAtEpochMs)
        .put("durationSeconds", s.durationSeconds)
        .put("endReason", s.endReason.name)
        .put("source", s.source.name)
        .put("audioSeconds", s.audioSeconds ?: JSONObject.NULL)
        .put("note", s.note ?: JSONObject.NULL)

    private fun sessionFromJson(o: JSONObject): SessionLogEntity = SessionLogEntity(
        id = 0,
        scheduleId = if (o.isNull("scheduleId")) null else o.optLong("scheduleId"),
        startedAtEpochMs = o.optLong("startedAtEpochMs"),
        durationSeconds = o.optInt("durationSeconds"),
        endReason = runCatching { SessionEndReason.valueOf(o.optString("endReason")) }
            .getOrDefault(SessionEndReason.COMPLETED),
        source = runCatching { SessionSource.valueOf(o.optString("source")) }
            .getOrDefault(SessionSource.ALARM),
        audioSeconds = if (o.isNull("audioSeconds")) null else o.optInt("audioSeconds"),
        // v1/v2 backups predate the journal note: missing field = null.
        note = if (o.isNull("note")) null else o.optString("note"),
    )
}
