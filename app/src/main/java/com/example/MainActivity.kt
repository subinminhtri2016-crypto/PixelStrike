package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.model.PlayerProfile
import com.example.ui.auth.AuthScreen
import com.example.ui.lobby.LobbyScreen
import com.example.ui.match.MatchScreen
import com.example.ui.theme.PixelStrikeTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelStrikeTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            var activeMatchProfile by remember { mutableStateOf<PlayerProfile?>(null) }
            val matchProfile = activeMatchProfile
            if (matchProfile == null) {
                LobbyScreen(
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "VoxelSoldier",
                    onStartMatch = { profile -> activeMatchProfile = profile },
                )
            } else {
                MatchScreen(
                    currentUserId = user.uid,
                    profile = matchProfile,
                    onExitToLobby = { activeMatchProfile = null },
                )
            }
        }
    }
}
