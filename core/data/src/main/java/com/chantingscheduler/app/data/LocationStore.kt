package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Geographic coordinates used for sunrise/sunset calculations, plus the
 * optional human-readable address the user searched for (display only).
 */
data class LatLng(val latitude: Double, val longitude: Double, val address: String? = null)

/**
 * App-wide location storage. SharedPreferences (not DataStore) so that
 * [com.chantingscheduler.app.scheduling.AlarmScheduler] can read the value
 * synchronously on alarm/boot paths.
 */
class LocationStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _location = MutableStateFlow(readLocation())

    /** Observable location for the UI. */
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    /** Current location, readable synchronously (e.g. from BroadcastReceivers). */
    val current: LatLng? get() = _location.value

    fun set(latitude: Double, longitude: Double, address: String? = null) {
        prefs.edit()
            .putFloat(KEY_LAT, latitude.toFloat())
            .putFloat(KEY_LNG, longitude.toFloat())
            .apply {
                if (address != null) putString(KEY_ADDRESS, address) else remove(KEY_ADDRESS)
            }
            .apply()
        _location.value = LatLng(latitude, longitude, address)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _location.value = null
    }

    private fun readLocation(): LatLng? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LNG)) return null
        return LatLng(
            latitude = prefs.getFloat(KEY_LAT, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_LNG, 0f).toDouble(),
            address = prefs.getString(KEY_ADDRESS, null),
        )
    }

    companion object {
        private const val PREFS_NAME = "location"
        private const val KEY_LAT = "latitude"
        private const val KEY_LNG = "longitude"
        private const val KEY_ADDRESS = "address"

        @Volatile
        private var instance: LocationStore? = null

        fun get(context: Context): LocationStore =
            instance ?: synchronized(this) {
                instance ?: LocationStore(context).also { instance = it }
            }
    }
}
