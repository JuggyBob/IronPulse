package com.example

import com.example.data.ai.AiErrorKind
import com.example.data.ai.AiException
import com.example.data.ai.AiHttp
import com.example.data.ai.AiProviderId
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Regression coverage for the shared provider HTTP layer.
 *
 * 5.1.0 read every response with an exact-length Okio call, which threw EOFException for every
 * normal short provider response and surfaced as "Could not reach provider". These tests exercise
 * real local HTTP responses and the I/O error classifier so that regression cannot return.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiHttpTest {

    @Test
    fun `a short successful response is read without EOF`() = withServer(
        status = 200,
        body = """{"ok":true,"text":"hello"}"""
    ) { url ->
        val response = post(url)
        assertEquals(true, response.getBoolean("ok"))
        assertEquals("hello", response.getString("text"))
    }

    @Test
    fun `HTTP 401 is an authentication failure with the provider message`() {
        val error = assertError(401, """{"error":{"message":"invalid api key"}}""")
        assertEquals(AiErrorKind.AUTHENTICATION, error.kind)
        assertTrue(error.message.contains("rejected the API key"))
        assertTrue(error.message.contains("invalid api key"))
    }

    @Test
    fun `HTTP 400 is an invalid request not a network failure`() {
        val error = assertError(400, """{"error":{"message":"bad model parameter"}}""")
        assertEquals(AiErrorKind.INVALID_REQUEST, error.kind)
        assertTrue(error.message.contains("bad model parameter"))
    }

    @Test
    fun `HTTP 404 is a model or endpoint failure`() {
        val error = assertError(404, """{"error":{"message":"model not found"}}""")
        assertEquals(AiErrorKind.MODEL_NOT_FOUND, error.kind)
    }

    @Test
    fun `HTTP 429 is rate limiting`() {
        val error = assertError(429, """{"error":{"message":"quota exceeded"}}""")
        assertEquals(AiErrorKind.RATE_LIMIT, error.kind)
        assertTrue(error.message.contains("quota exceeded"))
    }

    @Test
    fun `HTTP 500 is a provider-side failure`() {
        val error = assertError(500, """{"error":{"message":"upstream unavailable"}}""")
        assertEquals(AiErrorKind.SERVER, error.kind)
        assertTrue(error.message.contains("HTTP 500"))
    }

    @Test
    fun `malformed success body is a parse failure`() {
        val error = assertError(200, """{"ok":""")
        assertEquals(AiErrorKind.PARSE, error.kind)
    }

    @Test
    fun `an oversized response is rejected before parsing`() {
        val error = assertError(200, "x".repeat(4 * 1024 * 1024 + 1))
        assertEquals(AiErrorKind.PARSE, error.kind)
        assertTrue(error.message.contains("oversized"))
    }

    @Test
    fun `EOF while reading is a parse failure not a connection failure`() {
        val error = AiHttp.mapIOException(EOFException("short body"))
        assertEquals(AiErrorKind.PARSE, error.kind)
        assertTrue(error.message.contains("before it was complete"))
    }

    @Test
    fun `DNS failures are described as host resolution failures`() {
        val error = AiHttp.mapIOException(UnknownHostException("api.example.invalid"))
        assertEquals(AiErrorKind.NETWORK, error.kind)
        assertTrue(error.message.contains("resolve"))
        assertTrue(error.message.contains("DNS"))
    }

    @Test
    fun `connection failures are described as connection failures`() {
        val error = AiHttp.mapIOException(ConnectException("refused"))
        assertEquals(AiErrorKind.NETWORK, error.kind)
        assertTrue(error.message.contains("connect"))
    }

    @Test
    fun `TLS failures are not described as internet loss`() {
        val error = AiHttp.mapIOException(SSLHandshakeException("bad certificate"))
        assertEquals(AiErrorKind.NETWORK, error.kind)
        assertTrue(error.message.contains("secure connection"))
        assertTrue(error.message.contains("certificate"))
    }

    @Test
    fun `socket timeouts keep the timeout kind`() {
        val error = AiHttp.mapIOException(SocketTimeoutException("read timed out"))
        assertEquals(AiErrorKind.TIMEOUT, error.kind)
    }

    @Test
    fun `generic IO failures remain network failures`() {
        val error = AiHttp.mapIOException(IOException("socket closed"))
        assertEquals(AiErrorKind.NETWORK, error.kind)
    }

    private fun post(url: String): JSONObject = runBlocking {
        AiHttp.postJson(
            url = url,
            headers = emptyMap(),
            body = JSONObject("""{"model":"test"}"""),
            provider = AiProviderId.GOOGLE
        )
    }

    private fun assertError(status: Int, body: String): AiException {
        var captured: AiException? = null
        withServer(status, body) { url ->
            try {
                post(url)
                fail("Expected AiException for HTTP $status")
            } catch (e: AiException) {
                captured = e
            }
        }
        return captured ?: throw AssertionError("No AiException was thrown")
    }

    /**
     * Starts a one-shot local HTTP server. The server is always stopped; no credentials are sent or
     * logged because every test body is synthetic.
     */
    private fun withServer(
        status: Int,
        body: String,
        block: (String) -> Unit
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            try {
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}/v1/chat/completions")
        } finally {
            server.stop(0)
        }
    }
}
