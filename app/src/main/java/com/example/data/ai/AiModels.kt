package com.example.data.ai

/**
 * The providers IronPulse can talk to.
 *
 * The storage key is deliberately stable: it is written to DataStore, so renaming an enum constant
 * must never change the key an athlete's configuration was saved under.
 */
enum class AiProviderId(val storageKey: String, val displayName: String) {
    GOOGLE("google", "Google"),
    OPENAI("openai", "OpenAI"),
    ANTHROPIC("anthropic", "Anthropic"),
    OPENROUTER("openrouter", "OpenRouter"),
    XAI("xai", "xAI"),
    CUSTOM_OPENAI("custom_openai", "OpenAI-compatible endpoint");

    val isCustomEndpoint: Boolean get() = this == CUSTOM_OPENAI

    companion object {
        fun fromStorage(value: String?): AiProviderId =
            entries.firstOrNull { it.storageKey.equals(value?.trim(), ignoreCase = true) }
                ?: GOOGLE
    }
}

/** Three-state capability, because "the catalog did not say" is not "unsupported". */
enum class AiCapability {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN
}

/** What a model is known to support. Metadata can be stale; adapters still handle real errors. */
data class AiModelCapabilities(
    val nativeToolCalling: AiCapability = AiCapability.UNKNOWN,
    val streaming: AiCapability = AiCapability.UNKNOWN,
    val imageInput: AiCapability = AiCapability.UNKNOWN,
    val structuredOutput: AiCapability = AiCapability.UNKNOWN,
    val contextWindowTokens: Int? = null,
    val maxOutputTokens: Int? = null
)

/** A model the user can select. [isLive] distinguishes a fetched catalog from a known fallback. */
data class AiModelDescriptor(
    val id: String,
    val displayName: String = id,
    val description: String = "",
    val capabilities: AiModelCapabilities = AiModelCapabilities(),
    val isLive: Boolean = false
)

/** How tool calls should be produced for a provider/model. */
enum class AiToolMode {
    /** Try native tool calling, then fall back to the constrained text format on a clear failure. */
    AUTO,

    /** Only the provider's structured tool protocol. */
    NATIVE,

    /** Only the constrained text format. Useful for endpoints with a partial OpenAI surface. */
    COMPATIBILITY;

    companion object {
        fun fromStorage(value: String?): AiToolMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: AUTO
    }
}

/** Who a message came from. TOOL carries one or more tool results back to the model. */
enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * One normalized tool call.
 *
 * [argumentsJson] is kept as text on purpose: it is the exact object the model produced, and each
 * adapter needs to decide whether to re-serialize it or pass it through. Validation happens before
 * the handler sees it.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
    /** Provider-specific correlation data that must be echoed back (e.g. Anthropic block order). */
    val providerData: Map<String, String> = emptyMap()
)

/** The result of one call. [contentJson] is always a JSON object, never a raw exception. */
data class AiToolResult(
    val callId: String,
    val name: String,
    val contentJson: String,
    val isError: Boolean = false
)

/**
 * A normalized message.
 *
 * Assistant messages may carry [toolCalls]; TOOL messages carry [toolResults]. Keeping both on one
 * type lets a provider adapter serialize the exact conversational order without a second model.
 */
data class AiMessage(
    val role: AiRole,
    val text: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val toolResults: List<AiToolResult> = emptyList()
)

/** A tool definition in the application's own schema language (JSON Schema object). */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val permission: AiToolPermission = AiToolPermission.READ_ONLY,
    val maxResultBytes: Int = DEFAULT_MAX_RESULT_BYTES,
    val domain: AiToolDomain = AiToolDomain.CATALOG
) {
    companion object {
        const val DEFAULT_MAX_RESULT_BYTES = 16 * 1024
    }
}

/** Tools start read-only. A future write tool must declare a stricter permission explicitly. */
enum class AiToolPermission {
    READ_ONLY
}

/** Generation parameters shared by every adapter. Adapters may omit values a provider rejects. */
data class AiGenerationConfig(
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val maxOutputTokens: Int? = null
)

/** Everything needed to make one request, independent of provider protocol. */
data class AiRequest(
    val provider: AiProviderId,
    val model: String,
    val apiKey: String,
    val baseUrl: String = "",
    val systemInstruction: String = "",
    val messages: List<AiMessage>,
    val tools: List<AiToolDefinition> = emptyList(),
    val toolMode: AiToolMode = AiToolMode.AUTO,
    val generation: AiGenerationConfig = AiGenerationConfig()
)

/** Why a request stopped, normalized where possible. */
enum class AiFinishReason {
    COMPLETE,
    TOOL_CALLS,
    LENGTH,
    CONTENT_FILTER,
    ERROR,
    UNKNOWN
}

/** A provider response. [toolCalls] is empty when the model produced a final answer. */
data class AiResponse(
    val text: String,
    val toolCalls: List<AiToolCall> = emptyList(),
    val finishReason: AiFinishReason = AiFinishReason.UNKNOWN,
    val rawFinishReason: String = "",
    val model: String = ""
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

/** Error categories the UI can translate without parsing provider strings. */
enum class AiErrorKind {
    CONFIGURATION,
    AUTHENTICATION,
    PERMISSION,
    RATE_LIMIT,
    INVALID_REQUEST,
    UNSUPPORTED_CAPABILITY,
    MODEL_NOT_FOUND,
    NETWORK,
    TIMEOUT,
    CANCELLED,
    SERVER,
    PARSE,
    TOOL_EXECUTION,
    CONSENT_REQUIRED,
    UNKNOWN
}

/**
 * A sanitized provider error.
 *
 * The message is safe to show to the athlete. Raw response bodies are never included because they
 * can contain request echoes, account identifiers, or other users' data on shared gateways.
 */
class AiException(
    val kind: AiErrorKind,
    override val message: String,
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/** The final state of a bounded tool loop. */
data class AiConversationResult(
    val text: String,
    val rounds: Int,
    val toolCallsExecuted: Int,
    val usedCompatibilityFallback: Boolean,
    /** Present when the model asked for a routine change that needs explicit approval. */
    val routineProposal: RoutineModificationProposal? = null
)
