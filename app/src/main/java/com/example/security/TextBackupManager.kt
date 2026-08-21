package com.example.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.example.data.AppDatabase
import com.example.data.CachedGroup
import com.example.data.CachedGroupMessage
import com.example.data.CachedMessage
import com.example.data.CachedPost
import com.example.data.CachedStory
import com.example.data.CachedUser
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted, text-only offline backup.
 *
 * The public copy is intentionally ciphertext only, so another app/user cannot
 * read the content. The app-private copy is additionally stored under filesDir
 * and is not exposed through Android's document picker. Images, videos, audio,
 * and file payloads are never included.
 */
object TextBackupManager {
    private const val MAGIC = "CONVO_TEXT_BACKUP_V1"
    private const val FILE_PREFIX = "convo-text-backup-"
    private const val FILE_SUFFIX = ".cbackup"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    data class Result(val restored: Boolean, val itemCount: Int = 0, val reason: String = "")

    suspend fun export(context: Context, uid: String): Result = runCatching {
        val dao = AppDatabase.getDatabase(context).cacheDao()
        val root = JSONObject().apply {
            put("magic", MAGIC)
            put("uid", uid)
            put("createdAt", System.currentTimeMillis())
            put("users", JSONArray(dao.getAllUsers().first().map(::userJson)))
            put("messages", JSONArray(buildMessages(dao)))
            put("groups", JSONArray(dao.getAllGroups().first().map(::groupJson)))
            put("groupMessages", JSONArray(buildGroupMessages(dao)))
            put("stories", JSONArray(dao.getAllStories().first().map(::storyJson)))
            put("posts", JSONArray(dao.getAllPosts().first().map(::postJson)))
        }
        val bytes = encrypt(root.toString().toByteArray(StandardCharsets.UTF_8), uid)
        writePrivate(context, uid, bytes)
        writePublic(context, uid, bytes)
        Result(restored = false, itemCount = root.length())
    }.getOrElse { Result(false, reason = it.message ?: "backup failed") }

