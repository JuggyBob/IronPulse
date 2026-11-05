package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Runs one bounded conversation, including any tool calls the model asks for.
 *
 * The loop is intentionally small and provider independent:
 *  1. ask the provider (native tools, or the text protocol when the model cannot use them),
 *  2. validate and execute every requested call through [AiToolDispatcher],
 *  3. append the assistant turn and the tool results,
 *  4. repeat until the model answers or a hard limit is reached.
 *
 * Hard limits are part of the contract, not an optimisation: a model that keeps calling the same
 * search must not be able to keep the app busy. Identical calls are answered from a per-conversation
 * cache, the total number of executed calls is capped, and a final tool-free request forces an
 * answer when the round budget is exhausted.
 */
class AiConversationEngine(
    private val dispatcher: AiToolDispatcher,
    private val providerResolver: (AiProviderId) -> AiProvider = { AiProviderRegistry.provider(it) },
    private val maxRounds: Int = 4,
    private val maxToolCalls: Int = 12,
    private val requestTimeoutMillis: Long = 90_000L,
    private val maxRetries: Int = 1,
    private val maxCorrections: Int = 2
) {

    suspend fun run(
        configuration: AiProviderConfiguration,
        systemInstruction: String,
        messages: List<AiMessage>,
        enableTools: Boolean = true,
        generation: AiGenerationConfig = AiGenerationConfig(),
        accessPolicy: AiAccessPolicy = AiAccessPolicy.disabled,
        /**
         * Read before every provider request and every tool dispatch. This is what makes revoking
         * the Settings permission take effect inside an already-running agent loop.
         */
        accessPolicyProvider: (suspend () -> AiAccessPolicy)? = null
    ): AiConversationResult {
        validateConfiguration(configuration)
        val provider = providerResolver(configuration.provider)
        val conversation = messages.toMutableList()
        if (conversation.isEmpty()) {
            throw AiException(AiErrorKind.INVALID_REQUEST, "There is no message to send.")
        }

        var compatibility = configuration.toolMode == AiToolMode.COMPATIBILITY
        var usedCompatibility = compatibility
        var rounds = 0
        var executedCalls = 0
        var corrections = 0
        val cache = mutableMapOf<String, AiToolResult>()
        suspend fun currentAccess(): AiAccessPolicy =
            accessPolicyProvider?.invoke() ?: accessPolicy

        fun effectiveSystem(access: AiAccessPolicy): String {
            if (!enableTools || !compatibility) return systemInstruction
            return systemInstruction +
                AiCompatibilityProtocol.systemSuffix(dispatcher.definitions(access))
        }

        while (rounds < maxRounds) {
            rounds++
            val nativeTools = enableTools && !compatibility
            val requestAccess = currentAccess()
            val response = try {
                callProvider(
                    provider = provider,
                    request = buildRequest(
                        configuration = configuration,
                        systemInstruction = effectiveSystem(requestAccess),
                        messages = conversation,
                        nativeTools = nativeTools,
                        generation = generation,
                        accessPolicy = requestAccess
                    )
                )
            } catch (error: AiException) {
                // A provider that rejects the tools field outright can still answer in the text
                // protocol. Only AUTO falls back: an explicit NATIVE choice reports the error.
                if (nativeTools &&
                    configuration.toolMode == AiToolMode.AUTO &&
                    AiHttp.isToolUnsupportedError(error)
                ) {
                    compatibility = true
                    usedCompatibility = true
                    rounds--
                    continue
                }
                throw error
            }

            val calls = when {
                !enableTools -> emptyList()
                response.toolCalls.isNotEmpty() -> response.toolCalls
                compatibility -> AiCompatibilityProtocol.parseToolCalls(response.text).orEmpty()
                configuration.toolMode == AiToolMode.AUTO ->
                    AiCompatibilityProtocol.parseToolCalls(response.text).orEmpty()
                else -> emptyList()
            }

            if (calls.isEmpty()) {
                val text = response.text.trim()
                // A response that clearly tried to use the text protocol but produced no usable
                // call is a correctable mistake, not an answer. Send the model its own attempt plus
                // the exact shape and schemas, then let it try again before failing the athlete.
                if (enableTools && AiCompatibilityProtocol.looksLikeToolRequest(text)) {
                    if (corrections >= maxCorrections) {
                        throw AiException(
                            AiErrorKind.PARSE,
                            "The AI provider kept sending a tool request the app could not read. " +
                                "No data was changed. Please rephrase the question or try again."
                        )
                    }
                    corrections++
                    conversation.add(AiMessage(AiRole.ASSISTANT, text))
                    conversation.add(
                        AiMessage(
                            AiRole.USER,
                            AiCompatibilityProtocol.correctionMessage(
                                dispatcher.definitions(currentAccess())
                            )
                        )
                    )
                    continue
                }
                if (text.isBlank()) {
                    throw AiException(
                        AiErrorKind.PARSE,
                        "The AI provider returned an empty answer. Please try again."
                    )
                }
                return AiConversationResult(
                    text = text,
                    rounds = rounds,
                    toolCallsExecuted = executedCalls,
                    usedCompatibilityFallback = usedCompatibility
                )
            }

            // Keep exactly what the model emitted in the transcript. In compatibility mode this is
            // the tool envelope, and the serializer needs it verbatim so the next request mirrors
            // the model's own format. It is never returned as the visible answer.
            val assistantText = response.text
            conversation.add(
                AiMessage(
                    role = AiRole.ASSISTANT,
                    text = assistantText,
                    toolCalls = calls
                )
            )
            if (response.toolCalls.isEmpty()) usedCompatibility = true

            val results = mutableListOf<AiToolResult>()
            for (call in calls) {
                if (executedCalls >= maxToolCalls) {
                    results.add(budgetResult(call))
                    continue
                }
                val key = call.name + "|" + canonicalArguments(call.argumentsJson)
                val cached = cache[key]
                if (cached != null) {
                    results.add(cached.copy(callId = call.id))
                    continue
                }
                val result = dispatcher.dispatch(call, currentAccess())
                cache[key] = result
                executedCalls++
                results.add(result)
            }
            conversation.add(AiMessage(role = AiRole.TOOL, toolResults = results))

            // A routine change is a proposal, never an automatic write. Stop here so the app can
            // render the approval card; the model resumes only after the athlete decides.
            var modification: RoutineModificationProposal? = null
            var newRoutine: NewRoutineProposal? = null
            for (result in results) {
                if (result.isError) continue
                val json = runCatching { JSONObject(result.contentJson) }.getOrNull() ?: continue
                val proposalJson = json.optJSONObject("proposal") ?: continue
                if (proposalJson.optString("kind") == NewRoutineProposal.KIND) {
                    newRoutine = NewRoutineProposal.fromJson(proposalJson) ?: continue
                } else {
                    modification = RoutineModificationProposal.fromJson(proposalJson) ?: continue
                }
                break
            }
            if (modification != null) {
                return AiConversationResult(
                    text = response.text.trim().ifBlank {
                        "I prepared a routine change for your approval."
                    },
                    rounds = rounds,
                    toolCallsExecuted = executedCalls,
                    usedCompatibilityFallback = usedCompatibility,
                    routineProposal = modification
                )
            }
            if (newRoutine != null) {
                return AiConversationResult(
                    text = response.text.trim().ifBlank {
                        "I prepared a new routine for your approval."
                    },
                    rounds = rounds,
                    toolCallsExecuted = executedCalls,
                    usedCompatibilityFallback = usedCompatibility,
                    newRoutineProposal = newRoutine
                )
            }
        }

        // The model kept asking for tools. Ask once more with tools removed so the athlete gets an
        // answer built from what was already gathered instead of an error after a long wait.
        rounds++
        val finalResponse = callProvider(
            provider = provider,
            request = buildRequest(
                configuration = configuration,
                systemInstruction = systemInstruction +
                    "\n\nYou have reached the tool limit. Answer the athlete now using only the " +
                    "information you already have. Do not request any more tools.",
                messages = conversation,
                nativeTools = false,
                generation = generation,
                accessPolicy = currentAccess()
            )
        )
        val finalText = finalResponse.text.trim()
        val safeText = if (
            compatibility && AiCompatibilityProtocol.looksLikeToolRequest(finalText)
        ) {
            "I gathered the available data but reached the tool limit before I could finish the " +
                "answer. Please ask a narrower question."
        } else {
            finalText
        }
        if (safeText.isBlank()) {
            throw AiException(
                AiErrorKind.TOOL_EXECUTION,
                "The AI kept requesting tools and did not produce an answer. Please ask a more " +
                    "specific question."
            )
        }
        return AiConversationResult(
            text = safeText,
            rounds = rounds,
            toolCallsExecuted = executedCalls,
            usedCompatibilityFallback = usedCompatibility
        )
    }

    private fun buildRequest(
        configuration: AiProviderConfiguration,
        systemInstruction: String,
        messages: List<AiMessage>,
        nativeTools: Boolean,
        generation: AiGenerationConfig,
        accessPolicy: AiAccessPolicy
    ): AiRequest = AiRequest(
        provider = configuration.provider,
        model = configuration.model,
        apiKey = configuration.apiKey,
        baseUrl = configuration.baseUrl,
        systemInstruction = systemInstruction,
        messages = messages,
        tools = if (nativeTools) dispatcher.definitions(accessPolicy) else emptyList(),
        toolMode = if (nativeTools) AiToolMode.NATIVE else AiToolMode.COMPATIBILITY,
        generation = generation
    )

    private suspend fun callProvider(provider: AiProvider, request: AiRequest): AiResponse {
        var last: AiException? = null
        val attempts = maxRetries + 1
        for (attempt in 0 until attempts) {
            try {
                return withTimeout(requestTimeoutMillis) {
                    provider.generate(request).getOrThrow()
                }
            } catch (error: TimeoutCancellationException) {
                last = AiException(
                    AiErrorKind.TIMEOUT,
                    "The AI provider did not answer within ${requestTimeoutMillis / 1000} seconds."
                )
                if (attempt < attempts - 1) delay(400L) else throw last
            } catch (error: CancellationException) {
                // The athlete left the screen or cancelled the request: never turn that into a
                // retry or a visible provider error.
                throw error
            } catch (error: AiException) {
                last = error
                if (error.kind.isRetryable() && attempt < attempts - 1) {
                    delay(if (error.kind == AiErrorKind.RATE_LIMIT) 1_500L else 500L)
                } else {
                    throw error
                }
            } catch (error: Exception) {
                val normalized = error.toAiException()
                last = normalized
                if (normalized.kind.isRetryable() && attempt < attempts - 1) {
                    delay(500L)
                } else {
                    throw normalized
                }
            }
        }
        throw last ?: AiException(AiErrorKind.UNKNOWN, "The AI request failed.")
    }

    private fun validateConfiguration(configuration: AiProviderConfiguration) {
        if (configuration.apiKey.isBlank() && !configuration.provider.isCustomEndpoint) {
            throw AiException(
                AiErrorKind.CONFIGURATION,
                "No API key is saved for ${configuration.provider.displayName}. " +
                    "Add one in Profile & Settings."
            )
        }
        if (configuration.model.isBlank()) {
            throw AiException(
                AiErrorKind.CONFIGURATION,
                "No model is selected for ${configuration.provider.displayName}. " +
                    "Choose one in Profile & Settings."
            )
        }
        if (configuration.provider.isCustomEndpoint && configuration.baseUrl.isBlank()) {
            throw AiException(
                AiErrorKind.CONFIGURATION,
                "No endpoint URL is saved for the custom provider."
            )
        }
    }

    private fun budgetResult(call: AiToolCall): AiToolResult = AiToolResult(
        callId = call.id,
        name = call.name,
        contentJson = JSONObject().apply {
            put("ok", false)
            put("error", "tool_budget_exhausted")
            put("message", "The tool call limit for this answer was reached. Answer with what you know.")
        }.toString(),
        isError = true
    )

    /** Stable dedupe key: key order must not make two identical calls look different. */
    private fun canonicalArguments(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "{}"
        return runCatching {
            val obj = JSONObject(trimmed)
            val sorted = JSONObject()
            obj.keys().asSequence().sorted().forEach { key -> sorted.put(key, obj.get(key)) }
            sorted.toString()
        }.getOrDefault(trimmed)
    }
}

private fun AiErrorKind.isRetryable(): Boolean = when (this) {
    AiErrorKind.NETWORK, AiErrorKind.TIMEOUT, AiErrorKind.SERVER, AiErrorKind.RATE_LIMIT -> true
    else -> false
}
