package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.ui.theme.AmberStrike
import com.example.ui.theme.BasaltNight
import com.example.ui.theme.CanyonBorder
import com.example.ui.theme.CanyonDark
import com.example.ui.theme.MutedSand
import com.example.ui.theme.SandCream
import com.example.ui.theme.SandstoneGold
import com.example.ui.theme.SquadRed

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var isAutoChecking by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAutoChecking = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = scope,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BasaltNight)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_desert_lobby_bg),
            contentDescription = "Bối cảnh sa mạc hẻm núi Voxel",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xB8141210),
                            Color(0xD9141210),
                            Color(0xF5141210),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                color = CanyonDark.copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, AmberStrike),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = AmberStrike.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, AmberStrike),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "3D VOXEL FPS • TỬ CHIẾN 4V4",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberStrike,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "PIXEL STRIKE",
                        style = MaterialTheme.typography.displayLarge,
                        color = SandCream,
                    )
                    Text(
                        text = "VOXEL CLASH • SA MẠC HẺM NÚI",
                        style = MaterialTheme.typography.titleMedium,
                        color = SandstoneGold,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FeatureRow(
                            icon = Icons.Filled.GpsFixed,
                            text = "Góc nhìn thứ nhất 3D khối vuông • Mua súng theo hiệp",
                        )
                        FeatureRow(
                            icon = Icons.Filled.CloudDone,
                            text = "Đồng bộ hồ sơ chiến binh & lịch sử đấu trên đám mây",
                        )
                        FeatureRow(
                            icon = Icons.Filled.Leaderboard,
                            text = "Đua Top Bảng Xếp Hạng Tử Chiến Toàn Cầu",
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = SquadRed.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, SquadRed),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SandCream,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isLoading = false
                                    onAuthSuccess()
                                },
                                onAuthError = { msg ->
                                    isLoading = false
                                    errorMessage = msg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isLoading = false
                                },
                            )
                        },
                        enabled = !isLoading && !isAutoChecking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberStrike,
                            contentColor = Color(0xFF1A0F00),
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button"),
                    ) {
                        if (isLoading || isAutoChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF1A0F00),
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isAutoChecking) "ĐANG KIỂM TRA PHIÊN..." else "ĐANG ĐĂNG NHẬP...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Login,
                                contentDescription = "Đăng nhập Google",
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ĐĂNG NHẬP BẰNG GOOGLE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Sử dụng tài khoản Google để bảo toàn Rank & Bảng xếp hạng.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSand,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(BasaltNight.copy(alpha = 0.65f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AmberStrike,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = SandCream,
        )
    }
}
