package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

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
        .retryOnConnectionFailure(true)
        .build()

    /** Custom endpoints must not silently forward credentials or context to a redirect target. */
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
        val activeClient = if (provider.isCustomEndpoint) noRedirectClient else client
        var attempt = 0
        while (true) {
            attempt++
            try {
                activeClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (raw.length > MAX_RESPONSE_BYTES) {
                        throw AiException(
                            AiErrorKind.PARSE,
                            "The AI provider returned an oversized response."
                        )
                    }
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

                    val retryable = response.code == 429 || response.code >= 500
                    if (retryable && attempt == 1) {
                        delay(if (response.code == 429) 1_200L else 600L)
                        return@use
                    }
                    throw mapHttpError(response.code, raw)
                }
            } catch (e: AiException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw AiException(
                    AiErrorKind.TIMEOUT,
                    "The AI provider did not answer in time. Check the connection and try again.",
                    cause = e
                )
            } catch (e: IOException) {
                throw AiException(
                    AiErrorKind.NETWORK,
                    "Could not reach the AI provider. Check the network and the endpoint URL.",
                    cause = e
                )
            }
        }
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
