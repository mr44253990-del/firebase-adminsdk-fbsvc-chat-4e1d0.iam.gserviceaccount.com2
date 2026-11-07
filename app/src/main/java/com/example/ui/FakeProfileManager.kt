package com.example.ui

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

/**
 * Admin Fake Profile system — no limit, each with unique picture, customizable followers/flowers.
 * Used by Admin control center to seed fake users for demo / growth.
 */
object FakeProfileManager {

    data class FakeUserSpec(
        val name: String,
        val bio: String = "",
        val flowers: Int = Random.nextInt(5, 500),
        val followers: Int = Random.nextInt(10, 5000),
        val avatarUrl: String = "https://i.pravatar.cc/300?u=$name-${UUID.randomUUID()}",
        val premium: Boolean = false
    )

    private val presetNames = listOf(
        "Aisha Rahman", "Rafi Ahmed", "Nusrat Jahan", "Tanvir Khan", "Maya Sen", "Arjun Das",
        "Sadia Afroz", "Imran Hossain", "Zara Malik", "Kamal Uddin", "Lina Chowdhury", "Fahim Islam"
    )

    private val presetAvatars = listOf(
        "https://i.pravatar.cc/300?img=1", "https://i.pravatar.cc/300?img=5",
        "https://i.pravatar.cc/300?img=8", "https://i.pravatar.cc/300?img=11",
        "https://i.pravatar.cc/300?img=15", "https://i.pravatar.cc/300?img=20",
        "https://i.pravatar.cc/300?img=26", "https://i.pravatar.cc/300?img=32",
        "https://i.pravatar.cc/300?img=44", "https://i.pravatar.cc/300?img=47"
    )

    suspend fun createFakeUsers(count: Int, customFlowers: Int? = null): List<String> {
        val db = FirebaseFirestore.getInstance()
        val ids = mutableListOf<String>()
        repeat(count.coerceIn(1, 50)) {
            val name = presetNames.random() + " " + Random.nextInt(100, 999)
            val spec = FakeUserSpec(
                name = name,
                flowers = customFlowers ?: Random.nextInt(10, 1000),
                followers = Random.nextInt(5, 2000),
                avatarUrl = presetAvatars.random() + "?t=${System.currentTimeMillis()}-${it}"
            )
            val uid = "fake_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val data = mapOf(
                "uid" to uid,
                "name" to spec.name,
                "displayName" to spec.name,
                "bio" to "Hello from ${spec.name} — Convo Chat family!",
                "profileImageUrl" to spec.avatarUrl,
                "photoUrl" to spec.avatarUrl,
                "email" to "${uid}@fake.convo.local",
                "isFake" to true,
                "fakeFlowers" to spec.flowers,
                "followersCount" to spec.followers,
                "followingCount" to Random.nextInt(5, 100),
                "createdAt" to FieldValue.serverTimestamp(),
                "role" to "user",
                "isPremium" to spec.premium,
                "verified" to false
            )
            db.collection("users").document(uid).set(data).await()
            ids.add(uid)
        }
        return ids
    }

    suspend fun createFakePosts(fakeUid: String, count: Int = 1, aiText: String? = null): List<String> {
        val db = FirebaseFirestore.getInstance()
        val postIds = mutableListOf<String>()
        val prompts = listOf(
            "Beautiful day in Dhaka!", "Loving Convo Chat premium features",
            "Check my new photo", "Who wants to chat?", "Good morning everyone"
        )
        repeat(count.coerceIn(1, 10)) {
            val text = aiText ?: prompts.random()
            val ref = db.collection("posts").document()
            val data = mapOf(
                "id" to ref.id,
                "senderId" to fakeUid,
                "text" to text,
                "content" to text,
                "timestamp" to System.currentTimeMillis(),
                "createdAt" to FieldValue.serverTimestamp(),
                "likes" to emptyList<String>(),
                "isFake" to true
            )
            ref.set(data).await()
            postIds.add(ref.id)
        }
        return postIds
    }
}
