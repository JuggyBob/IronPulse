package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenAI chat-completions protocol.
 *
 * OpenAI, OpenRouter, xAI and a custom compatible endpoint all speak a variant of this shape. The
 * differences that matter (base URL, auth header, provider headers, model metadata) stay in their
 * adapters; the message/tool conversion is shared here so a tool cannot work on one provider and
 * silently disappear on another.
 */
object OpenAiProtocol {

    fun buildChatRequest(request: AiRequest, includeTools: Boolean): JSONObject = JSONObject().apply {
        put("model", request.model)
        put(
            "messages",
            serializeMessages(
                request.messages,
                compatibility = request.toolMode == AiToolMode.COMPATIBILITY
            )
        )
        if (request.systemInstruction.isNotBlank()) {
            // The system instruction is normally already the first message. Putting it here as well
            // would duplicate it, so only add it when the caller passed messages without one.
            val hasSystem = request.messages.any { it.role == AiRole.SYSTEM }
            if (!hasSystem) {
                getJSONArray("messages").put(
                    JSONObject().put("role", "system").put("content", request.systemInstruction)
                )
            }
        }
        put("temperature", request.generation.temperature)
        put("top_p", request.generation.topP)
        request.generation.maxOutputTokens?.let { put("max_tokens", it) }
        if (includeTools && request.tools.isNotEmpty()) {
            put("tools", serializeTools(request.tools))
            put("tool_choice", "auto")
        }
    }

