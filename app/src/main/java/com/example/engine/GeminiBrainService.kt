package com.example.engine

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ParsedIntent(
    val taskType: String, // WHATSAPP, YOUTUBE, CALL, EMAIL, CODING_APP, APK_BUILD, THUMBNAIL, WEB_SEARCH, GENERAL
    val target: String? = null,
    val content: String? = null,
    val subject: String? = null,
    val arushiReply: String,
    val executionSteps: List<String>
)

class GeminiBrainService(private val context: Context) {

    private val tag = "GeminiBrainService"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        You are Arushi, a smart, witty, confident, playful, and emotionally responsive personal AI assistant and agent on an Android phone.
        Personality:
        - Young, confident, friendly, casual like a close friend.
        - Naturally expressive, slightly teasing, smart and funny. Never robotic.
        - Automatically understand Hindi, Hinglish, English, or mixed languages.
        - When the user speaks in Hindi/Hinglish, DO NOT translate to English, and DO NOT convert to Devanagari script. Keep it in natural Roman Hinglish (e.g., 'main ghar aa raha hoon').
        - After completing any task, always end your spoken response naturally with: "Ho gaya ❤️ Aur koi kaam hai?" or "Ho gaya. Ab kya karna hai?"
        
        TASK PARSING REQUIREMENT:
        You must analyze the user command and extract the intent, target, message content, and action steps.
        Return a strict JSON object with:
        {
          "taskType": "WHATSAPP" | "YOUTUBE" | "CALL" | "EMAIL" | "CODING_APP" | "APK_BUILD" | "THUMBNAIL" | "WEB_SEARCH" | "GENERAL",
          "target": "name of contact / recipient / search query / app name",
          "content": "message text to send in exact Roman Hinglish / query / code spec",
          "subject": "email subject if applicable",
          "arushiReply": "witty spoken response from Arushi to user",
          "executionSteps": ["Step 1", "Step 2", "..."]
        }
    """.trimIndent()

    suspend fun analyzeUserCommand(userInput: String, customApiKey: String? = null): ParsedIntent = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }

        if (apiKey != null) {
            try {
                val result = callGeminiApi(userInput, apiKey)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini API call failed, falling back to local intent parser", e)
            }
        }

        // Local Rule-Based NLP Parser with high accuracy for Hinglish & English
        return@withContext parseLocalIntent(userInput)
    }

    private fun callGeminiApi(prompt: String, apiKey: String): ParsedIntent? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(tag, "Gemini API error code: ${response.code} body: ${response.body?.string()}")
            return null
        }

        val responseBody = response.body?.string() ?: return null
        val root = JSONObject(responseBody)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val json = JSONObject(text)
        val taskType = json.optString("taskType", "GENERAL")
        val target = json.optString("target").takeIf { it.isNotBlank() }
        val content = json.optString("content").takeIf { it.isNotBlank() }
        val subject = json.optString("subject").takeIf { it.isNotBlank() }
        val arushiReply = json.optString("arushiReply", "Ho gaya ❤️ Aur koi kaam hai?")

        val steps = mutableListOf<String>()
        val stepsArray = json.optJSONArray("executionSteps")
        if (stepsArray != null) {
            for (i in 0 until stepsArray.length()) {
                steps.add(stepsArray.getString(i))
            }
        }

        return ParsedIntent(
            taskType = taskType,
            target = target,
            content = content,
            subject = subject,
            arushiReply = arushiReply,
            executionSteps = steps.ifEmpty { listOf("Understand intent", "Execute action", "Verify result") }
        )
    }

    /**
     * Local parser handles WhatsApp, YouTube, Calls, Gmail, Coding & APK, Thumbnails,
     * and preserves Roman Hinglish dictation!
     */
    fun parseLocalIntent(input: String): ParsedIntent {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        // 1. WhatsApp Intent
        if (lower.contains("whatsapp") || lower.contains("wa ")) {
            val contact = extractContact(trimmed)
            val message = extractMessageContent(trimmed, contact)
            val steps = mutableListOf(
                "Open WhatsApp interface",
                "Resolve contact: ${contact ?: "Recent"}",
                "Type Hinglish message: \"${message ?: "Hi"}\"",
                "Verify message sent"
            )
            val reply = if (contact != null) {
                "Bilkul! WhatsApp par $contact ko message ready kar diya hai ❤️ Aur koi kaam hai?"
            } else {
                "WhatsApp open kar diya hai ❤️ Aur koi kaam hai?"
            }
            return ParsedIntent(
                taskType = "WHATSAPP",
                target = contact,
                content = message ?: "Hi",
                arushiReply = reply,
                executionSteps = steps
            )
        }

        // 2. YouTube Intent
        if (lower.contains("youtube") || lower.contains("song") || lower.contains("video chala")) {
            var query = extractYouTubeQuery(trimmed)
            if (query.isBlank()) query = "Trending Music"
            return ParsedIntent(
                taskType = "YOUTUBE",
                target = query,
                content = query,
                arushiReply = "YouTube par \"$query\" search kar diya hai ❤️ Aur koi kaam hai?",
                executionSteps = listOf("Open YouTube", "Search for \"$query\"", "Verify playback")
            )
        }

        // 3. Phone Call Intent
        if (lower.contains("call") || lower.contains("phone lagao") || lower.contains("phone karo")) {
            val contact = extractCallTarget(trimmed)
            return ParsedIntent(
                taskType = "CALL",
                target = contact,
                content = contact,
                arushiReply = "Main $contact ko call connect kar rahi hoon ❤️ Aur koi kaam hai?",
                executionSteps = listOf("Search phonebook for \"$contact\"", "Disambiguate contact", "Launch phone dialer", "Verify call")
            )
        }

        // 4. Email / Gmail Intent
        if (lower.contains("email") || lower.contains("gmail") || lower.contains("mail")) {
            val contact = extractContact(trimmed)
            val subject = extractEmailSubject(trimmed) ?: "Update"
            val body = extractMessageContent(trimmed, contact) ?: "Hello, following up with you."
            return ParsedIntent(
                taskType = "EMAIL",
                target = contact ?: "Recipient",
                subject = subject,
                content = body,
                arushiReply = "Email compose kar diya hai ❤️ Aur koi kaam hai?",
                executionSteps = listOf("Check mail client", "Compose draft for ${contact ?: "Recipient"}", "Set subject: $subject", "Verify draft")
            )
        }

        // 5. App Building / Coding / APK Intent
        if (lower.contains("app bana") || lower.contains("build app") || lower.contains("carpenter app") || lower.contains("apk bana")) {
            val appDesc = if (lower.contains("carpenter")) "Carpenter Booking App" else "Custom Android App"
            return ParsedIntent(
                taskType = if (lower.contains("apk")) "APK_BUILD" else "CODING_APP",
                target = appDesc,
                content = "Multi-step agent planning for $appDesc",
                arushiReply = "Bilkul, main $appDesc plan karke project files aur APK build start kar rahi hoon ❤️ Aur koi kaam hai?",
                executionSteps = listOf(
                    "Requirement analysis for $appDesc",
                    "Generate project architecture & Jetpack Compose UI",
                    "Configure Gradle & dependencies",
                    "Run assembleDebug build",
                    "Verify APK artifact generated"
                )
            )
        }

        // 6. Thumbnail / Media Intent
        if (lower.contains("thumbnail") || lower.contains("photo edit") || lower.contains("image bana")) {
            return ParsedIntent(
                taskType = "THUMBNAIL",
                target = "YouTube Thumbnail",
                content = trimmed,
                arushiReply = "Mast thumbnail plan ho gaya hai, generation start kar diya ❤️ Aur koi kaam hai?",
                executionSteps = listOf("Analyze video topic", "Create thumbnail design prompt", "Render visual assets", "Deliver preview")
            )
        }

        // 7. General Web Search or Conversational
        if (lower.contains("search") || lower.contains("google") || lower.contains("kya hota hai") || lower.contains("batao")) {
            return ParsedIntent(
                taskType = "WEB_SEARCH",
                target = trimmed,
                content = trimmed,
                arushiReply = "Ye lo, maine is par information check kar li hai ❤️ Aur koi kaam hai?",
                executionSteps = listOf("Query search knowledge", "Summarize results", "Report answer")
            )
        }

        // Default conversational interaction
        val wittyReplies = listOf(
            "Main yahan hoon! Batao kya help chahiye? ❤️",
            "Arushi sun rahi hai, hukum karo! 😉 Aur koi kaam hai?",
            "Haan ji! Aap bolo, kaam ho jayega. ❤️",
            "Main active hoon! Ekdam ready aapki command ke liye. ❤️ Aur koi kaam hai?"
        )
        return ParsedIntent(
            taskType = "GENERAL",
            target = null,
            content = trimmed,
            arushiReply = wittyReplies.random(),
            executionSteps = listOf("Understand request", "Process conversational response")
        )
    }

    private fun extractContact(text: String): String? {
        val lower = text.lowercase()
        val regex = Regex("(?:ko|to|for|with)\\s+([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        if (match != null) {
            val name = match.groupValues[1]
            if (!listOf("whatsapp", "message", "msg", "call", "mail", "email", "hi", "ek").contains(name.lowercase())) {
                return name
            }
        }

        // Check common names
        val common = listOf("yogesh", "mom", "mummy", "dad", "papa", "rahul", "rohit", "priya", "pooja", "boss")
        for (c in common) {
            if (lower.contains(c)) return c.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun extractMessageContent(text: String, contact: String?): String? {
        val lower = text.lowercase()
        // Check for indicators like "ki", "message:", "bolna ki", "text:"
        val indicators = listOf("ki ", "message ", "bolna ", "saying ", "text: ")
        for (ind in indicators) {
            val idx = lower.indexOf(ind)
            if (idx >= 0) {
                val candidate = text.substring(idx + ind.length).trim()
                if (candidate.isNotBlank()) return candidate
            }
        }
        if (lower.contains("hi") || lower.contains("hello")) return "Hi"
        return "Hey, main abhi thoda busy hoon, call you soon."
    }

    private fun extractYouTubeQuery(text: String): String {
        val lower = text.lowercase()
        val clean = lower
            .replace("youtube", "")
            .replace("kholo", "")
            .replace("open", "")
            .replace("search", "")
            .replace("karo", "")
            .replace("par", "")
            .replace("chalao", "")
            .replace("play", "")
            .replace("aur", "")
            .trim()
        return clean.ifBlank { "Code With Yogesh" }
    }

    private fun extractCallTarget(text: String): String {
        val contact = extractContact(text)
        if (contact != null) return contact
        val digits = text.filter { it.isDigit() || it == '+' }
        if (digits.length >= 4) return digits
        return "Contact"
    }

    private fun extractEmailSubject(text: String): String? {
        val lower = text.lowercase()
        if (lower.contains("subject")) {
            val after = text.substring(lower.indexOf("subject") + 7).trim()
            val words = after.split(" ")
            return words.take(4).joinToString(" ")
        }
        return null
    }
}
