package com.kazahana.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

/**
 * Calls the Anthropic Messages API to generate concise ALT text for images.
 */
object ClaudeService {

    private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val API_VERSION = "2023-06-01"
    private const val MAX_WIDTH = 1024
    private const val MAX_BYTES = 1_000_000 // 1 MB

    private val client = HttpClient(OkHttp)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Prepare image bytes for the API: resize to max 1024px width,
     * compress JPEG quality 0.8 then 0.3 if needed, cap at 1 MB.
     */
    fun prepareImageBytes(originalBytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
            ?: return originalBytes

        // Resize if wider than MAX_WIDTH
        val scaled = if (bitmap.width > MAX_WIDTH) {
            val ratio = MAX_WIDTH.toFloat() / bitmap.width
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        // Try quality 80 first
        val out80 = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out80)
        if (out80.size() <= MAX_BYTES) {
            if (scaled !== bitmap) scaled.recycle()
            return out80.toByteArray()
        }

        // Fallback to quality 30
        val out30 = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 30, out30)
        if (scaled !== bitmap) scaled.recycle()
        return out30.toByteArray()
    }

    /**
     * Map BCP-47 language code to a natural language name for the prompt.
     */
    private fun languageName(code: String): String = when (code.lowercase().substringBefore("-")) {
        "ja" -> "Japanese"
        "en" -> "English"
        "zh" -> "Chinese"
        "ko" -> "Korean"
        "fr" -> "French"
        "de" -> "German"
        "es" -> "Spanish"
        "pt" -> "Portuguese"
        "ru" -> "Russian"
        "id" -> "Indonesian"
        else -> code
    }

    /**
     * Generate ALT text for the given image bytes.
     *
     * @param imageBytes   Raw image bytes (will be resized/compressed internally)
     * @param apiKey       Anthropic API key
     * @param languageCode BCP-47 language code (e.g. "ja", "en") for the output language
     * @return Result containing the generated ALT text string
     */
    suspend fun generateAltText(imageBytes: ByteArray, apiKey: String, languageCode: String = "en"): Result<String> {
        return try {
            val prepared = prepareImageBytes(imageBytes)
            val base64 = Base64.encodeToString(prepared, Base64.NO_WRAP)
            val lang = languageName(languageCode)

            val requestBody = """
                {
                  "model": "$MODEL",
                  "max_tokens": 300,
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {
                          "type": "image",
                          "source": {
                            "type": "base64",
                            "media_type": "image/jpeg",
                            "data": "$base64"
                          }
                        },
                        {
                          "type": "text",
                          "text": "Generate a concise accessibility ALT text for this image in $lang. Describe what is shown in the image specifically, within 150 characters. Output only the ALT text itself with no preamble or explanation."
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val response = client.post(ENDPOINT) {
                header("x-api-key", apiKey)
                header("anthropic-version", API_VERSION)
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            val bodyText = response.bodyAsText()

            if (response.status.value != 200) {
                val errorMsg = try {
                    val parsed = json.parseToJsonElement(bodyText).jsonObject
                    parsed["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: "API error ${response.status.value}"
                } catch (_: Exception) {
                    "API error ${response.status.value}"
                }
                return Result.failure(Exception(errorMsg))
            }

            val parsed = json.parseToJsonElement(bodyText).jsonObject
            val content = parsed["content"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = content?.get("text")?.jsonPrimitive?.content
                ?: return Result.failure(Exception("Empty response from Claude API"))

            Result.success(text.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