    fun serializeMessages(
        messages: List<AiMessage>,
        compatibility: Boolean = false
    ): JSONArray {
        val array = JSONArray()
        for (message in messages) {
            when (message.role) {
                AiRole.SYSTEM -> array.put(
                    JSONObject().put("role", "system").put("content", message.text)
                )
                AiRole.USER -> array.put(
                    JSONObject().put("role", "user").put("content", message.text)
                )
                AiRole.ASSISTANT -> {
                    val obj = JSONObject().put("role", "assistant")
                    if (compatibility) {
                        val text = message.text.ifBlank {
                            if (message.toolCalls.isEmpty()) "" else
                                JSONObject().put(
                                    "tool_calls",
                                    JSONArray().apply {
                                        message.toolCalls.forEach { call ->
                                            put(
                                                JSONObject().apply {
                                                    put("name", call.name)
                                                    put(
                                                        "arguments",
                                                        runCatching { JSONObject(call.argumentsJson) }
                                                            .getOrElse { JSONObject() }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                ).toString()
                        }
                        obj.put("content", text)
                    } else {
                        if (message.text.isBlank()) {
                            obj.put("content", JSONObject.NULL)
                        } else {
                            obj.put("content", message.text)
                        }
                        if (message.toolCalls.isNotEmpty()) {
                            obj.put("tool_calls", JSONArray().apply {
                                message.toolCalls.forEach { call ->
                                    put(
                                        JSONObject().apply {
                                            put("id", call.id)
                                            put("type", "function")
                                            put(
                                                "function",
                                                JSONObject().apply {
                                                    put("name", call.name)
                                                    put("arguments", call.argumentsJson)
                                                }
                                            )
                                        }
                                    )
                                }
                            })
                        }
                    }
                    array.put(obj)
                }
                AiRole.TOOL -> {
                    if (compatibility) {
                        if (message.toolResults.isNotEmpty()) {
                            array.put(
                                JSONObject().put(
                                    "role",
                                    "user"
                                ).put(
                                    "content",
                                    "TOOL RESULTS (JSON): " + JSONArray().apply {
                                        message.toolResults.forEach { result ->
                                            put(
                                                JSONObject().apply {
                                                    put("name", result.name)
                                                    put("call_id", result.callId)
                                                    put("is_error", result.isError)
                                                    put(
                                                        "result",
                                                        runCatching { JSONObject(result.contentJson) }
                                                            .getOrElse { result.contentJson }
                                                    )
                                                }
                                            )
                                        }
                                    }.toString()
                                )
                            )
                        }
                    } else {
                        message.toolResults.forEach { result ->
                            array.put(
                                JSONObject().apply {
                                    put("role", "tool")
                                    put("tool_call_id", result.callId)
                                    put("content", result.contentJson)
                                }
                            )
                        }
                        if (message.toolResults.isEmpty() && message.text.isNotBlank()) {
                            array.put(JSONObject().put("role", "tool").put("content", message.text))
                        }
                    }
                }
            }
        }
        return array
    }

    fun serializeTools(tools: List<AiToolDefinition>): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", runCatching { JSONObject(tool.inputSchemaJson) }
                                .getOrElse { JSONObject().put("type", "object") })
                        }
                    )
                }
            )
        }
    }

    fun parseChatResponse(json: JSONObject): AiResponse {
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: throw AiException(AiErrorKind.PARSE, "The AI provider returned no completion choices.")
        val message = choice.optJSONObject("message") ?: JSONObject()
        val text = extractContent(message)
        val calls = mutableListOf<AiToolCall>()

        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.optJSONObject(i) ?: continue
                val function = call.optJSONObject("function") ?: continue
                val name = function.optString("name").trim()
                if (name.isBlank()) continue
                val id = call.optString("id").ifBlank { "call_${i + 1}" }
                calls.add(
                    AiToolCall(
                        id = id,
                        name = name,
                        argumentsJson = normalizeArguments(function.opt("arguments")),
                        providerData = mapOf("provider" to AiProviderId.OPENAI.storageKey)
                    )
                )
            }
        }

        // The pre-2023 single function_call shape still appears on small compatible servers.
        val legacyCall = message.optJSONObject("function_call")
        if (calls.isEmpty() && legacyCall != null) {
            val name = legacyCall.optString("name").trim()
            if (name.isNotBlank()) {
                calls.add(
                    AiToolCall(
                        id = "call_legacy_1",
                        name = name,
                        argumentsJson = normalizeArguments(legacyCall.opt("arguments")),
                        providerData = mapOf("legacyFunctionCall" to "true")
                    )
                )
            }
        }

        val rawFinish = choice.optString("finish_reason", "")
        val finish = when (rawFinish.lowercase()) {
            "tool_calls", "function_call" -> AiFinishReason.TOOL_CALLS
            "stop" -> if (calls.isEmpty()) AiFinishReason.COMPLETE else AiFinishReason.TOOL_CALLS
            "length" -> AiFinishReason.LENGTH
            "content_filter" -> AiFinishReason.CONTENT_FILTER
            else -> if (calls.isEmpty()) AiFinishReason.UNKNOWN else AiFinishReason.TOOL_CALLS
        }
        return AiResponse(
            text = text,
            toolCalls = calls,
            finishReason = finish,
            rawFinishReason = rawFinish,
            model = json.optString("model")
        )
    }

    private fun extractContent(message: JSONObject): String {
        val content = message.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val text = part.optString("text")
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
            else -> ""
        }
    }

    fun normalizeArguments(raw: Any?): String = when (raw) {
        null, JSONObject.NULL -> "{}"
        is JSONObject -> raw.toString()
        is String -> raw.trim().ifBlank { "{}" }
        else -> raw.toString()
    }

    /** Parses a model catalog in the OpenAI `{data:[{id:...}]}` shape. */
    fun parseModelList(json: JSONObject, provider: AiProviderId): List<AiModelDescriptor> {
        val data = json.optJSONArray("data") ?: return emptyList()
        val models = mutableListOf<AiModelDescriptor>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            val supported = item.optJSONArray("supported_parameters")
            val nativeTools = when {
                supported == null -> AiCapability.UNKNOWN
                (0 until supported.length()).any { supported.optString(it).equals("tools", true) } ->
                    AiCapability.SUPPORTED
                else -> AiCapability.UNSUPPORTED
            }
            models.add(
                AiModelDescriptor(
                    id = id,
                    displayName = item.optString("name").ifBlank { id },
                    description = item.optString("description"),
                    capabilities = AiModelCapabilities(
                        nativeToolCalling = nativeTools,
                        contextWindowTokens = item.optInt("context_length", 0).takeIf { it > 0 }
                    ),
                    isLive = true
                )
            )
        }
        return models.sortedBy { it.id.lowercase() }
    }
}
