package com.cameradinhvi.app

import android.content.Context

/** Cài đặt watermark, lưu trong SharedPreferences. */
class WmSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("watermark", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = p.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    var title: String
        get() = str("title", "")
        set(v) = putStr("title", v)

    /** 0 = giờ thật, 1 = giờ tùy chỉnh (vẫn chạy tiếp), 2 = giờ cố định */
    var timeMode: Int
        get() = p.getInt("timeMode", 0)
        set(v) = p.edit().putInt("timeMode", v).apply()

    var offsetMs: Long
        get() = p.getLong("offsetMs", 0L)
        set(v) = p.edit().putLong("offsetMs", v).apply()

    var customMs: Long
        get() = p.getLong("customMs", 0L)
        set(v) = p.edit().putLong("customMs", v).apply()

    var showAddr: Boolean
        get() = bool("showAddr", true)
        set(v) = putBool("showAddr", v)

    var addrAuto: Boolean
        get() = bool("addrAuto", true)
        set(v) = putBool("addrAuto", v)

    var addrText: String
        get() = str("addrText", "")
        set(v) = putStr("addrText", v)

    var showCoord: Boolean
        get() = bool("showCoord", true)
        set(v) = putBool("showCoord", v)

    var coordAuto: Boolean
        get() = bool("coordAuto", true)
        set(v) = putBool("coordAuto", v)

    var coordText: String
        get() = str("coordText", "")
        set(v) = putStr("coordText", v)

    var note: String
        get() = str("note", "")
        set(v) = putStr("note", v)

    var sizePct: Int
        get() = p.getInt("sizePct", 100)
        set(v) = p.edit().putInt("sizePct", v).apply()

    var atTop: Boolean
        get() = bool("atTop", false)
        set(v) = putBool("atTop", v)

    /** Thời điểm hiển thị trên watermark. */
    fun displayTimeMs(now: Long = System.currentTimeMillis()): Long = when (timeMode) {
        1 -> now + offsetMs
        2 -> if (customMs > 0) customMs else now
        else -> now
    }
}

/** Nội dung watermark tại một thời điểm. */
data class WmData(
    val title: String,
    val time: String,
    val date: String,
    val weekday: String,
    val lines: List<String>,
    val sizePct: Int,
    val atTop: Boolean
)
