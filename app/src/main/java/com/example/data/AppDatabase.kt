package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// Room Database Entities

@Entity(tableName = "cached_users")
data class CachedUser(
    @PrimaryKey val uid: String,
    val name: String,
    val dob: String,
    val username: String,
    val fcmToken: String,
    val profileImageUrl: String,
    val isOnline: Boolean,
    val lastActive: Long,
    val blockedUsersJson: String, // JSON Array of UIDs
    val createdAt: Long
) {
    fun toUser(): User {
        val blockedList = mutableListOf<String>()
        try {
            val array = JSONArray(blockedUsersJson)
            for (i in 0 until array.length()) {
                blockedList.add(array.getString(i))
            }
        } catch (e: Exception) {}
        return User(uid, name, dob, username, fcmToken, profileImageUrl, isOnline, lastActive, blockedList, createdAt)
    }

    companion object {
        fun fromUser(user: User): CachedUser {
            val jsonArray = JSONArray(user.blockedUsers)
            return CachedUser(
                user.uid, user.name, user.dob, user.username, user.fcmToken,
                user.profileImageUrl, user.isOnline, user.lastActive,
                jsonArray.toString(), user.createdAt
            )
        }
    }
}

@Entity(tableName = "cached_messages")
data class CachedMessage(
    @PrimaryKey val messageId: String,
    val senderId: String,
    val senderName: String,
    val senderUsername: String,
    val text: String,
    val timestamp: Long,
    val edited: Boolean,
    val replyToId: String?,
    val replyToText: String?,
    val replyToSenderName: String?,
    val imageUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSec: Int?,
    val seenByRecipient: Boolean,
    val chatId: String // to query messages per conversation
) {
    fun toMessage(): Message {
        return Message(
            messageId, senderId, senderName, senderUsername, text, timestamp,
            edited, replyToId, replyToText, replyToSenderName, imageUrl, voiceUrl, voiceDurationSec, seenByRecipient
        )
    }

    companion object {
        fun fromMessage(msg: Message, chatId: String): CachedMessage {
            return CachedMessage(
                msg.messageId, msg.senderId, msg.senderName, msg.senderUsername,
                msg.text, msg.timestamp, msg.edited, msg.replyToId, msg.replyToText,
                msg.replyToSenderName, msg.imageUrl, msg.voiceUrl, msg.voiceDurationSec, msg.seenByRecipient, chatId
            )
        }
    }
}

