package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AuthScreen
import com.example.ui.ChatScreen
import com.example.ui.ChatViewModel
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.data.User

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val activeRecipient by viewModel.activeRecipientUser.collectAsState()

                // Check starting destination depending on whether a user is already signed in
                val startDestination = remember {
                    try {
                        if (FirebaseAuth.getInstance().currentUser != null) {
                            "home"
                        } else {
                            "auth"
                        }
                    } catch (e: Exception) {
                        "auth"
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("auth") {
                            AuthScreen(
                                viewModel = viewModel,
                                onAuthSuccess = {
                                    navController.navigate("home") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onUserSelected = { recipient ->
                                    viewModel.selectRecipient(recipient)
                                    navController.navigate("chat")
                                },
                                onSignOut = {
                                    navController.navigate("auth") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("chat") {
                            activeRecipient?.let { recipient ->
                                ChatScreen(
                                    viewModel = viewModel,
                                    recipient = recipient,
                                    onBack = {
                                        viewModel.selectRecipient(null)
                                        navController.popBackStack()
                                    }
                                )
                            } ?: run {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    }
                }

                // Process deep link if launched via push notification click
                LaunchedEffect(intent) {
                    val senderId = intent?.getStringExtra("senderId")
                    if (!senderId.isNullOrBlank()) {
                        // Fetch recipient user profile from Firestore and open chat
                        FirebaseFirestore.getInstance().collection("users")
                            .document(senderId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val recipient = document.toObject(User::class.java)
                                    if (recipient != null) {
                                        viewModel.selectRecipient(recipient)
                                        navController.navigate("chat") {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}
