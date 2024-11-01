package com.example.testingthings

import android.media.MediaCodec
import android.media.MediaFormat
import com.example.testingthings.utils.logd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import kotlin.run

class MediaCodecCallback(
    private val mediaFlow: MutableSharedFlow<ByteArray>,
    private val scope: CoroutineScope
) : MediaCodec.Callback() {
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

                    mediaFlow.emit(bytes)
                }

                codec.releaseOutputBuffer(index, false)
            }
        }

//        logd("onOutputBufferAvailable:= buffer index is: $index")
    }

    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
        logd("onError:= buffer index is: $e")
    }

    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
        logd("onOutputFormatChanged:= buffer index is: $codec")
    }
}