    suspend fun restoreLatest(context: Context, uid: String): Result = runCatching {
        val bytes = readLatest(context, uid) ?: return@runCatching Result(false, reason = "no backup found")
        val root = JSONObject(String(decrypt(bytes, uid), StandardCharsets.UTF_8))
        if (root.optString("magic") != MAGIC || root.optString("uid") != uid) {
            return@runCatching Result(false, reason = "backup does not belong to this account")
        }
        val dao = AppDatabase.getDatabase(context).cacheDao()
        var count = 0

        val users = root.optJSONArray("users")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { userFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (users.isNotEmpty()) { dao.insertUsers(users); count += users.size }

        val messages = root.optJSONArray("messages")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { messageFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (messages.isNotEmpty()) { dao.insertMessages(messages); count += messages.size }

        val groups = root.optJSONArray("groups")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { groupFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (groups.isNotEmpty()) { dao.insertGroups(groups); count += groups.size }

        val groupMessages = root.optJSONArray("groupMessages")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { groupMessageFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (groupMessages.isNotEmpty()) { dao.insertGroupMessages(groupMessages); count += groupMessages.size }

        val stories = root.optJSONArray("stories")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { storyFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (stories.isNotEmpty()) { dao.insertStories(stories); count += stories.size }

        val posts = root.optJSONArray("posts")?.let { array ->
            (0 until array.length()).mapNotNull { index -> runCatching { postFromJson(array.getJSONObject(index)) }.getOrNull() }
        }.orEmpty()
        if (posts.isNotEmpty()) { dao.insertPosts(posts); count += posts.size }
        Result(true, count)
    }.getOrElse { Result(false, reason = it.message ?: "restore failed") }

    private suspend fun buildMessages(dao: com.example.data.CacheDao): List<JSONObject> {
        // One ordered snapshot avoids races between conversation-ID and per-chat queries
        // while the app is moving to the background.
        return dao.getAllMessages().first().map(::messageJson)
    }

    private suspend fun buildGroupMessages(dao: com.example.data.CacheDao): List<JSONObject> {
        return dao.getAllGroups().first().flatMap { dao.getGroupMessages(it.id).first() }.map(::groupMessageJson)
    }

    private fun userJson(x: CachedUser) = JSONObject().apply {
        put("uid", x.uid); put("name", x.name); put("dob", x.dob); put("username", x.username)
        put("fcmToken", ""); put("isOnline", false); put("lastActive", x.lastActive)
        put("blockedUsersJson", x.blockedUsersJson); put("createdAt", x.createdAt); put("friendsJson", x.friendsJson)
        put("bio", x.bio); put("followersJson", x.followersJson); put("followingJson", x.followingJson)
        put("role", x.role); put("isPremium", x.isPremium); put("premiumPlan", x.premiumPlan)
        put("premiumUntil", x.premiumUntil); put("premiumApprovedAt", x.premiumApprovedAt)
    }

    private fun userFromJson(x: JSONObject) = CachedUser(
        uid = x.optString("uid"), name = x.optString("name"), dob = x.optString("dob"),
        username = x.optString("username"), fcmToken = "", profileImageUrl = "", isOnline = false,
        lastActive = x.optLong("lastActive"), blockedUsersJson = x.optString("blockedUsersJson", "[]"),
        createdAt = x.optLong("createdAt"), friendsJson = x.optString("friendsJson", "[]"),
        bio = x.optString("bio"), coverImageUrl = "", followersJson = x.optString("followersJson", "[]"),
        followingJson = x.optString("followingJson", "[]"), role = x.optString("role", "user"),
        isPremium = x.optBoolean("isPremium"), premiumPlan = x.optString("premiumPlan"),
        premiumUntil = x.optLong("premiumUntil"), premiumApprovedAt = x.optLong("premiumApprovedAt")
    )

    private fun messageJson(x: CachedMessage) = JSONObject().apply {
        put("messageId", x.messageId); put("senderId", x.senderId); put("senderName", x.senderName)
        put("senderUsername", x.senderUsername); put("text", x.text); put("timestamp", x.timestamp)
        put("edited", x.edited); put("replyToId", x.replyToId); put("replyToText", x.replyToText)
        put("replyToSenderName", x.replyToSenderName); put("seenByRecipient", x.seenByRecipient)
        put("deliveredToRecipient", x.deliveredToRecipient); put("expiresAt", x.expiresAt); put("chatId", x.chatId)
    }

    private fun messageFromJson(x: JSONObject) = CachedMessage(
        messageId = x.optString("messageId"), senderId = x.optString("senderId"), senderName = x.optString("senderName"),
        senderUsername = x.optString("senderUsername"), text = x.optString("text"), timestamp = x.optLong("timestamp"),
        edited = x.optBoolean("edited"), replyToId = x.optNullableString("replyToId"),
        replyToText = x.optNullableString("replyToText"), replyToSenderName = x.optNullableString("replyToSenderName"),
        imageUrl = null, voiceUrl = null, voiceDurationSec = null, remoteVoiceUrl = null, fileUrl = null,
        remoteFileUrl = null, fileName = null, fileMimeType = null, fileSize = null,
        seenByRecipient = x.optBoolean("seenByRecipient"), deliveredToRecipient = x.optBoolean("deliveredToRecipient"),
        expiresAt = x.optLong("expiresAt"), chatId = x.optString("chatId")
    )

    private fun groupJson(x: CachedGroup) = JSONObject().apply {
        put("id", x.id); put("name", x.name); put("membersJson", x.membersJson); put("createdAt", x.createdAt)
        put("lastMessage", x.lastMessage); put("createdBy", x.createdBy)
    }

    private fun groupFromJson(x: JSONObject) = CachedGroup(
        id = x.optString("id"), name = x.optString("name"), profileUrl = "",
        membersJson = x.optString("membersJson", "[]"), createdAt = x.optLong("createdAt"),
        lastMessage = x.optString("lastMessage"), createdBy = x.optString("createdBy")
    )

    private fun groupMessageJson(x: CachedGroupMessage) = JSONObject().apply {
        put("messageId", x.messageId); put("groupId", x.groupId); put("senderId", x.senderId)
        put("senderName", x.senderName); put("text", x.text); put("timestamp", x.timestamp)
        put("replyToId", x.replyToId); put("replyToText", x.replyToText); put("replyToSenderName", x.replyToSenderName)
        put("expiresAt", x.expiresAt)
    }

    private fun groupMessageFromJson(x: JSONObject) = CachedGroupMessage(
        messageId = x.optString("messageId"), groupId = x.optString("groupId"), senderId = x.optString("senderId"),
        senderName = x.optString("senderName"), text = x.optString("text"), timestamp = x.optLong("timestamp"),
        imageUrl = null, voiceUrl = null, voiceDurationSec = null,
        replyToId = x.optNullableString("replyToId"), replyToText = x.optNullableString("replyToText"),
        replyToSenderName = x.optNullableString("replyToSenderName"), expiresAt = x.optLong("expiresAt")
    )

    private fun storyJson(x: CachedStory) = JSONObject().apply {
        put("id", x.id); put("senderId", x.senderId); put("senderName", x.senderName); put("text", x.text)
        put("timestamp", x.timestamp); put("reactionsJson", x.reactionsJson); put("commentsJson", x.commentsJson)
        put("viewersJson", x.viewersJson); put("viewCountsJson", x.viewCountsJson); put("spotlightUntil", x.spotlightUntil)
    }

    private fun storyFromJson(x: JSONObject) = CachedStory(
        id = x.optString("id"), senderId = x.optString("senderId"), senderName = x.optString("senderName"),
        senderProfilePic = "", imageUrl = "", videoUrl = "", text = x.optString("text"), timestamp = x.optLong("timestamp"),
        reactionsJson = x.optString("reactionsJson", "{}"), commentsJson = x.optString("commentsJson", "[]"),
        viewersJson = x.optString("viewersJson", "[]"), viewCountsJson = x.optString("viewCountsJson", "{}"),
        spotlightUntil = x.optLong("spotlightUntil")
    )

    private fun postJson(x: CachedPost) = JSONObject().apply {
        put("id", x.id); put("senderId", x.senderId); put("senderName", x.senderName); put("text", x.text)
        put("timestamp", x.timestamp); put("reactionsJson", x.reactionsJson); put("commentsJson", x.commentsJson)
        put("viewsCount", x.viewsCount); put("isPrivate", x.isPrivate); put("title", x.title)
        put("tagsJson", x.tagsJson); put("taggedUserIdsJson", x.taggedUserIdsJson); put("feeling", x.feeling)
        put("backgroundStyle", x.backgroundStyle); put("textAnimation", x.textAnimation); put("isReel", x.isReel)
        put("expiresAt", x.expiresAt); put("imageUrlsJson", "[]"); put("mediaReactionsJson", x.mediaReactionsJson)
    }

    private fun postFromJson(x: JSONObject) = CachedPost(
        id = x.optString("id"), senderId = x.optString("senderId"), senderName = x.optString("senderName"), senderProfilePic = "",
        text = x.optString("text"), imageUrl = "", audioUrl = "", videoUrl = "", timestamp = x.optLong("timestamp"),
        reactionsJson = x.optString("reactionsJson", "{}"), commentsJson = x.optString("commentsJson", "[]"),
        viewsCount = x.optInt("viewsCount"), isPrivate = x.optBoolean("isPrivate"), title = x.optString("title"),
        tagsJson = x.optString("tagsJson", "[]"), taggedUserIdsJson = x.optString("taggedUserIdsJson", "[]"),
        feeling = x.optString("feeling"), backgroundStyle = x.optString("backgroundStyle", "glass"),
        textAnimation = x.optString("textAnimation", "none"), r2ObjectKeysJson = "[]", isReel = x.optBoolean("isReel"),
        expiresAt = x.optLong("expiresAt"), imageUrlsJson = "[]", mediaReactionsJson = x.optString("mediaReactionsJson", "{}")
    )

    private fun JSONObject.optNullableString(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

    private fun encrypt(plain: ByteArray, uid: String): ByteArray {
        val salt = ByteArray(SALT_BYTES); val iv = ByteArray(IV_BYTES); SecureRandom().nextBytes(salt); SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(uid, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val payload = cipher.doFinal(plain)
        return JSONObject().apply {
            put("magic", MAGIC); put("salt", Base64.encodeToString(salt, Base64.NO_WRAP)); put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(payload, Base64.NO_WRAP))
        }.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun decrypt(bytes: ByteArray, uid: String): ByteArray {
        val envelope = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(envelope.optString("magic") == MAGIC) { "invalid backup" }
        val salt = Base64.decode(envelope.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(uid, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(Base64.decode(envelope.getString("ciphertext"), Base64.DEFAULT))
    }

    private fun deriveKey(uid: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(("ConvoChat|" + uid).toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun writePrivate(context: Context, uid: String, bytes: ByteArray) {
        val dir = File(context.filesDir, "secret_backups").apply { mkdirs() }
        File(dir, "$FILE_PREFIX$uid$FILE_SUFFIX").writeBytes(bytes)
    }

    private fun writePublic(context: Context, uid: String, bytes: ByteArray) {
        val name = "$FILE_PREFIX$uid-${System.currentTimeMillis()}$FILE_SUFFIX"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Convo Chat Backups")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) { context.contentResolver.delete(uri, null, null); throw error }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Convo Chat Backups").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }
    }

    private fun readLatest(context: Context, uid: String): ByteArray? {
        val privateFile = File(context.filesDir, "secret_backups/$FILE_PREFIX$uid$FILE_SUFFIX")
        if (privateFile.exists()) return privateFile.readBytes()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("$FILE_PREFIX$uid-%$FILE_SUFFIX")
            context.contentResolver.query(collection, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    return context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
                }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Convo Chat Backups")
            return dir.listFiles { file -> file.name.startsWith("$FILE_PREFIX$uid-") && file.name.endsWith(FILE_SUFFIX) }
                ?.maxByOrNull { it.lastModified() }?.readBytes()
        }
        return null
    }
}

private fun OutputStream.write(bytes: ByteArray) { write(bytes, 0, bytes.size) }
private fun InputStream.readBytes(): ByteArray = java.io.ByteArrayOutputStream().use { output ->
    copyTo(output)
    output.toByteArray()
}
