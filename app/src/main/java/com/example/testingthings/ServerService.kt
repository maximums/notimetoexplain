package com.example.testingthings

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import com.example.testingthings.utils.logd
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow

private const val CHANNEL_ID = "server_service_notification_channel"

class ServerService() : Service() {
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var socketServer: MediaSocketServer? = null
    private val mediaDataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Service Main Scope"))

    override fun onCreate() {
        logd("onCreate")

        NotificationChannel(CHANNEL_ID, "Server Service", NotificationManager.IMPORTANCE_LOW).also {
            it.description = "Server Service notification description for user. xD"
            getSystemService(NotificationManager::class.java).createNotificationChannel(it)
        }

        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        encoder = createEncoder()
        socketServer = MediaSocketServer(mediaFlow = mediaDataFlow, scope = serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logd("onStartCommand")

        val notification = Notification.Builder(this, CHANNEL_ID).build()
        startForeground(203, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", RESULT_OK)
        val data = intent?.getParcelableExtra("DATA", Intent::class.java)

        if (resultCode == RESULT_OK && data != null) {
            socketServer?.start()
            startScreenRecording(resultCode, data)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logd("onDestroy")

        socketServer?.close()
        socketServer = null

        mediaProjection?.stop()
        mediaProjection = null

        virtualDisplay?.release()
        virtualDisplay = null

        encoder?.stop()
        encoder?.release()
        encoder = null

        serviceScope.cancel()
    }

    private fun createEncoder(width: Int = 1280, height: Int = 720): MediaCodec {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, width, height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 6000000) // Set bitrate
            setInteger(MediaFormat.KEY_FRAME_RATE, 30) // Set frame rate
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10) // Set I-frame interval
        }

        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { codec ->
            codec.setCallback(MediaCodecCallback(mediaFlow = mediaDataFlow, scope = serviceScope))
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)

        val inputSurface = encoder?.createInputSurface()
        encoder?.start()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorderDisplay",
            1280,
            720,
            resources.displayMetrics.densityDpi,
            0,
            inputSurface,
            null,
            null
        )
    }
}