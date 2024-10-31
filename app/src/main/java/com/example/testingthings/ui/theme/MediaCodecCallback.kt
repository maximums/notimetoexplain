package com.example.testingthings.ui.theme

import android.media.MediaCodec
import android.media.MediaFormat
import com.example.testingthings.utils.logd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.net.Socket

class MediaCodecCallback(
    private val scope: CoroutineScope,
    private val bufferedWriter: BufferedOutputStream
) : MediaCodec.Callback() {
    private val bufferInfo by lazy { MediaCodec.BufferInfo() }

    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
        logd("onInputBufferAvailable:= buffer index is: $index")
    }

    override fun onOutputBufferAvailable(
        codec: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo
    ) {
        if (index >= 0) {
            scope.launch(Dispatchers.IO) {
                codec.getOutputBuffer(index)?.run {
                    val bytes = ByteArray(info.size)
                    get(bytes)
//                    get()

                    logd("Buffer size is: ${bytes.size}")

//                    bufferedWriter.write("Bytes: $bytes")
//                    bufferedWriter.newLine()
//                    bufferedWriter.flush()


                    bufferedWriter.write(bytes)
                    bufferedWriter.flush()
                }

                codec.releaseOutputBuffer(index, false)
            }
        }

        logd("onOutputBufferAvailable:= buffer index is: $index")
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        logd("onError:= buffer index is: $e")
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        logd("onOutputFormatChanged:= buffer index is: $codec")
    }
}