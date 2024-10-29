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
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import com.example.testingthings.utils.logd
import com.example.testingthings.utils.loge
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

private const val CHANNEL_ID = "server_service_notification_channel"

class ServerService() : Service() {
    private var serverSocket: ServerSocket? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    private var outputStream: OutputStream? = null
    private var mediaRecorder: MediaRecorder? = null
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
        setupEncoder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logd("onStartCommand")
        val notification = Notification.Builder(this, CHANNEL_ID).build()
        startForeground(203, notification)

        val resultCode = intent?.getIntExtra("RESULT_CODE", RESULT_OK)
        val data = intent?.getParcelableExtra("DATA", Intent::class.java)

        if (resultCode == RESULT_OK && data != null) {
            serverSocket = startServer()
//            startScreenRecording(resultCode, data)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logd("onDestroy")

        serverSocket?.close()
        serverSocket = null

        serverScope.cancel()

        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null

        mediaProjection?.stop()
        mediaProjection = null

        encoder?.stop()
        encoder?.release()
        encoder = null

        outputStream?.close()
        outputStream = null

        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
//        mediaRecorder = MediaRecorder(this).apply {
////            setAudioSource(MediaRecorder.AudioSource.MIC)
//            setVideoSource(MediaRecorder.VideoSource.SURFACE)
//            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//
//            val outputFile = File(externalCacheDir, "screen_record.mp4")
//
//            setOutputFile(outputFile.absolutePath)
//
//            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
////            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
//            setVideoSize(1280, 720)  // Adjust as necessary
//            setVideoFrameRate(30)
//            setVideoEncodingBitRate(5 * 1000 * 1000)
//
//            try {
//                prepare()
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        }

        // Create a virtual display for screen recording
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorderDisplay",
            1280,
            720,
            resources.displayMetrics.densityDpi,
            0,
            encoder?.createInputSurface(),
            null,
            null
        ).also { vDisplay ->
            serverScope.launch {
                val bufferInfo = MediaCodec.BufferInfo()

                while (outputStream != null && encoder != null) {
                    val outputBufferIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 0)
                    if (outputBufferIndex >= 0) {
                        val encodedData = encoder!!.getOutputBuffer(outputBufferIndex)
                        if (encodedData != null) {
                            // Send encoded data through the output stream to the receiver device
                            val buffer = ByteArray(bufferInfo.size)
                            encodedData.get(buffer)
                            outputStream!!.write(buffer, bufferInfo.offset, bufferInfo.size)
                            outputStream!!.flush()
                            outputStream!!.bufferedWriter()

                            // Release the output buffer
                            encoder!!.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    }
                }
            }
        }
        encoder?.start()
//        mediaRecorder?.start()
    }

    private fun setupEncoder(width: Int = 1280, height: Int = 720) {
        val format =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                .apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, 6000000) // Set bitrate
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30) // Set frame rate
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10) // Set I-frame interval
                }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { codec ->
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun startServer() = ServerSocket(8099).also { server ->
        try {
            serverScope.launch {
                while (isActive && !server.isClosed) {
                    val socket = server.accept()
                    outputStream = socket.outputStream

                    launch {
                        ClientHandler(socket = socket).invoke()
//                        while (socket.isConnected && !socket.isClosed) {
//                            val msg = socket.inputStream.bufferedReader().readLine()
//                            outputStream?.bufferedWriter()
//                                ?.write("Hello ${socket.inetAddress} reply to $msg")
//                            outputStream?.flush()
//
//                            logd("${socket.inetAddress}: $msg")
//                        }
                    }

                    logd("${socket.inetAddress}:${socket.port} is connected")
                }
            }
        } catch (e: Exception) {
            loge(
                message = "Got an exception bro, stack trace is:\n${e.printStackTrace()}",
                exception = e
            )
        }
    }
}

class ClientHandler(private val socket: Socket) {
    suspend operator fun invoke() {
        socket.use { client ->
            client.inputStream.bufferedReader().use { reader ->
                client.outputStream.bufferedWriter().use { writer ->
                    while (reader.readLine().also(::logd) != null) {
                        writer.run {
                            write("Hello ${client.inetAddress}:${client.port}!")
                            newLine()
                            flush()
                        }
                    }
                }
            }
        }
    }
}