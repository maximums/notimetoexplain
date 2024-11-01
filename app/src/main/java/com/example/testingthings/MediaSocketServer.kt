package com.example.testingthings

import com.example.testingthings.utils.logd
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class MediaSocketServer(
    private val mediaFlow: SharedFlow<ByteArray>,
    private val scope: CoroutineScope,
    private val port: Int = 8099
) : AutoCloseable {
    private val serverSocket = ServerSocket(port).also { logd("Server started on port: $port") }

    fun start() = with(scope) {
        launch(CoroutineName("Connections Accepting Coroutine")) {
            while (isActive && !serverSocket.isClosed) {
                logd("ACCEPTING CONNECTIONS")
                try {
                    serverSocket.accept().also(::streamMediaData)
                } catch (e: SocketException) {
                    logd("A error occurred while accepting connections e: $e")
                    cancel("Socket closed, stopping server coroutine")
                }
            }
        }
    }

    private fun streamMediaData(socket: Socket) = with(scope) {
        logd("Accepted connection from: ${socket.inetAddress.hostAddress}")

        val stream = try {
            socket.outputStream
        } catch (e: SocketException) {
            logd("An error occurred while getting outputStream: $e")
            null
        }

        launch {
            mediaFlow.collect { data ->
                try {
                    stream?.write(data)
                    stream?.flush()
                } catch (io: IOException) {
                    logd("An error occurred while streaming data: $io")
                    cancel("Canceling Client[${socket.inetAddress.hostAddress}] Processing Coroutine")
                }
            }
        }
    }

    override fun close() {
        logd("Stopping server on port: $port")

        if (!serverSocket.isClosed) serverSocket.close()
    }
}