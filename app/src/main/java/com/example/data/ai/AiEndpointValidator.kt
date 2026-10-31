package com.example.data.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.URI

/**
 * Validates and normalizes provider endpoints.
 *
 * The app never disables certificate validation and never follows redirects for a user supplied
 * endpoint. A custom base URL is a credential destination, so the URL is parsed once, the chat and
 * model paths are derived from the same origin, and plain HTTP is only accepted for a loopback or
 * private-network host (with an explicit warning in the UI).
 */
object AiEndpointValidator {

    data class ValidatedEndpoint(
        val baseUrl: String,
        val chatCompletionsUrl: String,
        val modelsUrl: String,
        val isLocal: Boolean,
        val isHttps: Boolean
    )

    fun validateCustomBaseUrl(raw: String): Result<ValidatedEndpoint> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return Result.failure(
                AiException(AiErrorKind.CONFIGURATION, "Enter the endpoint base URL, for example https://host/v1.")
            )
        }
        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(
                AiException(AiErrorKind.CONFIGURATION, "The endpoint URL is not a valid http or https URL.")
            )
        if (parsed.username.isNotBlank() || parsed.password.isNotBlank()) {
            return Result.failure(
                AiException(
                    AiErrorKind.CONFIGURATION,
                    "Do not put credentials in the URL. Use the API key field instead."
                )
            )
        }
        if (parsed.fragment?.isNotBlank() == true) {
            return Result.failure(
                AiException(AiErrorKind.CONFIGURATION, "The endpoint URL must not contain a fragment.")
            )
        }
        val isHttps = parsed.isHttps
        val isLocal = isLocalHost(parsed.host)
        if (!isHttps && !isLocal) {
            return Result.failure(
                AiException(
                    AiErrorKind.CONFIGURATION,
                    "Plain HTTP is only allowed for a local or private-network endpoint. " +
                        "Use HTTPS for a remote host so prompts and credentials are encrypted in transit."
                )
            )
        }

        val base = normalizedBase(parsed)
            ?: return Result.failure(
                AiException(AiErrorKind.CONFIGURATION, "The endpoint URL has an unsupported path.")
            )
        val chat = base.newBuilder().addPathSegments("chat/completions").build()
        val models = base.newBuilder().addPathSegments("models").build()
        return Result.success(
            ValidatedEndpoint(
                baseUrl = base.toString().trimEnd('/'),
                chatCompletionsUrl = chat.toString(),
                modelsUrl = models.toString(),
                isLocal = isLocal,
                isHttps = isHttps
            )
        )
    }

    /**
     * Turns whatever the athlete typed into the API root.
     *
     * Accepted forms:
     *  - https://host                  -> https://host/v1
     *  - https://host/v1               -> https://host/v1
     *  - https://host/v1/              -> https://host/v1
     *  - https://host/v1/chat/completions -> https://host/v1
     *  - https://host/openai/v1        -> https://host/openai/v1
     */
    private fun normalizedBase(url: HttpUrl): HttpUrl? {
        val segments = url.pathSegments.filter { it.isNotBlank() }.toMutableList()
        if (segments.size >= 2 &&
            segments[segments.lastIndex - 1].equals("chat", ignoreCase = true) &&
            segments.last().equals("completions", ignoreCase = true)
        ) {
            segments.removeAt(segments.lastIndex)
            segments.removeAt(segments.lastIndex)
        } else if (segments.isNotEmpty() && segments.last().equals("models", ignoreCase = true)) {
            segments.removeAt(segments.lastIndex)
        }
        if (segments.isEmpty()) segments.add("v1")
        val builder = url.newBuilder().encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    /** True for loopback and RFC1918/link-local addresses. A hostname is never trusted by name alone. */
    fun isLocalHost(host: String): Boolean {
        val clean = host.trim().lowercase().removeSurrounding("[", "]")
        if (clean == "localhost" || clean == "::1" || clean == "0.0.0.0") return true
        val literal = clean.toByteArrayOrNull() ?: return false
        return literal.let { bytes ->
            val address = runCatching { InetAddress.getByAddress(bytes) }.getOrNull() ?: return@let false
            address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress
        }
    }

    private fun String.toByteArrayOrNull(): ByteArray? {
        // Only parse dotted IPv4 here. IPv6 literals are already covered by the loopback check above;
        // parsing arbitrary strings through InetAddress would perform DNS lookups.
        val parts = split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return byteArrayOf(
            octets[0].toByte(),
            octets[1].toByte(),
            octets[2].toByte(),
            octets[3].toByte()
        )
    }

    /** A stable identity for the consent record: origin + normalized path, never credentials. */
    fun endpointIdentity(raw: String): String =
        validateCustomBaseUrl(raw).map { it.baseUrl }.getOrElse { raw.trim() }

    /** Best-effort display host, for the privacy warning. */
    fun displayHost(raw: String): String =
        runCatching { URI(raw.trim()).host }.getOrNull().orEmpty().ifBlank { raw.trim() }
}
