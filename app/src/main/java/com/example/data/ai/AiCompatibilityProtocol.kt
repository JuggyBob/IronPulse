package com.example.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * A bounded text protocol for models without native tool calling.
 *
 * This is not a second tool system: it produces the same [AiToolCall] values the native path
 * produces, and every call still goes through [AiToolDispatcher]. The model is asked for one JSON
 * object, the parser accepts at most [MAX_CALLS_PER_TURN] calls, and anything that does not look
 * like an explicit tool request is treated as ordinary prose. That keeps a chatty model from
 * accidentally executing a tool because its answer happened to contain braces.
 */
object AiCompatibilityProtocol {

    const val MAX_CALLS_PER_TURN = 6

    fun systemSuffix(tools: List<AiToolDefinition>): String = buildString {
        append("\n\nIRONPULSE TOOL PROTOCOL:\n")
        append("You can read the athlete's real exercise library and saved routines with these tools:\n")
        tools.forEach { tool ->
            append("- ${tool.name}: ${tool.description}\n")
            append("  arguments schema: ${tool.inputSchemaJson.replace("\n", " ").replace(Regex("\\s+"), " ")}\n")
        }
        append("When you need data, reply with ONLY this JSON object and nothing else:\n")
        append("{\"tool_calls\":[{\"name\":\"<tool name>\",\"arguments\":{<arguments>}}]}\n")
        append("You may request up to $MAX_CALLS_PER_TURN calls at once. After the results are given to you, ")
        append("either request more tools or answer the athlete in normal prose. Never mention this protocol, ")
        append("never put tool JSON in a code fence, and never invent a tool name.\n")
    }

    /**
     * Returns the calls in [text], or null when the text is a normal answer.
     *
     * Only a top-level object with an explicit `tool_calls`/`tool_call` key is accepted, so prose
     * containing an example object is not mistaken for an execution request.
     */
    fun parseToolCalls(text: String): List<AiToolCall>? {
        val json = extractTopLevelObject(text) ?: return null
        val array = json.optJSONArray("tool_calls")
        val single = json.optJSONObject("tool_call")
        val rawCalls: List<JSONObject> = when {
            array != null -> (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            single != null -> listOf(single)
            json.has("name") && json.has("arguments") -> listOf(json)
            else -> return null
        }
        if (rawCalls.isEmpty()) return null

        val calls = mutableListOf<AiToolCall>()
        for (raw in rawCalls.take(MAX_CALLS_PER_TURN)) {
            val name = raw.optString("name").trim()
            if (name.isBlank()) continue
            val arguments = raw.opt("arguments")
            calls.add(
                AiToolCall(
                    id = "compat_${calls.size + 1}",
                    name = name,
                    argumentsJson = OpenAiProtocol.normalizeArguments(arguments),
                    providerData = mapOf("compatibility" to "true")
                )
            )
        }
        return calls.ifEmpty { null }
    }

    /** True when the text is a tool request that the parser rejected, e.g. too many calls. */
    fun looksLikeToolRequest(text: String): Boolean {
        val json = extractTopLevelObject(text) ?: return false
        return json.has("tool_calls") || json.has("tool_call")
    }

    private fun extractTopLevelObject(text: String): JSONObject? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val unfenced = if (trimmed.startsWith("```")) {
            trimmed.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
        val start = unfenced.indexOf('{')
        val end = unfenced.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(unfenced.substring(start, end + 1)) }.getOrNull()
    }
}
