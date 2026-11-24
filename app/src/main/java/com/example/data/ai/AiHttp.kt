package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * The single HTTP entry point for every provider adapter.
 *
 * It never logs headers or bodies. Responses are capped before parsing, and errors are reduced to a
 * short, sanitized message so a gateway echo cannot leak another user's prompt into the UI.
 */
internal object AiHttp {

    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_ERROR_CHARS = 320

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        // Retries are owned by AiConversationEngine so attempt counts stay predictable across
        // providers. Letting OkHttp retry as well multiplies waits and hides the real failure.
        .retryOnConnectionFailure(false)
        .build()

    /**
     * No provider legitimately redirects a generation request. Refusing every redirect prevents
     * Authorization/x-api-key headers and prompt bodies from being forwarded to another origin.
     */
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: JSONObject,
        provider: AiProviderId
    ): JSONObject = withContext(Dispatchers.IO) {
        execute(
            request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(jsonMediaType))
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build(),
            provider = provider
        )
    }

    suspend fun getJson(
        url: String,
        headers: Map<String, String>,
        provider: AiProviderId
    ): JSONObject = withContext(Dispatchers.IO) {
        execute(
            request = Request.Builder()
                .url(url)
                .get()
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build(),
            provider = provider
        )
    }

    private suspend fun execute(request: Request, provider: AiProviderId): JSONObject {
        val activeClient = noRedirectClient
        try {
            activeClient.newCall(request).execute().use { response ->
                // request() buffers up to the cap and returns false at EOF. Unlike
                // readByteArray(n), it does not demand exactly n bytes, so a normal short response
                // cannot throw EOFException. If the cap is reached, refuse the body unread.
                val source = response.body?.source()
                val bytes = if (source == null) {
                    ByteArray(0)
                } else {
                    if (source.request((MAX_RESPONSE_BYTES + 1).toLong())) {
                        throw AiException(
                            AiErrorKind.PARSE,
                            "The AI provider returned an oversized response."
                        )
                    }
                    source.readByteArray()
                }
                val raw = bytes.toString(Charsets.UTF_8)
                if (response.isSuccessful) {
                    if (raw.isBlank()) {
                        throw AiException(AiErrorKind.PARSE, "The AI provider returned an empty response.")
                    }
                    return try {
                        JSONObject(raw)
                    } catch (e: Exception) {
                        throw AiException(
                            AiErrorKind.PARSE,
                            "The AI provider returned a response the app could not read.",
                            cause = e
                        )
                    }
                }

                if (response.code in 300..399) {
                    throw AiException(
                        AiErrorKind.CONFIGURATION,
                        "The endpoint redirected the request. For safety IronPulse does not follow " +
                            "redirects for a custom endpoint; use the final base URL instead."
                    )
                }
                throw mapHttpError(response.code, raw)
            }
        } catch (e: AiException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw mapIOException(e)
        }
    }

    /**
     * Keeps the user-facing message aligned with the actual transport failure. HTTP status and
     * authentication failures are handled by [mapHttpError]; this method only classifies
     * I/O-level failures.
     */
    internal fun mapIOException(e: IOException): AiException = when (e) {
        is UnknownHostException -> AiException(
            AiErrorKind.NETWORK,
            "Could not resolve the AI provider's host. Check the endpoint host name and the device's DNS.",
            cause = e
        )
        is ConnectException, is NoRouteToHostException -> AiException(
            AiErrorKind.NETWORK,
            "Could not connect to the AI provider. The endpoint refused the connection or is unreachable.",
            cause = e
        )
        is SSLException -> AiException(
            AiErrorKind.NETWORK,
            "The secure connection to the AI provider failed. Check the endpoint certificate and the device clock.",
            cause = e
        )
        is SocketTimeoutException, is InterruptedIOException -> AiException(
            AiErrorKind.TIMEOUT,
            "The AI provider did not answer in time. Check the connection and try again.",
            cause = e
        )
        is EOFException -> AiException(
            AiErrorKind.PARSE,
            "The AI provider closed the response before it was complete.",
            cause = e
        )
        is ProtocolException -> AiException(
            AiErrorKind.PARSE,
            "The AI provider sent an invalid HTTP response.",
            cause = e
        )
        is SocketException -> AiException(
            AiErrorKind.NETWORK,
            "The connection to the AI provider was interrupted. Try again.",
            cause = e
        )
        else -> AiException(
            AiErrorKind.NETWORK,
            "The network request to the AI provider failed. Check the connection and the endpoint URL.",
            cause = e
        )
    }

    private fun mapHttpError(code: Int, rawBody: String): AiException {
        val providerMessage = extractErrorMessage(rawBody)
        val safeSuffix = providerMessage
            .takeIf { it.isNotBlank() }
            ?.take(MAX_ERROR_CHARS)
            ?.let { ": $it" }
            .orEmpty()
        return when (code) {
            401 -> AiException(AiErrorKind.AUTHENTICATION, "The AI provider rejected the API key.$safeSuffix")
            403 -> AiException(AiErrorKind.PERMISSION, "The API key is not allowed to use this model.$safeSuffix")
            404 -> AiException(AiErrorKind.MODEL_NOT_FOUND, "The selected model or endpoint was not found.$safeSuffix")
            408 -> AiException(AiErrorKind.TIMEOUT, "The AI provider timed out.$safeSuffix")
            409 -> AiException(AiErrorKind.INVALID_REQUEST, "The AI provider rejected the request.$safeSuffix")
            422 -> AiException(AiErrorKind.INVALID_REQUEST, "The AI provider could not process the request.$safeSuffix")
            429 -> AiException(AiErrorKind.RATE_LIMIT, "The AI provider is rate limiting requests. Wait a moment and try again.$safeSuffix")
            in 500..599 -> AiException(AiErrorKind.SERVER, "The AI provider is temporarily unavailable (HTTP $code).$safeSuffix")
            else -> AiException(AiErrorKind.INVALID_REQUEST, "The AI provider rejected the request (HTTP $code).$safeSuffix")
        }
    }

    /**
     * Pulls only the provider's human message out of an error envelope. The raw body is never
     * returned, because it can echo the prompt or contain account data.
     */
    internal fun extractErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return runCatching {
            val json = JSONObject(rawBody)
            val error = json.opt("error")
            when (error) {
                is JSONObject -> error.optString("message").ifBlank { error.optString("type") }
                is String -> error
                else -> json.optString("message")
            }
        }.getOrDefault("").trim()
    }

    internal fun isToolUnsupportedError(error: AiException): Boolean {
        val message = error.message.lowercase()
        return error.kind == AiErrorKind.INVALID_REQUEST &&
            (message.contains("tool") || message.contains("function")) &&
            (message.contains("not supported") ||
                message.contains("unsupported") ||
                message.contains("unknown") ||
                message.contains("does not support") ||
                message.contains("invalid"))
    }
}
