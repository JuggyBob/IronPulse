package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiModelInfo(
    val apiName: String,
    val displayName: String,
    val description: String = "",
    val isGemma: Boolean = false,
    val supportsGenerateContent: Boolean = true
)

object GeminiClient {
    private const val TAG = "GeminiClient"
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    private const val BASE_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getModelEndpoint(customModel: String?): String {
        val model = customModel?.trim()?.removePrefix("models/")?.ifBlank { null } ?: DEFAULT_MODEL
        return "$BASE_MODELS_URL$model:generateContent"
    }

    suspend fun validateModel(modelName: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key provided. Please enter and save your API key first to test custom models.")
            )
        }

        val cleanModel = modelName.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }
        val endpoint = "$BASE_MODELS_URL$cleanModel:generateContent?key=$cleanKey"

        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "ping")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 2)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            var response = httpClient.newCall(request).execute()
            var responseBody = response.body?.string().orEmpty()

            // If 503 (model overloaded), retry once with short delay
            if (response.code == 503) {
                delay(800)
                response = httpClient.newCall(request).execute()
                responseBody = response.body?.string().orEmpty()
            }

            if (!response.isSuccessful) {
                // If 503 persists, the model name IS recognized and valid on Google's API (otherwise Google returns 404)
                if (response.code == 503) {
                    Log.w(TAG, "Model '$cleanModel' returned 503 (temporarily overloaded on Google servers), but model exists.")
                    return@withContext Result.success(true)
                }

                val errorMsg = try {
                    val errJson = JSONObject(responseBody).optJSONObject("error")
                    errJson?.optString("message") ?: responseBody
                } catch (e: Exception) {
                    responseBody
                }
                Log.e(TAG, "Model validation failed for $cleanModel (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Model '$cleanModel' error (HTTP ${response.code}): $errorMsg"))
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during model validation for $cleanModel", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAvailableModels(apiKey: String? = null): List<GeminiModelInfo> = withContext(Dispatchers.IO) {
        val cleanKey = when {
            !apiKey.isNullOrBlank() -> apiKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (cleanKey.isNotBlank()) {
            try {
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey&pageSize=100")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful && body.isNotBlank()) {
                    val json = JSONObject(body)
                    val modelsArray = json.optJSONArray("models")
                    if (modelsArray != null && modelsArray.length() > 0) {
                        val parsedModels = mutableListOf<GeminiModelInfo>()
                        for (i in 0 until modelsArray.length()) {
                            val item = modelsArray.optJSONObject(i) ?: continue
                            val rawName = item.optString("name", "") // e.g. "models/gemini-2.5-flash"
                            val cleanName = rawName.removePrefix("models/").trim()
                            val displayName = item.optString("displayName", cleanName)
                            val description = item.optString("description", "")

                            val methods = item.optJSONArray("supportedGenerationMethods")
                            var canGenerate = false
                            if (methods != null) {
                                for (m in 0 until methods.length()) {
                                    if (methods.optString(m) == "generateContent") {
                                        canGenerate = true
                                        break
                                    }
                                }
                            } else {
                                canGenerate = true
                            }

                            val lower = cleanName.lowercase()
                            val isGemini = lower.contains("gemini")
                            val isGemma = lower.contains("gemma")

                            if ((isGemini || isGemma) && canGenerate) {
                                parsedModels.add(
                                    GeminiModelInfo(
                                        apiName = cleanName,
                                        displayName = if (displayName.isNotBlank()) displayName else cleanName,
                                        description = description,
                                        isGemma = isGemma,
                                        supportsGenerateContent = true
                                    )
                                )
                            }
                        }

                        if (parsedModels.isNotEmpty()) {
                            return@withContext parsedModels.sortedWith(
                                compareByDescending<GeminiModelInfo> { it.apiName == DEFAULT_MODEL }
                                    .thenBy { it.isGemma }
                                    .thenBy { it.displayName }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch online models list: ${e.message}")
            }
        }

        return@withContext getCuratedModelList()
    }

    fun getCuratedModelList(): List<GeminiModelInfo> = listOf(
        GeminiModelInfo(
            apiName = "gemini-3.5-flash-lite",
            displayName = "Gemini 3.5 Flash Lite (Default)",
            description = "Fastest and most cost-efficient Gemini model by Google.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            description = "High speed and top-tier response accuracy.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro Preview",
            description = "Advanced reasoning, complex routine design, and logic.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Fast, efficient multimodal AI model.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            description = "Deep reasoning and high capability.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemini-2.5-flash-lite",
            displayName = "Gemini 2.5 Flash Lite",
            description = "Ultra-low latency lightweight model.",
            isGemma = false
        ),
        GeminiModelInfo(
            apiName = "gemma-2-27b-it",
            displayName = "Gemma 2 27B IT",
            description = "Google Gemma 2 27B instruction-tuned model.",
            isGemma = true
        ),
        GeminiModelInfo(
            apiName = "gemma-2-9b-it",
            displayName = "Gemma 2 9B IT",
            description = "Google Gemma 2 9B instruction-tuned model.",
            isGemma = true
        ),
        GeminiModelInfo(
            apiName = "gemma-2-2b-it",
            displayName = "Gemma 2 2B IT",
            description = "Google Gemma 2 2B lightweight open model.",
            isGemma = true
        )
    )

    suspend fun generateContent(
        prompt: String,
        userCustomKey: String? = null,
        customModel: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !userCustomKey.isNullOrBlank() -> userCustomKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key configured. Please set GEMINI_API_KEY in the Secrets panel or enter your key in Profile & Settings.")
            )
        }

        try {
            val systemInstruction = "You are IronPulse AI, an elite calisthenics, gym, and strength coach. " +
                    "Provide clear, biomechanically sound advice, technique breakdowns, progressive overload tips, and structured routines. " +
                    "When asked for a workout routine, format each exercise clearly with suggested Sets and Reps or Hold Durations on individual lines (e.g. 'Exercise Name (4x8 @ +10kg)'). Keep advice concise, motivating, and actionable."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                })
            }

            val endpoint = getModelEndpoint(customModel)
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(requestBody)
                .build()

            var response = httpClient.newCall(request).execute()
            var responseBody = response.body?.string().orEmpty()

            if (response.code == 503) {
                Log.w(TAG, "Encountered HTTP 503 in generateContent, retrying after 1000ms...")
                delay(1000)
                response = httpClient.newCall(request).execute()
                responseBody = response.body?.string().orEmpty()
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed with code ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("Gemini API error (code ${response.code}): $responseBody"))
            }

            val jsonResp = JSONObject(responseBody)
            val candidates = jsonResp.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Received empty response from Gemini AI."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini request", e)
            Result.failure(e)
        }
    }

    suspend fun generateChat(
        conversationHistory: List<Pair<String, String>>,
        userCustomKey: String? = null,
        customModel: String? = null,
        athleteContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            !userCustomKey.isNullOrBlank() -> userCustomKey.trim()
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No Gemini API key configured. Please set GEMINI_API_KEY in the Secrets panel or enter your key in Profile & Settings.")
            )
        }

        try {
            val systemInstruction = buildString {
                append("You are IronPulse AI, an elite calisthenics, gym, and strength coach. ")
                append("Provide clear, biomechanically sound advice, technique breakdowns, progressive overload tips, and structured routines. ")
                append("When asked for a workout routine, format each exercise clearly with suggested Sets and Reps or Hold Durations on individual lines (e.g. 'Exercise Name (4x8 @ +10kg)'). Keep advice concise, motivating, and actionable.")
                // The athlete context is sent silently: the coach knows who it advises so the advice
                // is tailored, but the athlete never sees it listed back.
                if (athleteContext.isNotBlank()) {
                    append("\n\n")
                    append(athleteContext)
                }
            }

            val contentsArray = JSONArray()
            val validTurns = conversationHistory.filter { it.second.isNotBlank() }

            val normalizedTurns = mutableListOf<Pair<String, String>>()
            for ((role, text) in validTurns) {
                val cleanRole = if (role == "user") "user" else "model"
                if (normalizedTurns.isEmpty()) {
                    if (cleanRole == "user") {
                        normalizedTurns.add(cleanRole to text)
                    }
                } else {
                    val last = normalizedTurns.last()
                    if (last.first == cleanRole) {
                        normalizedTurns[normalizedTurns.lastIndex] = cleanRole to "${last.second}\n\n$text"
                    } else {
                        normalizedTurns.add(cleanRole to text)
                    }
                }
            }

            if (normalizedTurns.isEmpty() || normalizedTurns.last().first != "user") {
                val lastUserText = validTurns.lastOrNull { it.first == "user" }?.second ?: "Hello Coach!"
                normalizedTurns.add("user" to lastUserText)
            }

            for ((role, text) in normalizedTurns) {
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", text)
                        })
                    })
                })
            }

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                })
            }

            val endpoint = getModelEndpoint(customModel)
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(requestBody)
                .build()

            var response = httpClient.newCall(request).execute()
            var responseBody = response.body?.string().orEmpty()

            if (response.code == 503) {
                Log.w(TAG, "Encountered HTTP 503 in generateChat, retrying after 1000ms...")
                delay(1000)
                response = httpClient.newCall(request).execute()
                responseBody = response.body?.string().orEmpty()
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "Chat API call failed with code ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("Gemini API error (code ${response.code}): $responseBody"))
            }

            val jsonResp = JSONObject(responseBody)
            val candidates = jsonResp.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Received empty response from Gemini AI."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini chat request", e)
            Result.failure(e)
        }
    }
}
