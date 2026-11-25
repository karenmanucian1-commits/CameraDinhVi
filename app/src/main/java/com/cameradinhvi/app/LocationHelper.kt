package com.cameradinhvi.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

/** Lấy vị trí GPS/mạng và địa chỉ (không cần Google Play Services). */
class LocationHelper(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val exec = Executors.newSingleThreadExecutor()
    private var lastGeo: Location? = null
    private var running = false

    @Volatile var location: Location? = null
        private set
    @Volatile var address: String? = null
        private set

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) { update(l) }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (!lm.allProviders.contains(p)) continue
                lm.getLastKnownLocation(p)?.let { update(it) }
                lm.requestLocationUpdates(p, 3000L, 0f, listener, Looper.getMainLooper())
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        running = false
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
    }

    private fun update(l: Location) {
        val cur = location
        val better = cur == null ||
            l.time - cur.time > 10_000 ||
            (l.hasAccuracy() && (!cur.hasAccuracy() || l.accuracy <= cur.accuracy))
        if (!better) return
        location = l
        val lg = lastGeo
        if (lg == null || lg.distanceTo(l) > 30f || address == null) {
            lastGeo = l
            geocode(l)
        }
    }

    private fun geocode(l: Location) {
        if (!Geocoder.isPresent()) return
        exec.execute {
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(appCtx, Locale("vi", "VN")).getFromLocation(l.latitude, l.longitude, 1)
                val a = list?.firstOrNull()
                val line = a?.getAddressLine(0)
                if (!line.isNullOrBlank()) address = line
            } catch (_: Exception) {
                // không có mạng thì giữ địa chỉ cũ
            }
        }
    }
}
