package com.cameradinhvi.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private lateinit var settings: WmSettings
    private lateinit var loc: LocationHelper
    private val renderer = WatermarkRenderer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExec = Executors.newSingleThreadExecutor()

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var shutter: ShutterView
    private lateinit var thumb: ImageView
    private lateinit var tvPhoto: TextView
    private lateinit var tvVideo: TextView
    private lateinit var tvRec: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var btnSwitch: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var overlayEffect: OverlayEffect? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var videoMode = false
    private var useFront = false
    private var busy = false
    private var lastUri: Uri? = null
    private var lastMime = "image/jpeg"

    private val fmtTime = SimpleDateFormat("HH:mm", Locale.US)
    private val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val fmtFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val weekdays = arrayOf("", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionsResult()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ vòng đời

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        settings = WmSettings(this)
        loc = LocationHelper(this)
        buildUi()

        val need = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT < 29) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val missing = need.filter { !granted(it) }
        if (missing.isEmpty()) onPermissionsResult() else permLauncher.launch(missing.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) loc.start()
    }

    override fun onPause() {
        super.onPause()
        recording?.stop()
        recording = null
        loc.stop()
    }

    private fun onPermissionsResult() {
        if (!granted(Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Cần quyền Camera để dùng app", Toast.LENGTH_LONG).show()
            return
        }
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) loc.start()
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                applyModeLayout()
            } catch (e: Exception) {
                Log.e(TAG, "camera provider", e)
                toast("Không mở được camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------------------------------------------------------------ giao diện

    private fun pill(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x66000000) }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Thanh trên
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val btnWm = pill("✎ Watermark").apply {
            setOnClickListener { if (recording == null) SettingsDialog.show(this@MainActivity, settings) { toast("Đã lưu watermark") } }
        }
        btnSwitch = pill("⟲ Đổi cam").apply {
            setOnClickListener {
                if (recording != null || busy) return@setOnClickListener
                useFront = !useFront
                bindCamera()
            }
        }
        tvRec = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xCCE53935.toInt()) }
            visibility = View.GONE
        }
        topBar.addView(btnWm)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(tvRec)
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(btnSwitch)
        root.addView(topBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Thanh dưới
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
            setBackgroundColor(0x55000000)
        }
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        tvPhoto = TextView(this).apply { text = "ẢNH"; textSize = 15f; setPadding(dp(16), dp(6), dp(16), dp(10)); setOnClickListener { setMode(false) } }
        tvVideo = TextView(this).apply { text = "VIDEO"; textSize = 15f; setPadding(dp(16), dp(6), dp(16), dp(10)); setOnClickListener { setMode(true) } }
        modeRow.addView(tvPhoto); modeRow.addView(tvVideo)
        bottomBar.addView(modeRow)

        val ctrlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF333333.toInt()); setStroke(dp(2), Color.WHITE) }
            clipToOutline = true
            setOnClickListener { openLast() }
        }
        shutter = ShutterView(this).apply { setOnClickListener { onShutter() } }
        ctrlRow.addView(thumb, LinearLayout.LayoutParams(dp(52), dp(52)))
        ctrlRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        ctrlRow.addView(shutter, LinearLayout.LayoutParams(dp(76), dp(76)))
        ctrlRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        ctrlRow.addView(View(this), LinearLayout.LayoutParams(dp(52), dp(52)))
        bottomBar.addView(ctrlRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(bottomBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(dp(12), bars.top + dp(8), dp(12), dp(8))
            bottomBar.setPadding(dp(16), dp(8), dp(16), bars.bottom + dp(16))
            insets
        }
        updateModeUi()
    }

    private fun updateModeUi() {
        tvPhoto.setTextColor(if (!videoMode) 0xFFFFC107.toInt() else Color.WHITE)
        tvVideo.setTextColor(if (videoMode) 0xFFFFC107.toInt() else Color.WHITE)
        shutter.videoMode = videoMode
        shutter.recording = recording != null
    }

    private fun setMode(video: Boolean) {
        if (recording != null || busy || video == videoMode) return
        videoMode = video
        updateModeUi()
        applyModeLayout()
    }

    /** Khung xem trước 3:4 (ảnh) hoặc 9:16 (video) để ảnh/video ra đúng như màn hình. */
    private fun applyModeLayout() {
        root.doOnLayout {
            val w = root.width
            val ratio = if (videoMode) 16f / 9f else 4f / 3f
            val h = min((w * ratio).toInt(), root.height)
            val lp = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            previewView.layoutParams = lp
            previewView.doOnLayout { bindCamera() }
        }
    }

    // ------------------------------------------------------------------ camera

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        overlayEffect?.close()
        overlayEffect = null
        imageCapture = null
        videoCapture = null

        var selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            if (!provider.hasCamera(selector)) {
                useFront = !useFront
                selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (_: Exception) {
        }

        val strategy = if (videoMode) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                       else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val rs = ResolutionSelector.Builder().setAspectRatioStrategy(strategy).build()

        val preview = Preview.Builder().setResolutionSelector(rs).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val targets = if (videoMode) CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE else CameraEffect.PREVIEW
        val effect = OverlayEffect(targets, 0, mainHandler) { t -> Log.e(TAG, "overlay error", t) }
        effect.setOnDrawListener { frame -> drawOverlay(frame) }

        val builder = UseCaseGroup.Builder().addUseCase(preview)
        previewView.viewPort?.let { builder.setViewPort(it) }

        if (videoMode) {
            val recorder = Recorder.Builder()
                .setAspectRatio(AspectRatio.RATIO_16_9)
                .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val vc = VideoCapture.Builder(recorder)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .build()
            videoCapture = vc
            builder.addUseCase(vc)
        } else {
            val ic = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(rs)
                .build()
            imageCapture = ic
            builder.addUseCase(ic)
        }

        try {
            provider.bindToLifecycle(this, selector, builder.addEffect(effect).build())
            overlayEffect = effect
        } catch (e: Exception) {
            Log.e(TAG, "bind with effect failed", e)
            // Máy không hỗ trợ hiệu ứng: vẫn cho dùng (video sẽ không có watermark).
            try {
                effect.close()
                provider.unbindAll()
                val b2 = UseCaseGroup.Builder().addUseCase(preview)
                previewView.viewPort?.let { b2.setViewPort(it) }
                imageCapture?.let { b2.addUseCase(it) }
                videoCapture?.let { b2.addUseCase(it) }
                provider.bindToLifecycle(this, selector, b2.build())
                toast("Máy không hỗ trợ vẽ watermark trực tiếp lên video")
            } catch (e2: Exception) {
                Log.e(TAG, "bind failed", e2)
                toast("Lỗi mở camera: ${e2.message}")
            }
        }
    }

    /** Vẽ watermark lên từng khung hình (xem trước + video). */
    private fun drawOverlay(frame: Frame): Boolean {
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val sensorToView = previewView.sensorToViewTransform ?: return true
        val viewToSensor = Matrix()
        if (!sensorToView.invert(viewToSensor)) return true
        val viewToBuffer = Matrix(viewToSensor)
        viewToBuffer.postConcat(frame.sensorToBufferTransform)
        canvas.setMatrix(viewToBuffer)
        val rect = RectF(0f, 0f, previewView.width.toFloat(), previewView.height.toFloat())
        renderer.draw(canvas, rect, currentData())
        return true
    }

    private fun currentData(): WmData {
        val t = settings.displayTimeMs()
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val date = Date(t)
        val lines = mutableListOf<String>()
        val l = loc.location
        if (settings.showAddr) {
            val a = if (settings.addrAuto) {
                loc.address ?: if (l == null) "Đang lấy vị trí..." else "Đang lấy địa chỉ..."
            } else settings.addrText
            if (a.isNotBlank()) lines.add(a)
        }
        if (settings.showCoord) {
            val c = if (settings.coordAuto) {
                if (l != null) String.format(Locale.US, "%.6f, %.6f", l.latitude, l.longitude) else ""
            } else settings.coordText
            if (c.isNotBlank()) lines.add("Tọa độ: $c")
        }
        if (settings.note.isNotBlank()) lines.add(settings.note)
        return WmData(
            title = settings.title,
            time = fmtTime.format(date),
            date = fmtDate.format(date),
            weekday = weekdays[cal.get(Calendar.DAY_OF_WEEK)],
            lines = lines,
            sizePct = settings.sizePct,
            atTop = settings.atTop
        )
    }

    // ------------------------------------------------------------------ chụp / quay

    private fun onShutter() {
        if (videoMode) toggleRecording() else takePhoto()
    }

    private fun flash() {
        previewView.animate().alpha(0.2f).setDuration(60).withEndAction {
            previewView.animate().alpha(1f).setDuration(120).start()
        }.start()
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        if (busy) return
        busy = true
        val data = currentData()
        flash()
        ic.takePicture(ioExec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val out = renderPhoto(image, data)
                    val uri = saveJpeg(out)
                    val small = Bitmap.createScaledBitmap(out, 160, (160f * out.height / out.width).toInt().coerceAtLeast(1), true)
                    out.recycle()
                    runOnUiThread {
                        busy = false
                        thumb.setImageBitmap(small)
                        if (uri != null) { lastUri = uri; lastMime = "image/jpeg" }
                        toast("Đã lưu ảnh vào Pictures/CameraDinhVi")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "save photo", e)
                    runOnUiThread { busy = false; toast("Lỗi lưu ảnh: ${e.message}") }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture", exception)
                runOnUiThread { busy = false; toast("Lỗi chụp: ${exception.message}") }
            }
        })
    }

    private fun renderPhoto(image: ImageProxy, data: WmData): Bitmap {
        var bmp: Bitmap
        val crop = image.cropRect
        val rot = image.imageInfo.rotationDegrees
        try {
            bmp = image.toBitmap()
        } finally {
            image.close()
        }
        if (crop.width() > 0 && crop.height() > 0 &&
            (crop.width() != bmp.width || crop.height() != bmp.height) &&
            crop.left >= 0 && crop.top >= 0 && crop.right <= bmp.width && crop.bottom <= bmp.height
        ) {
            val c = Bitmap.createBitmap(bmp, crop.left, crop.top, crop.width(), crop.height())
            if (c != bmp) bmp.recycle()
            bmp = c
        }
        if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r != bmp) bmp.recycle()
            bmp = r
        }
        if (!bmp.isMutable || bmp.config != Bitmap.Config.ARGB_8888) {
            val c = bmp.copy(Bitmap.Config.ARGB_8888, true)
            bmp.recycle()
            bmp = c
        }
        renderer.draw(Canvas(bmp), RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()), data)
        return bmp
    }

    private fun saveJpeg(bmp: Bitmap): Uri? {
        val name = "IMG_" + fmtFile.format(Date()) + ".jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraDinhVi")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: throw IllegalStateException("Không tạo được file ảnh")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            cv.clear()
            cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, cv, null, null)
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CameraDinhVi")
            dir.mkdirs()
            val f = File(dir, name)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            MediaScannerConnection.scanFile(this, arrayOf(f.absolutePath), arrayOf("image/jpeg")) { _, uri ->
                runOnUiThread { if (uri != null) { lastUri = uri; lastMime = "image/jpeg" } }
            }
            return null
        }
    }

    @SuppressLint("MissingPermission")
    private fun toggleRecording() {
        val vc = videoCapture ?: return
        val cur = recording
        if (cur != null) {
            cur.stop()
            recording = null
            updateModeUi()
            return
        }
        val name = "VID_" + fmtFile.format(Date())
        val pending: PendingRecording = if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CameraDinhVi")
            }
            val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(cv)
                .build()
            vc.output.prepareRecording(this, opts)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "CameraDinhVi")
            dir.mkdirs()
            vc.output.prepareRecording(this, FileOutputOptions.Builder(File(dir, "$name.mp4")).build())
        }
        if (granted(Manifest.permission.RECORD_AUDIO)) pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> {
                    tvRec.text = "● 00:00"
                    tvRec.visibility = View.VISIBLE
                    btnSwitch.alpha = 0.4f
                }
                is VideoRecordEvent.Status -> {
                    val s = ev.recordingStats.recordedDurationNanos / 1_000_000_000L
                    tvRec.text = String.format(Locale.US, "● %02d:%02d", s / 60, s % 60)
                }
                is VideoRecordEvent.Finalize -> {
                    tvRec.visibility = View.GONE
                    btnSwitch.alpha = 1f
                    recording = null
                    updateModeUi()
                    if (ev.hasError() && ev.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
                        toast("Lỗi quay video (mã ${ev.error})")
                    } else {
                        val uri = ev.outputResults.outputUri
                        if (uri != Uri.EMPTY) {
                            lastUri = uri; lastMime = "video/mp4"
                            loadVideoThumb(uri)
                        }
                        toast("Đã lưu video vào Movies/CameraDinhVi")
                    }
                }
                else -> {}
            }
        }
        updateModeUi()
    }

    private fun loadVideoThumb(uri: Uri) {
        if (Build.VERSION.SDK_INT < 29) return
        ioExec.execute {
            try {
                val b = contentResolver.loadThumbnail(uri, android.util.Size(160, 160), null)
                runOnUiThread { thumb.setImageBitmap(b) }
            } catch (_: Exception) {
            }
        }
    }

    private fun openLast() {
        val uri = lastUri ?: run { toast("Chưa có ảnh/video nào"); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, lastMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            toast("Không mở được, hãy xem trong Thư viện ảnh")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object { private const val TAG = "CameraDinhVi" }
}
