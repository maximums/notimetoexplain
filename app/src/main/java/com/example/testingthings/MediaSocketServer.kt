@file:Suppress("unused")

package com.example.testingthings

import com.example.testingthings.utils.logd
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class MediaSocketServer(
    private val mediaFlow: SharedFlow<ByteArray>,
    private val port: Int = 8099
) : AutoCloseable {
    private val serverSocket = ServerSocket(port).also { logd("Server started on port: $port") }
    private val serverScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("ServerScope"))
    }

    fun start() = with(serverScope) {
        launch {
            while (isActive && !serverSocket.isClosed) {
                try {
                    serverSocket.accept().use(::streamMediaData)
                } catch (e: SocketException) {
                    logd("A error occurred while accepting connections e: $e")
                    break
                }
            }
        }
    }

    private fun streamMediaData(socket: Socket) = with(serverScope) {
        logd("Accepted connection from: ${socket.inetAddress.hostAddress}")

        launch {
            mediaFlow.collect { data ->
                socket.runCatching {
                    outputStream.buffered().use { stream ->
                        stream.write(data)
                        stream.flush()
                    }
                }.onFailure {
                    logd("An error occurred while streaming data: $it")
                    cancel("Canceling the coroutine for client: ${socket.inetAddress.hostAddress}")
                }
            }
        }
    }

    override fun close() {
        if (!serverSocket.isClosed) serverSocket.close()

        serverScope.cancel(message = "Server on port: $port has stopped")
    }
}