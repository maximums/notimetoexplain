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
import com.example.testingthings.ui.theme.MediaCodecCallback
import com.example.testingthings.utils.logd
import com.example.testingthings.utils.loge
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket

private const val CHANNEL_ID = "server_service_notification_channel"

class ServerService() : Service() {
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var serv: MediaSocketServer? = null

    override fun onCreate() {
        logd("onCreate")
        super.onCreate()

        NotificationChannel(CHANNEL_ID, "Server Service", NotificationManager.IMPORTANCE_LOW).also {
            it.description = "Server Service notification description for user. xD"
            getSystemService(NotificationManager::class.java).createNotificationChannel(it)
        }
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logd("onStartCommand")
        val notification = Notification.Builder(this, CHANNEL_ID).build()
        startForeground(203, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", RESULT_OK)
        val data = intent?.getParcelableExtra("DATA", Intent::class.java)

        if (resultCode == RESULT_OK && data != null) {
//            serverSocket = startServer(resultCode, data)
            val flow = MutableSharedFlow<ByteArray>()
            serv = MediaSocketServer(mediaFlow = flow).also { it.start() }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logd("onDestroy")

        mediaProjection?.stop()
        mediaProjection = null

        encoder?.stop()
        encoder?.release()
        encoder = null

        virtualDisplay?.release()
        virtualDisplay = null

        serv?.close()
        serv = null
    }

    private fun createEncoder(
        stream: BufferedOutputStream,
        w: Int = 1280,
        h: Int = 720
    ): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 6000000) // Set bitrate
            setInteger(MediaFormat.KEY_FRAME_RATE, 30) // Set frame rate
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10) // Set I-frame interval
        }

        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { codec ->
//            MediaSocketServer(mediaFlow = codec.myFlow())
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

fun MediaCodec.myFlow() = callbackFlow<ByteArray> {
    val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            logd("onInputBufferAvailable, InputBufferIndex: $index")
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            logd("Available Output Buffer Index: $index")

            if (index >= 0) {
                codec.getOutputBuffer(index)?.run {
                    val bytes = ByteArray(info.size).also(::get)

                    logd("Buffer size: ${info.size}")

                    trySend(bytes)
                }

                codec.releaseOutputBuffer(index, false)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            logd("onError, error: $e")
            cancel("An error occurred in MediaCode, error: $e")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            logd("onOutputFormatChanged, format: $format")
        }
    }

    setCallback(callback)

    awaitClose {
        setCallback(null)
    }
}