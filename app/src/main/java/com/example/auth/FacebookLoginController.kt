package com.example.auth

import android.app.Activity
import android.content.Intent
import com.example.BuildConfig
import com.facebook.*
import com.facebook.login.LoginManager

object FacebookLoginController {
    val callbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }

    fun login(activity: Activity, onToken: (String) -> Unit, onError: (String) -> Unit) {
        val appId = BuildConfig.FACEBOOK_APP_ID.trim()
        val clientToken = BuildConfig.FACEBOOK_CLIENT_TOKEN.trim()
        if (appId.isBlank() || appId == "0" || clientToken.isBlank() || clientToken == "not_configured") {
            onError("Facebook SDK is not configured. Add FACEBOOK_APP_ID and FACEBOOK_CLIENT_TOKEN to GitHub Actions secrets/local environment.")
            return
        }
        try {
            if (!FacebookSdk.isInitialized()) {
                FacebookSdk.setApplicationId(appId)
                FacebookSdk.setClientToken(clientToken)
                @Suppress("DEPRECATION") FacebookSdk.sdkInitialize(activity.applicationContext)
            }
            LoginManager.getInstance().unregisterCallback(callbackManager)
            LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<com.facebook.login.LoginResult> {
                override fun onSuccess(result: com.facebook.login.LoginResult) = onToken(result.accessToken.token)
                override fun onCancel() = onError("Facebook login was cancelled")
                override fun onError(error: FacebookException) = onError(error.localizedMessage ?: "Facebook login failed")
            })
            LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email", "public_profile"))
        } catch (error: Throwable) {
            onError("Facebook SDK configuration error: ${error.localizedMessage ?: error.javaClass.simpleName}")
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean = callbackManager.onActivityResult(requestCode, resultCode, data)
}
