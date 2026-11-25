package com.cameradinhvi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Hộp thoại chỉnh nội dung watermark. */
object SettingsDialog {

    fun show(act: Activity, s: WmSettings, onSaved: () -> Unit) {
        val d = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val fmt = SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.US)

        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun header(t: String) = TextView(act).apply {
            text = t
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(4))
        }.also { box.addView(it) }
        fun edit(hint: String, value: String, multi: Boolean = false) = EditText(act).apply {
            this.hint = hint
            setText(value)
            inputType = if (multi)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }.also { box.addView(it) }
        fun check(t: String, v: Boolean) = CheckBox(act).apply { text = t; isChecked = v }.also { box.addView(it) }

        // --- Tiêu đề
        header("Tiêu đề (ô vàng, để trống = ẩn)")
        val etTitle = edit("Vd: Dự án The Camellia", s.title)

        // --- Thời gian
        header("Thời gian")
        val rg = RadioGroup(act)
        val rbReal = RadioButton(act).apply { id = View.generateViewId(); text = "Giờ thật" }
        val rbRun = RadioButton(act).apply { id = View.generateViewId(); text = "Giờ tùy chỉnh (đồng hồ vẫn chạy tiếp)" }
        val rbFix = RadioButton(act).apply { id = View.generateViewId(); text = "Giờ cố định (đứng yên)" }
        rg.addView(rbReal); rg.addView(rbRun); rg.addView(rbFix)
        box.addView(rg)
        when (s.timeMode) { 1 -> rbRun.isChecked = true; 2 -> rbFix.isChecked = true; else -> rbReal.isChecked = true }

        var pickedMs = when (s.timeMode) {
            1 -> System.currentTimeMillis() + s.offsetMs
            2 -> if (s.customMs > 0) s.customMs else System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
        var pickedOffset = s.offsetMs
        val btnPick = Button(act).apply { text = "Chọn ngày giờ: " + fmt.format(Date(pickedMs)) }
        box.addView(btnPick)
        fun refreshPick() {
            btnPick.isEnabled = !rbReal.isChecked
            btnPick.alpha = if (btnPick.isEnabled) 1f else 0.4f
        }
        rg.setOnCheckedChangeListener { _, _ -> refreshPick() }
        refreshPick()
        btnPick.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = pickedMs }
            DatePickerDialog(act, { _, y, mo, dd ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, mo); cal.set(Calendar.DAY_OF_MONTH, dd)
                TimePickerDialog(act, { _, h, mi ->
                    cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, mi); cal.set(Calendar.SECOND, 0)
                    pickedMs = cal.timeInMillis
                    pickedOffset = pickedMs - System.currentTimeMillis()
                    btnPick.text = "Chọn ngày giờ: " + fmt.format(Date(pickedMs))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // --- Địa chỉ
        header("Địa chỉ")
        val cbAddr = check("Hiện địa chỉ", s.showAddr)
        val cbAddrAuto = check("Tự động theo GPS", s.addrAuto)
        val etAddr = edit("Nhập địa chỉ tùy ý", s.addrText, multi = true)

        // --- Tọa độ
        header("Tọa độ")
        val cbCoord = check("Hiện tọa độ", s.showCoord)
        val cbCoordAuto = check("Tự động theo GPS", s.coordAuto)
        val etCoord = edit("Vd: 16.054407, 108.202164", s.coordText)

        fun refreshEdits() {
            etAddr.isEnabled = cbAddr.isChecked && !cbAddrAuto.isChecked
            cbAddrAuto.isEnabled = cbAddr.isChecked
            etCoord.isEnabled = cbCoord.isChecked && !cbCoordAuto.isChecked
            cbCoordAuto.isEnabled = cbCoord.isChecked
        }
        listOf(cbAddr, cbAddrAuto, cbCoord, cbCoordAuto).forEach { cb -> cb.setOnCheckedChangeListener { _, _ -> refreshEdits() } }
        refreshEdits()

        // --- Ghi chú
        header("Ghi chú (dòng cuối, để trống = ẩn)")
        val etNote = edit("Vd: Khảo sát căn hộ tầng 12", s.note, multi = true)

        // --- Cỡ chữ & vị trí
        val tvSize = header("Cỡ chữ: ${s.sizePct}%")
        val sb = SeekBar(act).apply {
            max = 100           // 60% .. 160%
            progress = (s.sizePct - 60).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { tvSize.text = "Cỡ chữ: ${p + 60}%" }
                override fun onStartTrackingTouch(b: SeekBar?) {}
                override fun onStopTrackingTouch(b: SeekBar?) {}
            })
        }
        box.addView(sb)
        header("Vị trí watermark")
        val rgPos = RadioGroup(act).apply { orientation = RadioGroup.HORIZONTAL }
        val rbBottom = RadioButton(act).apply { id = View.generateViewId(); text = "Góc dưới" }
        val rbTop = RadioButton(act).apply { id = View.generateViewId(); text = "Góc trên" }
        rgPos.addView(rbBottom); rgPos.addView(rbTop)
        if (s.atTop) rbTop.isChecked = true else rbBottom.isChecked = true
        box.addView(rgPos)

        val scroll = ScrollView(act).apply { addView(box) }

        AlertDialog.Builder(act)
            .setTitle("Chỉnh watermark")
            .setView(scroll)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                s.title = etTitle.text.toString().trim()
                s.timeMode = when { rbRun.isChecked -> 1; rbFix.isChecked -> 2; else -> 0 }
                s.offsetMs = pickedOffset
                s.customMs = pickedMs
                s.showAddr = cbAddr.isChecked
                s.addrAuto = cbAddrAuto.isChecked
                s.addrText = etAddr.text.toString().trim()
                s.showCoord = cbCoord.isChecked
                s.coordAuto = cbCoordAuto.isChecked
                s.coordText = etCoord.text.toString().trim()
                s.note = etNote.text.toString().trim()
                s.sizePct = sb.progress + 60
                s.atTop = rbTop.isChecked
                onSaved()
            }
            .show()
    }
}
