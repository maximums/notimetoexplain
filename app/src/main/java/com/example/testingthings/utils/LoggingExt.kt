package com.example.testingthings.utils

import android.util.Log

fun Any.logd(message: String) = Log.d(this::class.simpleName, message)

fun Any.loge(exception: Throwable, message: String? = null) =
    Log.e(this::class.simpleName, message, exception)