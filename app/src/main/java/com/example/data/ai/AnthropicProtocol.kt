package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic's Messages API.
 *
 * Anthropic is not OpenAI with different field names: tool calls are content blocks (`tool_use`),
 * results are `tool_result` blocks inside a user message, and `max_tokens` is mandatory. Treating
 * its response as an OpenAI `tool_calls` array would lose the block ids and break the next turn.
 */
object AnthropicProtocol {

    const val API_VERSION = "2023-06-01"
    const val DEFAULT_MAX_TOKENS = 4096

    fun buildMessagesRequest(request: AiRequest, includeTools: Boolean): JSONObject = JSONObject().apply {
        put("model", request.model)
        put("max_tokens", request.generation.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
        put("temperature", request.generation.temperature)
        put("top_p", request.generation.topP)
        if (request.systemInstruction.isNotBlank()) put("system", request.systemInstruction)
        put("messages", serializeMessages(request.messages))
        if (includeTools && request.tools.isNotEmpty()) {
            put("tools", serializeTools(request.tools))
        }
    }

    fun serializeMessages(messages: List<AiMessage>): JSONArray {
        data class Pending(val role: String, val blocks: MutableList<JSONObject>)

        val pending = mutableListOf<Pending>()
        fun append(role: String, block: JSONObject) {
            val last = pending.lastOrNull()
            if (last != null && last.role == role) {
                last.blocks.add(block)
            } else {
                pending.add(Pending(role, mutableListOf(block)))
            }
        }

        for (message in messages) {
            when (message.role) {
                AiRole.SYSTEM -> Unit
                AiRole.USER -> append("user", textBlock(message.text))
                AiRole.ASSISTANT -> {
                    if (message.text.isNotBlank()) append("assistant", textBlock(message.text))
                    message.toolCalls.forEach { call ->
                        append(
                            "assistant",
                            JSONObject().apply {
                                put("type", "tool_use")
                                put("id", call.id)
                                put("name", call.name)
                                put("input", runCatching { JSONObject(call.argumentsJson) }
                                    .getOrElse { JSONObject() })
                            }
                        )
                    }
                    // An assistant turn that is only a tool call still needs a message entry.
                    if (message.text.isBlank() && message.toolCalls.isEmpty()) {
                        append("assistant", textBlock(""))
                    }
                }
                AiRole.TOOL -> {
                    message.toolResults.forEach { result ->
                        append(
                            "user",
                            JSONObject().apply {
                                put("type", "tool_result")
                                put("tool_use_id", result.callId)
                                put("content", result.contentJson)
                                if (result.isError) put("is_error", true)
                            }
                        )
                    }
                }
            }
        }

        return JSONArray().apply {
            pending.forEach { turn ->
                put(
                    JSONObject().apply {
                        put("role", turn.role)
                        put("content", JSONArray().apply { turn.blocks.forEach { put(it) } })
                    }
                )
            }
        }
    }

    private fun textBlock(text: String): JSONObject =
        JSONObject().put("type", "text").put("text", text)

    fun serializeTools(tools: List<AiToolDefinition>): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put(
                        "input_schema",
                        runCatching { JSONObject(tool.inputSchemaJson) }
                            .getOrElse { JSONObject().put("type", "object") }
                    )
                }
            )
        }
    }

    fun parseMessagesResponse(json: JSONObject): AiResponse {
        val content = json.optJSONArray("content")
            ?: throw AiException(AiErrorKind.PARSE, "The AI provider returned no content blocks.")
        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> {
                    val value = block.optString("text")
                    if (value.isNotBlank()) {
                        if (text.isNotEmpty()) text.append('\n')
                        text.append(value)
                    }
                }
                "tool_use" -> {
                    val name = block.optString("name").trim()
                    if (name.isBlank()) continue
                    val input = block.opt("input")
                    calls.add(
                        AiToolCall(
                            id = block.optString("id").ifBlank { "toolu_${i + 1}" },
                            name = name,
                            argumentsJson = OpenAiProtocol.normalizeArguments(input)
                        )
                    )
                }
            }
        }
        val rawFinish = json.optString("stop_reason", "")
        val finish = when (rawFinish) {
            "tool_use" -> AiFinishReason.TOOL_CALLS
            "end_turn", "stop_sequence" -> AiFinishReason.COMPLETE
            "max_tokens" -> AiFinishReason.LENGTH
            "refusal" -> AiFinishReason.CONTENT_FILTER
            else -> if (calls.isEmpty()) AiFinishReason.UNKNOWN else AiFinishReason.TOOL_CALLS
        }
        return AiResponse(
            text = text.toString(),
            toolCalls = calls,
            finishReason = finish,
            rawFinishReason = rawFinish,
            model = json.optString("model")
        )
    }

    fun parseModelList(json: JSONObject): List<AiModelDescriptor> {
        val data = json.optJSONArray("data") ?: return emptyList()
        val models = mutableListOf<AiModelDescriptor>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (id.isBlank()) continue
            models.add(
                AiModelDescriptor(
                    id = id,
                    displayName = item.optString("display_name").ifBlank { id },
                    capabilities = AiModelCapabilities(
                        nativeToolCalling = AiCapability.SUPPORTED,
                        contextWindowTokens = item.optInt("context_window", 0).takeIf { it > 0 },
                        maxOutputTokens = item.optInt("max_output_tokens", 0).takeIf { it > 0 }
                    ),
                    isLive = true
                )
            )
        }
        return models.sortedBy { it.id.lowercase() }
    }
}
