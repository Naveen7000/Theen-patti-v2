package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.model.Card
import com.example.model.DealerRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    suspend fun getHandAdvice(
        cards: List<Card>,
        isSeen: Boolean,
        pot: Long,
        chaal: Long,
        walletBalance: Long
    ): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        val cardsStr = cards.joinToString { it.displayName() }
        val eval = DealerRanker.evaluate(cards)
        val handName = eval.handType.displayName
        
        val systemPrompt = "You are the premium Royal Teen Patti Dealer and expert card-playing advisor. You analyze Teen Patti card holdings and give highly tactical, brief, and authentic casino-grade advice (max two sentences, under 30 words). Never use lists or Markdown structures."
        val userPrompt = """
            My cards are: $cardsStr (which is a $handName).
            Pot Size: $pot chips.
            Current Chaal: $chaal chips.
            My Balance: $walletBalance chips.
            Playing style: ${if (isSeen) "Seen (betting 2x)" else "Blind (can play 1x but cannot see cards)"}.
            Should I Pack, call/Chaal, or raise? Give me advice with a percentage win prediction.
        """.trimIndent()

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local fallback logic
            return@withContext getLocalFallbackAdvice(handName, isSeen, chaal, walletBalance)
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userPrompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            }

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed code: ${response.code}")
                    return@withContext getLocalFallbackAdvice(handName, isSeen, chaal, walletBalance)
                }
                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    if (contentObj != null) {
                        val partsArr = contentObj.optJSONArray("parts")
                        if (partsArr != null && partsArr.length() > 0) {
                            val text = partsArr.getJSONObject(0).optString("text")
                            if (text.isNotEmpty()) return@withContext text.trim()
                        }
                    }
                }
                return@withContext getLocalFallbackAdvice(handName, isSeen, chaal, walletBalance)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in calling Gemini API", e)
            return@withContext getLocalFallbackAdvice(handName, isSeen, chaal, walletBalance)
        }
    }

    suspend fun getDealerChatMsg(eventDescription: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        val systemPrompt = "You are the witty, suspenseful Royal Teen Patti Table Dealer. You make short, interactive live casino dealer comments (exactly 1 sentence, max 15 words) on what happens at the card table."
        val userPrompt = "Comment on this table event: $eventDescription."

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getLocalFallbackDealerChat(eventDescription)
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userPrompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            }

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext getLocalFallbackDealerChat(eventDescription)
                val bodyStr = response.body?.string() ?: ""
                val rootJson = JSONObject(bodyStr)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    if (contentObj != null) {
                        val partsArr = contentObj.optJSONArray("parts")
                        if (partsArr != null && partsArr.length() > 0) {
                            val text = partsArr.getJSONObject(0).optString("text")
                            if (text.isNotEmpty()) return@withContext text.trim()
                        }
                    }
                }
                return@withContext getLocalFallbackDealerChat(eventDescription)
            }
        } catch (e: Exception) {
            return@withContext getLocalFallbackDealerChat(eventDescription)
        }
    }

    private fun getLocalFallbackAdvice(handName: String, isSeen: Boolean, chaal: Long, balance: Long): String {
        return when (handName) {
            "Trio / Trail" -> "Unbelievable Trio! You have almost 99% win chance. Go high, increase the chaal, and make them pay."
            "Pure Sequence / Straight Flush" -> "Superb Straight Flush (90% win). Keep raising the bet, let seen players sweat their piles."
            "Sequence / Straight" -> "Solid sequence (75% win). You are in a strong position. Let the blind players call while you maintain steady chaal."
            "Color / Flush" -> "Flush is a premium holding (65% win). If playing ${if (isSeen) "Seen" else "Blind"}, raise slightly to test other players' nerves."
            "Pair" -> "A pair is a decent holding (45% win). If the chaal ($chaal) is low, call or see. If others raise aggressively, consider packing."
            else -> "High Card is risky (15% win). Play ${if (isSeen) "Seen" else "Blind"} cautiously. Unless the table is passive, packing is highly advisable."
        }
    }

    private fun getLocalFallbackDealerChat(event: String): String {
        return when {
            event.contains("packed", ignoreCase = true) -> "Folded under pressure! Who's next to test their luck?"
            event.contains("saw", ignoreCase = true) || event.contains("seen", ignoreCase = true) -> "A player looks at their cards... The tension is mounting!"
            event.contains("chaal", ignoreCase = true) || event.contains("bet", ignoreCase = true) -> "The chips fall into the pot. The game gets bigger!"
            event.contains("show", ignoreCase = true) -> "Showdown requested! Let's see who rules the table tonight!"
            event.contains("won", ignoreCase = true) -> "Congratulations, what a magnificent win!"
            else -> "The cards speak, the chips make noise. Good luck players!"
        }
    }
}
