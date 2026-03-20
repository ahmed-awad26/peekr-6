package com.peekr.data.remote.telegram

import android.content.Context
import com.peekr.core.logger.AppLogger
import com.peekr.data.local.dao.AccountDao
import com.peekr.data.local.dao.ApiKeyDao
import com.peekr.data.local.dao.PostDao
import com.peekr.data.local.entities.AccountEntity
import com.peekr.data.local.entities.PostEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class TelegramAuthState {
    object Idle : TelegramAuthState()
    object WaitingPhone : TelegramAuthState()
    object WaitingCode : TelegramAuthState()
    object WaitingPassword : TelegramAuthState()
    object Authorized : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

@Singleton
class TelegramClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyDao: ApiKeyDao,
    private val accountDao: AccountDao,
    private val postDao: PostDao,
    private val logger: AppLogger
) {
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState

    private val httpClient = OkHttpClient()

    // ==============================
    // Check API keys — حضر للدخول
    // ==============================
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiId   = apiKeyDao.getApiKeyByPlatform("telegram_id")?.keyValue?.toIntOrNull()
            val apiHash = apiKeyDao.getApiKeyByPlatform("telegram_hash")?.keyValue
            val botToken = apiKeyDao.getApiKeyByPlatform("telegram_bot")?.keyValue

            when {
                // Bot token موجود → نستخدم Bot API مباشرة
                !botToken.isNullOrBlank() -> {
                    val valid = verifyBotToken(botToken)
                    if (valid) {
                        _authState.value = TelegramAuthState.Authorized
                        saveAccount("bot:$botToken", "Telegram Bot")
                        logger.info("تليجرام Bot: تم التحقق", "telegram")
                        true
                    } else {
                        _authState.value = TelegramAuthState.Error("Bot Token غير صحيح — تحقق من @BotFather")
                        false
                    }
                }
                // API ID + Hash موجودان → جاهز لدخول المستخدم
                apiId != null && !apiHash.isNullOrBlank() -> {
                    _authState.value = TelegramAuthState.WaitingPhone
                    true
                }
                // لا شيء موجود
                else -> {
                    _authState.value = TelegramAuthState.Error(
                        "أضف Bot Token أو API ID + Hash في مفاتيح API\n\n" +
                        "للقنوات العامة: احصل على Bot Token من @BotFather\n" +
                        "للحساب الشخصي: API ID من my.telegram.org"
                    )
                    false
                }
            }
        } catch (e: Exception) {
            _authState.value = TelegramAuthState.Error(e.message ?: "خطأ")
            false
        }
    }

    // ==============================
    // Bot Token API — verify
    // ==============================
    private suspend fun verifyBotToken(token: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$token/getMe"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            json.optBoolean("ok", false)
        } catch (_: Exception) { false }
    }

    // ==============================
    // Phone login (requires TDLib native)
    // ==============================
    suspend fun sendPhoneNumber(phone: String) = withContext(Dispatchers.IO) {
        if (phone.isBlank()) {
            _authState.value = TelegramAuthState.Error("أدخل رقم الهاتف")
            return@withContext
        }

        // TDLib غير متاحة في هذا البناء — نشرح للمستخدم
        _authState.value = TelegramAuthState.Error(
            "تسجيل الدخول بالهاتف يحتاج TDLib النسخة النيتف.\n\n" +
            "الحل البديل المتاح الآن:\n" +
            "① أضف Bot Token من @BotFather في مفاتيح API\n" +
            "② أضف البوت لقنواتك العامة\n" +
            "③ ستظهر المنشورات في الفيد تلقائياً"
        )
    }

    suspend fun sendCode(code: String) = withContext(Dispatchers.IO) {
        // Placeholder — needs TDLib
    }

    suspend fun sendPassword(password: String) = withContext(Dispatchers.IO) {
        // Placeholder — needs TDLib
    }

    // ==============================
    // Sync — يجلب رسائل القنوات عبر Bot API
    // ==============================
    suspend fun syncChats(): Result<Int> = withContext(Dispatchers.IO) {
        val botToken = apiKeyDao.getApiKeyByPlatform("telegram_bot")?.keyValue
        if (botToken.isNullOrBlank()) {
            return@withContext Result.failure(Exception("أضف Telegram Bot Token في مفاتيح API"))
        }

        try {
            // جلب القنوات المضافة (كل account هو channel username)
            val channels = accountDao.getAllAccountsByPlatformSync("telegram")
            if (channels.isEmpty()) {
                return@withContext Result.failure(Exception("أضف أسماء قنوات تليجرام من ربط الحسابات"))
            }

            var totalNew = 0
            channels.forEach { channel ->
                try {
                    val count = fetchChannelMessages(botToken, channel.accountName.trim())
                    totalNew += count
                } catch (e: Exception) {
                    logger.error("فشل جلب قناة تليجرام: ${channel.accountName}", "telegram", e)
                }
            }

            Result.success(totalNew)
        } catch (e: Exception) {
            logger.error("خطأ في sync تليجرام", "telegram", e)
            Result.failure(e)
        }
    }

    // ==============================
    // Fetch channel messages via getUpdates / forwardMessage
    // ==============================
    private suspend fun fetchChannelMessages(botToken: String, channelUsername: String): Int {
        var newCount = 0
        try {
            // استخدم getChat للحصول على معلومات القناة
            val chatUsername = channelUsername
                .removePrefix("https://t.me/")
                .removePrefix("t.me/")
                .removePrefix("@")
                .substringBefore("/")

            // جلب الرسائل عبر forwardMessages أو getUpdates
            val url = "https://api.telegram.org/bot$botToken/getUpdates?limit=20&allowed_updates=[\"channel_post\"]"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: return 0
            val json = JSONObject(body)

            if (!json.optBoolean("ok", false)) return 0

            val updates = json.optJSONArray("result") ?: return 0
            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                val post = update.optJSONObject("channel_post") ?: continue

                val chat = post.optJSONObject("chat") ?: continue
                val chatUsername2 = chat.optString("username", "")
                // تصفية — فقط القناة المطلوبة
                if (chatUsername2.lowercase() != chatUsername.lowercase()) continue

                val msgId  = post.optLong("message_id")
                val text   = post.optString("text", post.optString("caption", ""))
                val date   = post.optLong("date") * 1000L
                val postUrl = "https://t.me/$chatUsername/$msgId"

                if (postDao.existsByUrl(postUrl)) continue
                if (text.isBlank()) continue

                // صورة لو موجودة
                val photo = post.optJSONArray("photo")
                val photoUrl: String? = if (photo != null && photo.length() > 0) {
                    val fileId = photo.getJSONObject(photo.length() - 1).optString("file_id")
                    getFileUrl(botToken, fileId)
                } else null

                postDao.insertPost(
                    PostEntity(
                        platformId = "telegram",
                        sourceId   = chatUsername,
                        sourceName = chat.optString("title", chatUsername),
                        content    = text,
                        mediaUrl   = photoUrl,
                        postUrl    = postUrl,
                        timestamp  = date
                    )
                )
                newCount++
            }
        } catch (e: Exception) {
            logger.error("خطأ fetchChannelMessages: $channelUsername", "telegram", e)
        }
        return newCount
    }

    private suspend fun getFileUrl(botToken: String, fileId: String): String? {
        return try {
            val url = "https://api.telegram.org/bot$botToken/getFile?file_id=$fileId"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val filePath = json.optJSONObject("result")?.optString("file_path") ?: return null
            "https://api.telegram.org/file/bot$botToken/$filePath"
        } catch (_: Exception) { null }
    }

    private suspend fun saveAccount(extraData: String, name: String) {
        withContext(Dispatchers.IO) {
            accountDao.insertAccount(
                AccountEntity(
                    platformId  = "telegram",
                    accountName = name,
                    isConnected = true,
                    connectedAt = System.currentTimeMillis(),
                    extraData   = extraData
                )
            )
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        accountDao.deleteAccountByPlatform("telegram")
        _authState.value = TelegramAuthState.Idle
        logger.info("تليجرام: تم قطع الاتصال", "telegram")
    }

    fun isAuthorized() = _authState.value == TelegramAuthState.Authorized
}
