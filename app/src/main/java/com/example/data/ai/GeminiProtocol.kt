package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Google's Gemini generateContent protocol.
 *
 * Gemini calls tools with `functionCall` parts and expects a `functionResponse` part whose name
 * matches the called function. It does not always send a call id, so the adapter synthesizes a
 * stable one for the orchestration loop while preserving any thought signature the API returned.
 */
object GeminiProtocol {

    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

    fun generateContentUrl(model: String, apiKey: String): String {
        val cleanModel = model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        return "$BASE_URL$cleanModel:generateContent?key=$apiKey"
    }

    fun modelsUrl(apiKey: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100"

    fun buildGenerateContentRequest(request: AiRequest, includeTools: Boolean): JSONObject = JSONObject().apply {
        put("contents", serializeContents(request.messages))
        if (request.systemInstruction.isNotBlank()) {
            put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", request.systemInstruction))
                )
            )
        }
        put(
            "generationConfig",
            JSONObject().apply {
                put("temperature", request.generation.temperature)
                put("topP", request.generation.topP)
                request.generation.maxOutputTokens?.let { put("maxOutputTokens", it) }
            }
        )
        if (includeTools && request.tools.isNotEmpty()) {
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", serializeTools(request.tools))))
        }
    }

    fun serializeContents(messages: List<AiMessage>): JSONArray {
        data class Turn(val role: String, val parts: MutableList<JSONObject>)

        val turns = mutableListOf<Turn>()
        fun append(role: String, part: JSONObject) {
            val last = turns.lastOrNull()
            if (last != null && last.role == role) {
                last.parts.add(part)
            } else {
                turns.add(Turn(role, mutableListOf(part)))
            }
        }

        for (message in messages) {
            when (message.role) {
                AiRole.SYSTEM -> Unit
                AiRole.USER -> append("user", JSONObject().put("text", message.text))
                AiRole.ASSISTANT -> {
                    if (message.text.isNotBlank()) {
                        append("model", JSONObject().put("text", message.text))
                    }
                    message.toolCalls.forEach { call ->
                        val part = JSONObject().put(
                            "functionCall",
                            JSONObject().apply {
                                put("name", call.name)
                                put("args", runCatching { JSONObject(call.argumentsJson) }
                                    .getOrElse { JSONObject() })
                            }
                        )
                        call.providerData["thoughtSignature"]?.let { part.put("thoughtSignature", it) }
                        append("model", part)
                    }
                    if (message.text.isBlank() && message.toolCalls.isEmpty()) {
                        append("model", JSONObject().put("text", ""))
                    }
                }
                AiRole.TOOL -> {
                    message.toolResults.forEach { result ->
                        append(
                            "user",
                            JSONObject().put(
                                "functionResponse",
                                JSONObject().apply {
                                    put("name", result.name)
                                    put(
                                        "response",
                                        runCatching { JSONObject(result.contentJson) }
                                            .getOrElse {
                                                JSONObject().put("result", result.contentJson)
                                            }
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }

        return JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject().apply {
                        put("role", turn.role)
                        put("parts", JSONArray().apply { turn.parts.forEach { put(it) } })
                    }
                )
            }
        }
    }

    fun serializeTools(tools: List<AiToolDefinition>): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put(
                        "parameters",
                        runCatching { JSONObject(tool.inputSchemaJson) }
                            .getOrElse { JSONObject().put("type", "object") }
                    )
                }
            )
        }
    }

    fun parseGenerateContentResponse(json: JSONObject): AiResponse {
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException(AiErrorKind.PARSE, "The AI provider returned no candidates.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException(AiErrorKind.PARSE, "The AI provider returned no content parts.")
        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            part.optString("text").takeIf { it.isNotBlank() }?.let {
                if (text.isNotEmpty()) text.append('\n')
                text.append(it)
            }
            val functionCall = part.optJSONObject("functionCall") ?: continue
            val name = functionCall.optString("name").trim()
            if (name.isBlank()) continue
            val providerData = mutableMapOf<String, String>()
            part.optString("thoughtSignature").takeIf { it.isNotBlank() }?.let {
                providerData["thoughtSignature"] = it
            }
            calls.add(
                AiToolCall(
                    id = "gemini_call_${i + 1}",
                    name = name,
                    argumentsJson = OpenAiProtocol.normalizeArguments(functionCall.opt("args")),
                    providerData = providerData
                )
            )
        }
        val rawFinish = candidate.optString("finishReason", "")
        val finish = when (rawFinish) {
            "STOP" -> if (calls.isEmpty()) AiFinishReason.COMPLETE else AiFinishReason.TOOL_CALLS
            "MAX_TOKENS" -> AiFinishReason.LENGTH
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                AiFinishReason.CONTENT_FILTER
            else -> if (calls.isEmpty()) AiFinishReason.UNKNOWN else AiFinishReason.TOOL_CALLS
        }
        return AiResponse(
            text = text.toString(),
            toolCalls = calls,
            finishReason = finish,
            rawFinishReason = rawFinish,
            model = json.optString("modelVersion")
        )
    }

    fun parseModelList(json: JSONObject): List<AiModelDescriptor> {
        val modelsArray = json.optJSONArray("models") ?: return emptyList()
        val parsed = mutableListOf<AiModelDescriptor>()
        for (i in 0 until modelsArray.length()) {
            val item = modelsArray.optJSONObject(i) ?: continue
            val name = item.optString("name").removePrefix("models/").trim()
            if (name.isBlank()) continue
            val methods = item.optJSONArray("supportedGenerationMethods")
            val canGenerate = methods == null ||
                (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            if (!canGenerate) continue
            val lower = name.lowercase()
            if (!lower.contains("gemini") && !lower.contains("gemma")) continue
            parsed.add(
                AiModelDescriptor(
                    id = name,
                    displayName = item.optString("displayName").ifBlank { name },
                    description = item.optString("description"),
                    capabilities = AiModelCapabilities(
                        nativeToolCalling = if (lower.contains("gemma")) {
                            // Gemma models have historically exposed generateContent without
                            // function calling. The adapter still tries native first and falls
                            // back, but the selector should not promise it.
                            AiCapability.UNKNOWN
                        } else {
                            AiCapability.SUPPORTED
                        }
                    ),
                    isLive = true
                )
            )
        }
        return parsed.sortedWith(
            compareByDescending<AiModelDescriptor> { it.id == DEFAULT_MODEL }
                .thenBy { it.displayName.lowercase() }
        )
    }

    fun curatedModels(): List<AiModelDescriptor> = listOf(
        AiModelDescriptor("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite (Default)", "Fast and cost-efficient."),
        AiModelDescriptor("gemini-3.5-flash", "Gemini 3.5 Flash", "High speed and strong accuracy."),
        AiModelDescriptor("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", "Advanced reasoning."),
        AiModelDescriptor("gemini-2.5-flash", "Gemini 2.5 Flash", "Fast multimodal model."),
        AiModelDescriptor("gemini-2.5-pro", "Gemini 2.5 Pro", "Deep reasoning."),
        AiModelDescriptor("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", "Low latency."),
        AiModelDescriptor("gemma-2-27b-it", "Gemma 2 27B IT", "Instruction-tuned open model."),
        AiModelDescriptor("gemma-2-9b-it", "Gemma 2 9B IT", "Instruction-tuned open model."),
        AiModelDescriptor("gemma-2-2b-it", "Gemma 2 2B IT", "Lightweight open model.")
    )
}
