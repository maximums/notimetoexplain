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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket

private const val CHANNEL_ID = "server_service_notification_channel"

class ServerService() : Service() {
    private var serverSocket: ServerSocket? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val serverScope by lazy {
        CoroutineScope(context = Job() + Dispatchers.IO + CoroutineName("ServerScope"))
    }

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
            serverSocket = startServer(resultCode, data)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logd("onDestroy")

        serverSocket?.close()
        serverSocket = null

        serverScope.cancel()

        mediaProjection?.stop()
        mediaProjection = null

        encoder?.stop()
        encoder?.release()
        encoder = null

        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun startServer(resultCode: Int, data: Intent) = ServerSocket(8099).also { server ->
        try {
            serverScope.launch {
                while (isActive && !server.isClosed) {
                    val socket = server.accept()

                    logd("${socket.inetAddress}:${socket.port} is connected")

                    encoder = createEncoder(socket.outputStream.bufferedWriter())
                    startScreenRecording(resultCode, data)
                }
            }
        } catch (e: Exception) {
            loge(
                message = "Got an exception bro, stack trace is:\n${e.printStackTrace()}",
                exception = e
            )
        }
    }

    private fun createEncoder(stream: BufferedWriter, w: Int = 1280, h: Int = 720): MediaCodec {
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
            codec.setCallback(MediaCodecCallback(serverScope, stream))
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

//        processEncodedData()
    }

    private fun processEncodedData() {
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputBufferIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = encoder?.getOutputBuffer(outputBufferIndex)

                // Write encoded data to file
                outputBuffer?.let {
                    val bytes = ByteArray(bufferInfo.size)
                    it.get(bytes)
                    logd("DATA IS: $bytes")
//                    outputStream.write(bytes)
                }

                encoder?.releaseOutputBuffer(outputBufferIndex, false)
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }

        encoder?.stop()
        encoder?.release()
    }
}

class ClientHandler(private val socket: Socket, private val encoder: MediaCodec) {
    suspend operator fun invoke() {
        socket.use { client ->
            client.outputStream.bufferedWriter().use { writer ->
                while (true) {
                    writer.write("Hello ${client.inetAddress}:${client.port}!")
                    writer.newLine()
                    writer.flush()

                    delay(1_000)
                }
            }
//            client.inputStream.bufferedReader().use { reader ->
//                client.outputStream.bufferedWriter().use { writer ->
//                    val bufferInfo = MediaCodec.BufferInfo()
//                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
//                    if (outputBufferIndex >= 0) {
//                        val encodedData = encoder.getOutputBuffer(outputBufferIndex)
//                        if (encodedData != null) {
//                            // Send encoded data through the output stream to the receiver device
//                            val buffer = ByteArray(bufferInfo.size)
//                            encodedData.get(buffer)
//                            while (true) {
//                                delay(1_000)
//                                writer.write("buffer value: $buffer")
//                                writer.newLine()
//                                writer.flush()
//                            }
////                            writer.write(buffer, bufferInfo.offset, bufferInfo.size)
////                            writer.flush()
////                            writer.bufferedWriter()
//
//                            // Release the output buffer
////                            encoder.releaseOutputBuffer(outputBufferIndex, false)
//                        }
//                    }
////                    while (true) {
////                        delay(1_000)
////                        writer.run {
////                            write("Hello ${client.inetAddress}:${client.port}!")
////                            newLine()
////                            write("${}")
////                            flush()
////                        }
////                    }
//                }
//            }
        }
    }
}