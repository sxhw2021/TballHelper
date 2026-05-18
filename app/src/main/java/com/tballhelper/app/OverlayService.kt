package com.tballhelper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.tballhelper.app.data.GameConfig
import com.tballhelper.app.data.TemplateManager
import com.tballhelper.app.overlay.OverlayView
import com.tballhelper.app.sdk.BilliardsSDK
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var billiardsSDK: BilliardsSDK
    private lateinit var templateManager: TemplateManager
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureHandler: Handler? = null
    private var captureRunnable: Runnable? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var screenWidth = 1080
    private var screenHeight = 2376
    private var currentGame: GameConfig? = null

    companion object {
        const val CHANNEL_ID = "tball_helper_channel"
        const val NOTIFICATION_ID = 10001
        private const val CAPTURE_INTERVAL = 100L
    }

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            templateManager = TemplateManager(this)
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            billiardsSDK = BilliardsSDK()

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())

            overlayView = OverlayView(this)
            setupOverlayWindow()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val mode = it.getStringExtra("mode")
            when (mode) {
                "capture_template" -> {
                    val gameName = it.getStringExtra("game_name") ?: "新游戏"
                    val gameId = it.getStringExtra("game_id") ?: "default"
                    screenWidth = it.getIntExtra("screen_width", 1080)
                    screenHeight = it.getIntExtra("screen_height", 2376)
                }
                else -> {
                    val game = templateManager.getCurrentGame()
                    if (game != null) {
                        currentGame = game
                        screenWidth = game.screenWidth
                        screenHeight = game.screenHeight
                    }
                }
            }

            val projectionCode = it.getIntExtra("projection_code", -1)
            val projectionData = it.getParcelableExtra<Intent>("projection_data")
            if (projectionCode != -1 && projectionData != null) {
                startCapture(projectionCode, projectionData)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlayWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        windowManager.addView(overlayView, params)
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight,
            480,
            60,
            imageReader?.surface,
            null,
            null
        )

        captureHandler = Handler(Looper.getMainLooper())
        captureRunnable = object : Runnable {
            override fun run() {
                captureScreen()
                captureHandler?.postDelayed(this, CAPTURE_INTERVAL)
            }
        }
        captureHandler?.post(captureRunnable!!)
    }

    private fun captureScreen() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bitmap = Bitmap.createBitmap(
                screenWidth, screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            processScreenCapture(bitmap)
            bitmap.recycle()
        } finally {
            image.close()
        }
    }

    private fun processScreenCapture(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                val result = billiardsSDK.processFrame(bitmap)
                val center = result.templateCenter
                val aim = result.aimPoint

                if (center != null && aim != null) {
                    val lines = calculateExtendedAimLine(center, aim)
                    val predictions = calculatePredictionPaths(center, aim)
                    withContext(Dispatchers.Main) {
                        overlayView.updateAimingData(center, aim, lines, predictions)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        overlayView.clear()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateExtendedAimLine(center: android.graphics.PointF, aim: android.graphics.PointF): List<OverlayView.AimLine> {
        val dx = aim.x - center.x
        val dy = aim.y - center.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()
        if (length < 1f) return emptyList()

        val dirX = dx / length
        val dirY = dy / length

        val extendedX = center.x + dirX * screenWidth * 2
        val extendedY = center.y + dirY * screenHeight * 2

        val oppositeX = center.x - dirX * screenWidth * 2
        val oppositeY = center.y - dirY * screenHeight * 2

        return listOf(
            OverlayView.AimLine(center.x, center.y, extendedX, extendedY),
            OverlayView.AimLine(center.x, center.y, oppositeX, oppositeY)
        )
    }

    private fun calculatePredictionPaths(
        center: android.graphics.PointF,
        aim: android.graphics.PointF
    ): List<OverlayView.PredictionPath> {
        val pocketPositions = listOf(
            android.graphics.PointF(0f, 0f),
            android.graphics.PointF(screenWidth / 2f, 0f),
            android.graphics.PointF(screenWidth.toFloat(), 0f),
            android.graphics.PointF(0f, screenHeight.toFloat()),
            android.graphics.PointF(screenWidth / 2f, screenHeight.toFloat()),
            android.graphics.PointF(screenWidth.toFloat(), screenHeight.toFloat())
        )

        return pocketPositions.take(3).map { pocket ->
            OverlayView.PredictionPath(
                fromX = center.x,
                fromY = center.y,
                toX = aim.x,
                toY = aim.y,
                pocketX = pocket.x,
                pocketY = pocket.y
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureRunnable?.let { captureHandler?.removeCallbacks(it) }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
        }
        serviceScope.cancel()
    }
}
