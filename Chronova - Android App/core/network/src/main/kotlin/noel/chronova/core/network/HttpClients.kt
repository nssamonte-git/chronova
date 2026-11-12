package noel.chronova.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** The one HTTP client the app shares; endpoints that need longer timeouts derive from it with `newBuilder()`. */
fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