@Entity(tableName = "cached_stories")
data class CachedStory(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val senderProfilePic: String,
    val imageUrl: String,
    val videoUrl: String,
    val text: String,
    val timestamp: Long,
    val reactionsJson: String, // Map<String, String> as JSON
    val commentsJson: String   // List<StoryComment> as JSON
) {
    fun toStory(): Story {
        val reactionsMap = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(reactionsJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                reactionsMap[k] = obj.getString(k)
            }
        } catch (e: Exception) {}

        val commentList = mutableListOf<StoryComment>()
        try {
            val array = JSONArray(commentsJson)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                commentList.add(
                    StoryComment(
                        commentId = item.optString("commentId"),
                        senderId = item.optString("senderId"),
                        senderName = item.optString("senderName"),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {}

        return Story(id, senderId, senderName, senderProfilePic, imageUrl, videoUrl, text, timestamp, reactionsMap, commentList)
    }

    companion object {
        fun fromStory(story: Story): CachedStory {
            val reactionsObj = JSONObject()
            story.reactions.forEach { (k, v) -> reactionsObj.put(k, v) }

            val commentsArr = JSONArray()
            story.comments.forEach { c ->
                val o = JSONObject().apply {
                    put("commentId", c.commentId)
                    put("senderId", c.senderId)
                    put("senderName", c.senderName)
                    put("text", c.text)
                    put("timestamp", c.timestamp)
                }
                commentsArr.put(o)
            }

            return CachedStory(
                story.id, story.senderId, story.senderName, story.senderProfilePic,
                story.imageUrl, story.videoUrl, story.text, story.timestamp,
                reactionsObj.toString(), commentsArr.toString()
            )
        }
    }
}

@Entity(tableName = "cached_posts")
data class CachedPost(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val senderProfilePic: String,
    val text: String,
    val imageUrl: String,
    val audioUrl: String,
    val videoUrl: String,
    val timestamp: Long,
    val reactionsJson: String, // Map<String, String> as JSON
    val commentsJson: String,  // List<PostComment> as JSON
    val viewsCount: Int,
    val isPrivate: Boolean
) {
    fun toPost(): Post {
        val reactionsMap = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(reactionsJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                reactionsMap[k] = obj.getString(k)
            }
        } catch (e: Exception) {}

        val commentList = mutableListOf<PostComment>()
        try {
            val array = JSONArray(commentsJson)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                commentList.add(
                    PostComment(
                        commentId = item.optString("commentId"),
                        senderId = item.optString("senderId"),
                        senderName = item.optString("senderName"),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {}

        return Post(
            id, senderId, senderName, senderProfilePic, text, imageUrl, audioUrl, videoUrl,
            timestamp, reactionsMap, commentList, viewsCount, isPrivate
        )
    }

    companion object {
        fun fromPost(post: Post): CachedPost {
            val reactionsObj = JSONObject()
            post.reactions.forEach { (k, v) -> reactionsObj.put(k, v) }

            val commentsArr = JSONArray()
            post.comments.forEach { c ->
                val o = JSONObject().apply {
                    put("commentId", c.commentId)
                    put("senderId", c.senderId)
                    put("senderName", c.senderName)
                    put("text", c.text)
                    put("timestamp", c.timestamp)
                }
                commentsArr.put(o)
            }

            return CachedPost(
                post.id, post.senderId, post.senderName, post.senderProfilePic,
                post.text, post.imageUrl, post.audioUrl, post.videoUrl, post.timestamp,
                reactionsObj.toString(), commentsArr.toString(), post.viewsCount, post.isPrivate
            )
        }
    }
}

@Entity(tableName = "cached_groups")
data class CachedGroup(
    @PrimaryKey val id: String,
    val name: String,
    val profileUrl: String,
    val membersJson: String, // List<String> JSON Array
    val createdAt: Long,
    val lastMessage: String,
    val createdBy: String
) {
    fun toGroup(): Group {
        val membersList = mutableListOf<String>()
        try {
            val array = JSONArray(membersJson)
            for (i in 0 until array.length()) {
                membersList.add(array.getString(i))
            }
        } catch (e: Exception) {}
        return Group(id, name, profileUrl, membersList, createdAt, lastMessage, createdBy)
    }

    companion object {
        fun fromGroup(g: Group): CachedGroup {
            val arr = JSONArray(g.members)
            return CachedGroup(g.id, g.name, g.profileUrl, arr.toString(), g.createdAt, g.lastMessage, g.createdBy)
        }
    }
}

@Entity(tableName = "cached_group_messages")
data class CachedGroupMessage(
    @PrimaryKey val messageId: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val imageUrl: String?,
    val voiceUrl: String?,
    val voiceDurationSec: Int?
) {
    fun toGroupMessage(): GroupMessage {
        return GroupMessage(messageId, groupId, senderId, senderName, text, timestamp, imageUrl, voiceUrl, voiceDurationSec)
    }

    companion object {
        fun fromGroupMessage(gm: GroupMessage): CachedGroupMessage {
            return CachedGroupMessage(
                gm.messageId, gm.groupId, gm.senderId, gm.senderName,
                gm.text, gm.timestamp, gm.imageUrl, gm.voiceUrl, gm.voiceDurationSec
            )
        }
    }
}


// Room DAO

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_users")
    fun getAllUsers(): Flow<List<CachedUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CachedUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<CachedUser>)

    @Query("DELETE FROM cached_users")
    suspend fun clearUsers()


    @Query("SELECT * FROM cached_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<CachedMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: CachedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(msgs: List<CachedMessage>)


    @Query("SELECT * FROM cached_stories ORDER BY timestamp DESC")
    fun getAllStories(): Flow<List<CachedStory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: CachedStory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<CachedStory>)

    @Query("DELETE FROM cached_stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)


    @Query("SELECT * FROM cached_posts ORDER BY timestamp DESC")
    fun getAllPosts(): Flow<List<CachedPost>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CachedPost)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<CachedPost>)

    @Query("DELETE FROM cached_posts WHERE id = :postId")
    suspend fun deletePost(postId: String)


    @Query("SELECT * FROM cached_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<CachedGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CachedGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<CachedGroup>)


    @Query("SELECT * FROM cached_group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getGroupMessages(groupId: String): Flow<List<CachedGroupMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(msg: CachedGroupMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessages(msgs: List<CachedGroupMessage>)
}

// Room Database Definition

@Database(
    entities = [
        CachedUser::class,
        CachedMessage::class,
        CachedStory::class,
        CachedPost::class,
        CachedGroup::class,
        CachedGroupMessage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "firechat_offline_cache_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
