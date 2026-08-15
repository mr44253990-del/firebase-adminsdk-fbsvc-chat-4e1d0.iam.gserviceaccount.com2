package com.example.data

data class RememberedAccount(
    val uid:String="",
    val name:String="",
    val email:String="",
    val photoUrl:String="",
    val provider:String="password",
    val lastUsedAt:Long=0L,
    val unread:Int=0
)
