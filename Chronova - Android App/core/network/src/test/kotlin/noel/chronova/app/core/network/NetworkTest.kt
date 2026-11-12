package noel.chronova.app.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Call
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun call(): Call = defaultHttpClient().newCall(Request.Builder().url(server.url("/up")).build())

    @Test
    fun awaitReturnsTheResponse() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertEquals(204, call().await().use { it.code })
    }

    @Test
    fun awaitThrowsWhenTheServerIsGone() = runTest {
        server.close()
        val failure = runCatching { call().await() }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
    }

    @Test
    fun cancellingTheCoroutineCancelsTheRequest() = runBlocking {
        // The response is held back, so the request waits until it is cancelled. Real threads, real time.
        server.enqueue(MockResponse.Builder().headersDelay(10, TimeUnit.SECONDS).build())
        val call = call()
        val waiting = launch(Dispatchers.IO) { runCatching { call.await() } }
        withTimeout(5_000) { while (server.requestCount == 0) delay(10) }
        waiting.cancelAndJoin()
        assertTrue(call.isCanceled())
    }

    @Serializable
    private data class Reply(val name: String, val note: String? = null)

    @Test
    fun jsonIgnoresUnknownFieldsAndLeavesOutNulls() {
        assertEquals(Reply("a"), ApiJson.json.decodeFromString(Reply.serializer(), """{"name":"a","fromANewerServer":1}"""))
        val encoded = ApiJson.json.encodeToString(Reply.serializer(), Reply("a"))
        assertFalse(encoded, encoded.contains("note"))
    }
}
