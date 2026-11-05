package com.example

import com.example.data.ai.AiCapability
import com.example.data.ai.AiCompatibilityProtocol
import com.example.data.ai.AiFinishReason
import com.example.data.ai.AiGenerationConfig
import com.example.data.ai.AiMessage
import com.example.data.ai.AiProviderId
import com.example.data.ai.AiRequest
import com.example.data.ai.AiRole
import com.example.data.ai.AiToolCall
import com.example.data.ai.AiToolCatalog
import com.example.data.ai.AiToolMode
import com.example.data.ai.AiToolResult
import com.example.data.ai.AnthropicProtocol
import com.example.data.ai.GeminiProtocol
import com.example.data.ai.OpenAiProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Provider protocol conversion, without a network.
 *
 * These are mocked adapter tests: they prove the request IronPulse builds and the response it can
 * read, not that a live provider accepts them. Live verification is a separate, credentialed step.
 * The three protocols are deliberately different shapes, so a tool that works on one is not assumed
 * to work on another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiProtocolConversionTest {

    private fun searchCall(id: String = "call_1") = AiToolCall(
        id = id,
        name = AiToolCatalog.SEARCH_EXERCISES,
        argumentsJson = """{"query":"bench press","limit":4}"""
    )

    private fun request(
        provider: AiProviderId,
        messages: List<AiMessage>,
        maxOutputTokens: Int? = 123
    ) = AiRequest(
        provider = provider,
        model = "test-model",
        apiKey = "test-key",
        systemInstruction = "You are IronPulse.",
        messages = messages,
        tools = AiToolCatalog.definitions,
        toolMode = AiToolMode.NATIVE,
        generation = AiGenerationConfig(temperature = 0.2, topP = 0.9, maxOutputTokens = maxOutputTokens)
    )

    // ---------------------------------------------------------------- OpenAI

    @Test
    fun `OpenAI request carries system, tool call and tool result in its own shape`() {
        val body = OpenAiProtocol.buildChatRequest(
            request(
                AiProviderId.OPENAI,
                listOf(
                    AiMessage(AiRole.SYSTEM, "You are IronPulse."),
                    AiMessage(AiRole.USER, "Find a bench press"),
                    AiMessage(AiRole.ASSISTANT, "Checking", toolCalls = listOf(searchCall())),
                    AiMessage(
                        AiRole.TOOL,
                        toolResults = listOf(
                            AiToolResult("call_1", AiToolCatalog.SEARCH_EXERCISES, """{"ok":true}""")
                        )
                    )
                )
            ),
            includeTools = true
        )

        assertEquals("test-model", body.getString("model"))
        assertEquals(0.2, body.getDouble("temperature"), 0.0001)
        assertEquals(0.9, body.getDouble("top_p"), 0.0001)
        assertEquals(123, body.getInt("max_tokens"))

        val messages = body.getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("You are IronPulse.", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("Find a bench press", messages.getJSONObject(1).getString("content"))

        val assistant = messages.getJSONObject(2)
        assertEquals("assistant", assistant.getString("role"))
        assertEquals("Checking", assistant.getString("content"))
        val toolCall = assistant.getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("call_1", toolCall.getString("id"))
        assertEquals("function", toolCall.getString("type"))
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, toolCall.getJSONObject("function").getString("name"))
        assertEquals(
            """{"query":"bench press","limit":4}""",
            toolCall.getJSONObject("function").getString("arguments")
        )

        val tool = messages.getJSONObject(3)
        assertEquals("tool", tool.getString("role"))
        assertEquals("call_1", tool.getString("tool_call_id"))
        assertEquals("""{"ok":true}""", tool.getString("content"))

        val toolDefinition = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", toolDefinition.getString("type"))
        assertEquals(
            AiToolCatalog.SEARCH_EXERCISES,
            toolDefinition.getJSONObject("function").getString("name")
        )
        assertEquals(
            "object",
            toolDefinition.getJSONObject("function").getJSONObject("parameters").getString("type")
        )
        assertEquals("auto", body.getString("tool_choice"))
    }

    @Test
    fun `OpenAI omits tools when the caller asked for a plain answer`() {
        val body = OpenAiProtocol.buildChatRequest(
            request(AiProviderId.OPENAI, listOf(AiMessage(AiRole.USER, "hello"))),
            includeTools = false
        )
        assertFalse(body.has("tools"))
        assertFalse(body.has("tool_choice"))
    }

    @Test
    fun `OpenAI parses multiple calls and concatenates text content parts`() {
        val response = OpenAiProtocol.parseChatResponse(
            JSONObject(
                """
                {
                  "model": "test-model",
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": [
                        {"type": "text", "text": "Looking"},
                        {"type": "text", "text": "it up"}
                      ],
                      "tool_calls": [
                        {"id": "call_a", "type": "function",
                         "function": {"name": "search_exercises", "arguments": "{\"query\":\"bench\"}"}},
                        {"id": "call_b", "type": "function",
                         "function": {"name": "list_routines", "arguments": {"limit": 3}}}
                      ]
                    }
                  }]
                }
                """.trimIndent()
            )
        )

        assertEquals("Looking\nit up", response.text)
        assertEquals(AiFinishReason.TOOL_CALLS, response.finishReason)
        assertEquals(2, response.toolCalls.size)
        assertEquals("call_a", response.toolCalls[0].id)
        assertEquals("""{"query":"bench"}""", response.toolCalls[0].argumentsJson)
        assertEquals("call_b", response.toolCalls[1].id)
        assertEquals(3, JSONObject(response.toolCalls[1].argumentsJson).getInt("limit"))
        assertEquals("test-model", response.model)
    }

    @Test
    fun `OpenAI still reads the legacy single function_call shape`() {
        val response = OpenAiProtocol.parseChatResponse(
            JSONObject(
                """
                {
                  "choices": [{
                    "finish_reason": "function_call",
                    "message": {
                      "content": null,
                      "function_call": {"name": "get_routine", "arguments": "{\"routineId\":2}"}
                    }
                  }]
                }
                """.trimIndent()
            )
        )
        assertEquals(1, response.toolCalls.size)
        assertEquals("call_legacy_1", response.toolCalls[0].id)
        assertEquals("get_routine", response.toolCalls[0].name)
        assertEquals(2, JSONObject(response.toolCalls[0].argumentsJson).getInt("routineId"))
        assertEquals(AiFinishReason.TOOL_CALLS, response.finishReason)
    }

    @Test
    fun `OpenAI normalizes empty and structured arguments instead of throwing`() {
        assertEquals("{}", OpenAiProtocol.normalizeArguments(null))
        assertEquals("{}", OpenAiProtocol.normalizeArguments(JSONObject.NULL))
        assertEquals("{}", OpenAiProtocol.normalizeArguments("   "))
        assertEquals("""{"a":1}""", OpenAiProtocol.normalizeArguments(JSONObject().put("a", 1)))
        // Invalid JSON stays as text so the dispatcher can reject it with a readable error.
        assertEquals("{not json", OpenAiProtocol.normalizeArguments("{not json"))
    }

    @Test
    fun `OpenAI model catalog reports tool capability from supported parameters`() {
        val models = OpenAiProtocol.parseModelList(
            JSONObject(
                """
                {"data": [
                  {"id": "z-model", "name": "Zed", "supported_parameters": ["tools"]},
                  {"id": "a-model", "supported_parameters": ["temperature"]},
                  {"id": "b-model"}
                ]}
                """.trimIndent()
            ),
            AiProviderId.OPENAI
        )
        assertEquals(listOf("a-model", "b-model", "z-model"), models.map { it.id })
        assertEquals(AiCapability.UNSUPPORTED, models[0].capabilities.nativeToolCalling)
        assertEquals(AiCapability.UNKNOWN, models[1].capabilities.nativeToolCalling)
        assertEquals(AiCapability.SUPPORTED, models[2].capabilities.nativeToolCalling)
        assertTrue(models.all { it.isLive })
        assertEquals("Zed", models[2].displayName)
    }

    // ------------------------------------------------------------- Anthropic

    @Test
    fun `Anthropic keeps the system prompt outside the message list and uses content blocks`() {
        val body = AnthropicProtocol.buildMessagesRequest(
            request(
                AiProviderId.ANTHROPIC,
                listOf(
                    AiMessage(AiRole.SYSTEM, "You are IronPulse."),
                    AiMessage(AiRole.USER, "Find a bench press"),
                    AiMessage(AiRole.ASSISTANT, toolCalls = listOf(searchCall())),
                    AiMessage(
                        AiRole.TOOL,
                        toolResults = listOf(
                            AiToolResult("call_1", AiToolCatalog.SEARCH_EXERCISES, """{"ok":true}""")
                        )
                    )
                ),
                maxOutputTokens = null
            ),
            includeTools = true
        )

        assertEquals("test-model", body.getString("model"))
        assertEquals(AnthropicProtocol.DEFAULT_MAX_TOKENS, body.getInt("max_tokens"))
        assertEquals("You are IronPulse.", body.getString("system"))

        val messages = body.getJSONArray("messages")
        assertEquals(3, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals(
            "Find a bench press",
            messages.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text")
        )

        val assistant = messages.getJSONObject(1)
        assertEquals("assistant", assistant.getString("role"))
        val toolUse = assistant.getJSONArray("content").getJSONObject(0)
        assertEquals("tool_use", toolUse.getString("type"))
        assertEquals("call_1", toolUse.getString("id"))
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, toolUse.getString("name"))
        assertEquals("bench press", toolUse.getJSONObject("input").getString("query"))

        val toolResult = messages.getJSONObject(2)
        assertEquals("user", toolResult.getString("role"))
        val block = toolResult.getJSONArray("content").getJSONObject(0)
        assertEquals("tool_result", block.getString("type"))
        assertEquals("call_1", block.getString("tool_use_id"))
        assertEquals("""{"ok":true}""", block.getString("content"))

        val definition = body.getJSONArray("tools").getJSONObject(0)
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, definition.getString("name"))
        assertEquals("object", definition.getJSONObject("input_schema").getString("type"))
    }

    @Test
    fun `Anthropic merges consecutive tool results into one user turn`() {
        val messages = AnthropicProtocol.serializeMessages(
            listOf(
                AiMessage(AiRole.ASSISTANT, toolCalls = listOf(searchCall("call_1"), searchCall("call_2"))),
                AiMessage(
                    AiRole.TOOL,
                    toolResults = listOf(
                        AiToolResult("call_1", AiToolCatalog.SEARCH_EXERCISES, "{}"),
                        AiToolResult("call_2", AiToolCatalog.SEARCH_EXERCISES, "{}")
                    )
                )
            )
        )
        assertEquals(2, messages.length())
        assertEquals("assistant", messages.getJSONObject(0).getString("role"))
        assertEquals(2, messages.getJSONObject(0).getJSONArray("content").length())
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals(2, messages.getJSONObject(1).getJSONArray("content").length())
    }

    @Test
    fun `Anthropic parses text and tool_use blocks with their ids`() {
        val response = AnthropicProtocol.parseMessagesResponse(
            JSONObject(
                """
                {
                  "model": "claude-test",
                  "stop_reason": "tool_use",
                  "content": [
                    {"type": "text", "text": "Let me check"},
                    {"type": "tool_use", "id": "toolu_1", "name": "get_exercise_details",
                     "input": {"exerciseId": 7}}
                  ]
                }
                """.trimIndent()
            )
        )
        assertEquals("Let me check", response.text)
        assertEquals(AiFinishReason.TOOL_CALLS, response.finishReason)
        assertEquals(1, response.toolCalls.size)
        assertEquals("toolu_1", response.toolCalls[0].id)
        assertEquals(7, JSONObject(response.toolCalls[0].argumentsJson).getInt("exerciseId"))
    }

    @Test
    fun `Anthropic maps a plain end_turn to a complete answer`() {
        val response = AnthropicProtocol.parseMessagesResponse(
            JSONObject(
                """{"stop_reason":"end_turn","content":[{"type":"text","text":"Done"}]}"""
            )
        )
        assertEquals("Done", response.text)
        assertEquals(AiFinishReason.COMPLETE, response.finishReason)
        assertTrue(response.toolCalls.isEmpty())
    }

    @Test
    fun `Anthropic model catalog exposes context limits and native tools`() {
        val models = AnthropicProtocol.parseModelList(
            JSONObject(
                """
                {"data": [
                  {"id": "claude-x", "display_name": "Claude X",
                   "context_window": 200000, "max_output_tokens": 8192}
                ]}
                """.trimIndent()
            )
        )
        assertEquals(1, models.size)
        assertEquals("Claude X", models[0].displayName)
        assertEquals(200000, models[0].capabilities.contextWindowTokens)
        assertEquals(8192, models[0].capabilities.maxOutputTokens)
        assertEquals(AiCapability.SUPPORTED, models[0].capabilities.nativeToolCalling)
    }

    // ---------------------------------------------------------------- Gemini

    @Test
    fun `Gemini uses contents with functionCall and functionResponse parts`() {
        val body = GeminiProtocol.buildGenerateContentRequest(
            request(
                AiProviderId.GOOGLE,
                listOf(
                    AiMessage(AiRole.SYSTEM, "You are IronPulse."),
                    AiMessage(AiRole.USER, "Find a bench press"),
                    AiMessage(
                        AiRole.ASSISTANT,
                        toolCalls = listOf(
                            searchCall().copy(providerData = mapOf("thoughtSignature" to "sig-1"))
                        )
                    ),
                    AiMessage(
                        AiRole.TOOL,
                        toolResults = listOf(
                            AiToolResult("call_1", AiToolCatalog.SEARCH_EXERCISES, """{"ok":true}""")
                        )
                    )
                )
            ),
            includeTools = true
        )

        assertEquals(
            "You are IronPulse.",
            body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )
        assertEquals(123, body.getJSONObject("generationConfig").getInt("maxOutputTokens"))

        val contents = body.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("Find a bench press", contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))

        val modelTurn = contents.getJSONObject(1)
        assertEquals("model", modelTurn.getString("role"))
        val functionCallPart = modelTurn.getJSONArray("parts").getJSONObject(0)
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, functionCallPart.getJSONObject("functionCall").getString("name"))
        assertEquals("bench press", functionCallPart.getJSONObject("functionCall").getJSONObject("args").getString("query"))
        assertEquals("sig-1", functionCallPart.getString("thoughtSignature"))

        val resultTurn = contents.getJSONObject(2)
        assertEquals("user", resultTurn.getString("role"))
        val functionResponse = resultTurn.getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, functionResponse.getString("name"))
        assertTrue(functionResponse.getJSONObject("response").getBoolean("ok"))

        val declaration = body.getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals(AiToolCatalog.SEARCH_EXERCISES, declaration.getString("name"))
    }

    @Test
    fun `Gemini tool declarations drop fields its OpenAPI subset rejects`() {
        val body = GeminiProtocol.buildGenerateContentRequest(
            request(AiProviderId.GOOGLE, listOf(AiMessage(AiRole.USER, "hi"))),
            includeTools = true
        )
        val declarations = body.getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations")
        assertTrue(declarations.length() > 0)
        for (index in 0 until declarations.length()) {
            val parameters = declarations.getJSONObject(index).getJSONObject("parameters").toString()
            assertFalse(parameters.contains("additionalProperties"))
            assertFalse(parameters.contains("\"default\""))
            // Fields Gemini does accept must survive the sanitization.
            assertTrue(parameters.contains("\"type\""))
        }
    }

    @Test
    fun `Gemini compatibility transcript uses text parts, never function parts`() {
        val compatibilityRequest = request(
            AiProviderId.GOOGLE,
            listOf(
                AiMessage(AiRole.USER, "How much have I improved in push ups?"),
                AiMessage(
                    AiRole.ASSISTANT,
                    """{"tool_calls":[{"name":"search_exercise_history","arguments":{"exerciseName":"Push-up"}}]}""",
                    toolCalls = listOf(
                        AiToolCall(
                            "compat_1",
                            "search_exercise_history",
                            """{"exerciseName":"Push-up"}"""
                        )
                    )
                ),
                AiMessage(
                    AiRole.TOOL,
                    toolResults = listOf(
                        AiToolResult(
                            "compat_1",
                            "search_exercise_history",
                            """{"ok":true,"sessions":[]}"""
                        )
                    )
                )
            )
        ).copy(toolMode = AiToolMode.COMPATIBILITY)

        val body = GeminiProtocol.buildGenerateContentRequest(compatibilityRequest, includeTools = false)
        val serialized = body.getJSONArray("contents").toString()
        assertFalse(serialized.contains("functionCall"))
        assertFalse(serialized.contains("functionResponse"))
        assertFalse(serialized.contains("thoughtSignature"))

        val contents = body.getJSONArray("contents")
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertTrue(
            contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0)
                .getString("text").contains("search_exercise_history")
        )
        assertEquals("user", contents.getJSONObject(2).getString("role"))
        val results = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(results.startsWith("TOOL RESULTS"))
        assertTrue(results.contains("compat_1"))
    }

    @Test
    fun `Gemini parses a function call and preserves the thought signature`() {
        val response = GeminiProtocol.parseGenerateContentResponse(
            JSONObject(
                """
                {
                  "modelVersion": "gemini-2.5-flash",
                  "candidates": [{
                    "finishReason": "STOP",
                    "content": {"parts": [
                      {"text": "Here"},
                      {"functionCall": {"name": "search_exercises", "args": {"query": "squat"}},
                       "thoughtSignature": "abc"}
                    ]}
                  }]
                }
                """.trimIndent()
            )
        )
        assertEquals("Here", response.text)
        assertEquals(AiFinishReason.TOOL_CALLS, response.finishReason)
        assertEquals(1, response.toolCalls.size)
        assertEquals("gemini_call_2", response.toolCalls[0].id)
        assertEquals("squat", JSONObject(response.toolCalls[0].argumentsJson).getString("query"))
        assertEquals("abc", response.toolCalls[0].providerData["thoughtSignature"])
    }

    @Test
    fun `Gemini builds the model URL and falls back to the default model`() {
        assertTrue(
            GeminiProtocol.generateContentUrl("models/gemini-2.5-flash", "KEY")
                .endsWith("gemini-2.5-flash:generateContent?key=KEY")
        )
        assertTrue(
            GeminiProtocol.generateContentUrl("  ", "KEY")
                .contains("${GeminiProtocol.DEFAULT_MODEL}:generateContent")
        )
    }

    @Test
    fun `Gemini model catalog filters out non generating and unrelated models`() {
        val models = GeminiProtocol.parseModelList(
            JSONObject(
                """
                {"models": [
                  {"name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash",
                   "supportedGenerationMethods": ["generateContent"]},
                  {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]},
                  {"name": "models/gemma-2-9b-it", "supportedGenerationMethods": ["generateContent"]}
                ]}
                """.trimIndent()
            )
        )
        assertEquals(listOf("gemini-2.5-flash", "gemma-2-9b-it"), models.map { it.id }.sorted())
        assertEquals(AiCapability.SUPPORTED, models.first { it.id == "gemini-2.5-flash" }.capabilities.nativeToolCalling)
        assertEquals(AiCapability.UNKNOWN, models.first { it.id == "gemma-2-9b-it" }.capabilities.nativeToolCalling)
    }

    // ------------------------------------------------------- Compatibility

    @Test
    fun `compatibility protocol parses a fenced tool envelope`() {
        val calls = AiCompatibilityProtocol.parseToolCalls(
            """
            ```json
            {"tool_calls": [
              {"name": "search_exercises", "arguments": {"query": "bench"}},
              {"name": "list_routines", "arguments": "{\"limit\":2}"}
            ]}
            ```
            """.trimIndent()
        )
        assertEquals(2, calls?.size)
        assertEquals("compat_1", calls!![0].id)
        assertEquals("search_exercises", calls[0].name)
        assertEquals("bench", JSONObject(calls[0].argumentsJson).getString("query"))
        assertEquals(2, JSONObject(calls[1].argumentsJson).getInt("limit"))
    }

    @Test
    fun `compatibility protocol treats ordinary prose as an answer`() {
        assertNull(
            AiCompatibilityProtocol.parseToolCalls(
                "Try the bench press first. Example shape: {\"tool_calls\": []} is not a request."
            )
        )
        assertNull(AiCompatibilityProtocol.parseToolCalls("No tools needed."))
        assertFalse(AiCompatibilityProtocol.looksLikeToolRequest("No tools needed."))
    }

    @Test
    fun `compatibility protocol caps the number of calls per turn`() {
        val array = JSONArray()
        repeat(AiCompatibilityProtocol.MAX_CALLS_PER_TURN + 4) { index ->
            array.put(
                JSONObject().put("name", "list_routines").put("arguments", JSONObject().put("limit", index + 1))
            )
        }
        val calls = AiCompatibilityProtocol.parseToolCalls(
            JSONObject().put("tool_calls", array).toString()
        )
        assertEquals(AiCompatibilityProtocol.MAX_CALLS_PER_TURN, calls?.size)
    }

    @Test
    fun `compatibility protocol accepts the single call shape`() {
        val calls = AiCompatibilityProtocol.parseToolCalls(
            """{"tool_call":{"name":"get_routine","arguments":{"routineId":3}}}"""
        )
        assertEquals(1, calls?.size)
        assertEquals("get_routine", calls!![0].name)
        assertEquals(3, JSONObject(calls[0].argumentsJson).getInt("routineId"))
    }

    @Test
    fun `OpenAI and Anthropic compatibility transcripts stay plain text`() {
        val messages = listOf(
            AiMessage(AiRole.USER, "How much have I improved in push ups?"),
            AiMessage(
                AiRole.ASSISTANT,
                """{"tool_calls":[{"name":"search_exercise_history","arguments":{"exerciseName":"Push-up"}}]}""",
                toolCalls = listOf(
                    AiToolCall(
                        "compat_1",
                        "search_exercise_history",
                        """{"exerciseName":"Push-up"}"""
                    )
                )
            ),
            AiMessage(
                AiRole.TOOL,
                toolResults = listOf(
                    AiToolResult("compat_1", "search_exercise_history", """{"ok":true}""")
                )
            )
        )
        val openAi = JSONObject()
            .put("messages", OpenAiProtocol.serializeMessages(messages, compatibility = true))
            .toString()
        assertFalse(openAi.contains("tool_calls\":["))
        assertFalse(openAi.contains("\"role\":\"tool\""))
        assertTrue(openAi.contains("TOOL RESULTS"))

        val anthropic = JSONObject()
            .put("messages", AnthropicProtocol.serializeMessages(messages, compatibility = true))
            .toString()
        assertFalse(anthropic.contains("tool_use"))
        assertFalse(anthropic.contains("tool_result"))
        assertTrue(anthropic.contains("TOOL RESULTS"))
    }
}
