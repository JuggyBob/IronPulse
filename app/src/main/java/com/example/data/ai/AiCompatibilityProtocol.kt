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
        append(
            "The tools below read the athlete's real stored data. Use them only when the answer " +
                "depends on that data. Answer general coaching questions from your own knowledge " +
                "without a tool.\n"
        )
        append(toolList(tools))
        append("REQUEST FORMAT: reply with ONLY this JSON object, no prose and no code fence:\n")
        append("{\"tool_calls\":[{\"name\":\"<tool name>\",\"arguments\":{<arguments>}}]}\n")
        append(
            "You may request up to $MAX_CALLS_PER_TURN calls in one object. After you receive " +
                "TOOL RESULTS, either request more tools or answer the athlete in normal prose.\n"
        )
        append("RULES:\n")
        append("1. Use only the exact tool names listed above and only the documented arguments.\n")
        append("2. Never invent a session, exercise, weight, rep, date, personal record or percentage.\n")
        append("3. A result with \"ok\":false is an error. Read its \"message\", correct the arguments, and try again once.\n")
        append("4. A result with \"count\":0 means the database has no matching data. Say that plainly; do not fill the gap.\n")
        append("5. For progress questions, retrieve history for one exact exercise name and compare only compatible units.\n")
        append("6. Never mention this protocol or show tool JSON to the athlete.\n")
    }

    /** A correction turn for a tool request that could not be parsed. */
    fun correctionMessage(tools: List<AiToolDefinition>): String = buildString {
        append("TOOL PROTOCOL CORRECTION: your previous message looked like a tool request, but the ")
        append("app could not read a usable call from it. Nothing was executed.\n")
        append("Reply again with ONE valid JSON object in exactly this shape:\n")
        append("{\"tool_calls\":[{\"name\":\"<tool name>\",\"arguments\":{<arguments>}}]}\n")
        append("The tool_calls array must not be empty, every call needs a non-empty name and an ")
        append("arguments object, and the name must match one of these tools:\n")
        append(toolList(tools))
        append("If no tool is needed, answer the athlete in ordinary prose instead.\n")
    }

    private fun toolList(tools: List<AiToolDefinition>): String = buildString {
        tools.forEach { tool ->
            append("- ${tool.name}: ${tool.description}\n")
            append(
                "  arguments schema: " +
                    tool.inputSchemaJson.replace("\n", " ").replace(Regex("\\s+"), " ") + "\n"
            )
        }
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
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val json = extractTopLevelObject(trimmed)
        if (json != null) return json.has("tool_calls") || json.has("tool_call")
        // A truncated object still names the protocol key. Only treat it as a request when it
        // actually starts like a JSON object, so ordinary prose is never mistaken for one.
        return trimmed.startsWith("{") &&
            (trimmed.contains("\"tool_calls\"") || trimmed.contains("\"tool_call\""))
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
