package com.example.ui.lobby

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchHistoryRecord
import com.example.data.model.PlayerProfile
import com.example.data.repository.PixelStrikeRepository
import com.example.game.WeaponCatalog
import com.example.game.WeaponSpec
import com.example.ui.auth.signOutUser
import com.example.ui.theme.AmberStrike
import com.example.ui.theme.BasaltNight
import com.example.ui.theme.CanyonBorder
import com.example.ui.theme.CanyonDark
import com.example.ui.theme.CanyonSurfaceElevated
import com.example.ui.theme.HeadshotGold
import com.example.ui.theme.HealthGreen
import com.example.ui.theme.MilitaryOlive
import com.example.ui.theme.MutedSand
import com.example.ui.theme.SandCream
import com.example.ui.theme.SandstoneGold
import com.example.ui.theme.SquadBlue
import com.example.ui.theme.SquadRed
import com.google.firebase.firestore.FirebaseFirestore

enum class LobbyTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    PLAY("Chiến đấu", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
    ARMORY("Kho súng", Icons.Filled.GpsFixed, Icons.Outlined.GpsFixed),
    LEADERBOARD("Xếp hạng", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    SETTINGS("Cài đặt", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun LobbyScreen(
    currentUserId: String,
    initialDisplayName: String,
    onStartMatch: (PlayerProfile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: LobbyViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val dbId = context.applicationContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                LobbyViewModel(
                    repository = PixelStrikeRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val historyState by viewModel.matchHistoryState.collectAsStateWithLifecycle()
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(LobbyTab.PLAY) }

    BackHandler(enabled = selectedTab != LobbyTab.PLAY) {
        selectedTab = LobbyTab.PLAY
    }

    val activeProfile = (profileState as? UiState.Success)?.data ?: PlayerProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = initialDisplayName.ifBlank { "VoxelSoldier" },
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BasaltNight)) {
        val isWide = maxWidth >= 680.dp

        Image(
            painter = painterResource(id = R.drawable.img_desert_lobby_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xC8141210),
                            Color(0xE0141210),
                            Color(0xF5141210),
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWide) {
                    NavigationBar(
                        containerColor = CanyonDark.copy(alpha = 0.96f),
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        LobbyTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AmberStrike,
                                    selectedTextColor = AmberStrike,
                                    indicatorColor = AmberStrike.copy(alpha = 0.18f),
                                    unselectedIconColor = MutedSand,
                                    unselectedTextColor = MutedSand,
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        containerColor = CanyonDark.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LobbyTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AmberStrike,
                                    selectedTextColor = AmberStrike,
                                    indicatorColor = AmberStrike.copy(alpha = 0.18f),
                                    unselectedIconColor = MutedSand,
                                    unselectedTextColor = MutedSand,
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TopPlayerHeader(
                        profile = activeProfile,
                        onSignOut = {
                            signOutUser(credentialManager, scope)
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedTab) {
                        LobbyTab.PLAY -> PlayTabContent(
                            profile = activeProfile,
                            profileState = profileState,
                            historyState = historyState,
                            onStartMatch = { onStartMatch(activeProfile) },
                        )
                        LobbyTab.ARMORY -> ArmoryTabContent()
                        LobbyTab.LEADERBOARD -> LeaderboardTabContent(
                            currentUserId = currentUserId,
                            leaderboardState = leaderboardState,
                        )
                        LobbyTab.SETTINGS -> SettingsTabContent(
                            profile = activeProfile,
                            saveStatus = saveStatus,
                            onSave = { name, sens, sound ->
                                viewModel.saveSettings(name, sens, sound)
                            },
                            onDismissStatus = { viewModel.clearSaveStatus() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPlayerHeader(
    profile: PlayerProfile,
    onSignOut: () -> Unit,
) {
    Surface(
        color = CanyonDark.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    color = AmberStrike.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AmberStrike),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MilitaryTech,
                            contentDescription = "Huy hiệu hạng",
                            tint = AmberStrike,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = SandCream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${profile.rankTier} • ${profile.rankPoints} RP",
                        style = MaterialTheme.typography.labelSmall,
                        color = SandstoneGold,
                    )
                }
            }

            IconButton(
                onClick = onSignOut,
                modifier = Modifier.testTag("sign_out_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Đăng xuất Google",
                    tint = MutedSand,
                )
            }
        }
    }
}

@Composable
private fun PlayTabContent(
    profile: PlayerProfile,
    profileState: UiState<PlayerProfile?>,
    historyState: UiState<List<MatchHistoryRecord>>,
    onStartMatch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = CanyonDark.copy(alpha = 0.92f),
            border = BorderStroke(1.5.dp, AmberStrike),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = MilitaryOlive,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "TỬ CHIẾN 4V4 • BO 7",
                                style = MaterialTheme.typography.labelSmall,
                                color = SandCream,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "HẺM NÚI SA MẠC VOXEL",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SandCream,
                        )
                        Text(
                            text = "Mua súng theo hiệp • Phá bom keo • Đấu Bot AI chiến thuật",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSand,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val kd = if (profile.totalDeaths == 0L) {
                        profile.totalKills.toDouble()
                    } else {
                        profile.totalKills.toDouble() / profile.totalDeaths.toDouble()
                    }
                    StatPill("TRẬN THẮNG", "${profile.wins}/${profile.matchesPlayed}", Modifier.weight(1f))
                    StatPill("K/D", String.format("%.2f", kd), Modifier.weight(1f))
                    StatPill("HEADSHOT", "${profile.totalHeadshots}", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onStartMatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberStrike,
                        contentColor = Color(0xFF1A0F00),
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_match_button"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BẮT ĐẦU TỬ CHIẾN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (profileState is UiState.Error) {
            Text(
                text = profileState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = SquadRed,
            )
        }

        Text(
            text = "LỊCH SỬ ĐẤU GẦN ĐÂY",
            style = MaterialTheme.typography.labelLarge,
            color = SandstoneGold,
        )

        when (historyState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberStrike)
                }
            }
            is UiState.Error -> {
                Text(
                    text = historyState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SquadRed,
                )
            }
            is UiState.Success -> {
                val records = historyState.data
                if (records.isEmpty()) {
                    Surface(
                        color = CanyonDark.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, CanyonBorder),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = SandstoneGold,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Chưa có trận Tử Chiến nào. Bấm BẮT ĐẦU TỬ CHIẾN để ghi danh bảng xếp hạng!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSand,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(records, key = { it.id }) { match ->
                            MatchHistoryCard(match)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = BasaltNight.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSand)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = SandCream)
        }
    }
}

@Composable
private fun MatchHistoryCard(record: MatchHistoryRecord) {
    val accent = if (record.won) HealthGreen else SquadRed
    Surface(
        color = CanyonDark.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = if (record.won) "BOOYAH! CHIẾN THẮNG" else "THẤT BẠI",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
                Text(
                    text = "Tỷ số ${record.playerTeamScore} - ${record.enemyTeamScore} • ${record.kills}/${record.deaths} K/D • ${record.headshots} HS",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SandCream,
                )
            }
            val sign = if (record.rankDelta >= 0) "+" else ""
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = "$sign${record.rankDelta} RP",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ArmoryTabContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(WeaponCatalog.allWeapons, key = { it.id }) { weapon ->
            WeaponSpecCard(weapon)
        }
    }
}

@Composable
private fun WeaponSpecCard(weapon: WeaponSpec) {
    Surface(
        color = CanyonDark.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = weapon.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = SandCream,
                    )
                    Text(
                        text = "${weapon.category.labelVi} • Băng đạn ${weapon.magSize}/${weapon.reserveAmmo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSand,
                    )
                }
                Surface(
                    color = AmberStrike.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AmberStrike),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = if (weapon.cost == 0) "MẶC ĐỊNH" else "$${weapon.cost}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberStrike,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            StatBarRow("Sát thương", ((weapon.damage * weapon.pellets) / 120f).coerceIn(0.1f, 1f), AmberStrike)
            Spacer(modifier = Modifier.height(6.dp))
            val fireRateNorm = (1f - (weapon.fireIntervalSec / 1.2f)).coerceIn(0.15f, 1f)
            StatBarRow("Tốc độ bắn", fireRateNorm, SquadBlue)
            Spacer(modifier = Modifier.height(6.dp))
            val accNorm = (1f - (weapon.adsSpreadDeg / 6f)).coerceIn(0.2f, 1f)
            StatBarRow("Độ chính xác", accNorm, HealthGreen)
        }
    }
}

@Composable
private fun StatBarRow(label: String, progress: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedSand,
            modifier = Modifier.width(108.dp),
        )
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = BasaltNight,
            modifier = Modifier.weight(1f).height(8.dp),
        )
    }
}

@Composable
private fun LeaderboardTabContent(
    currentUserId: String,
    leaderboardState: UiState<List<LeaderboardEntry>>,
) {
    when (leaderboardState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AmberStrike)
            }
        }
        is UiState.Error -> {
            Text(
                text = leaderboardState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = SquadRed,
            )
        }
        is UiState.Success -> {
            val entries = leaderboardState.data
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Chưa có chiến binh nào trên Bảng Xếp Hạng.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MutedSand,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(entries, key = { _, item -> item.userId }) { index, entry ->
                        val isMe = entry.userId == currentUserId
                        val rankColor = when (index) {
                            0 -> HeadshotGold
                            1 -> SandCream
                            2 -> AmberStrike
                            else -> MutedSand
                        }
                        Surface(
                            color = if (isMe) CanyonSurfaceElevated else CanyonDark.copy(alpha = 0.9f),
                            border = BorderStroke(
                                width = if (isMe) 1.5.dp else 1.dp,
                                color = if (isMe) AmberStrike else CanyonBorder,
                            ),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = rankColor,
                                    modifier = Modifier.width(38.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isMe) "${entry.displayName} (Bạn)" else entry.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SandCream,
                                    )
                                    Text(
                                        text = "${entry.rankTier} • ${entry.wins} Thắng • ${entry.totalKills} Hạ gục",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSand,
                                    )
                                }
                                Text(
                                    text = "${entry.rankPoints} RP",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberStrike,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    profile: PlayerProfile,
    saveStatus: String?,
    onSave: (String, Double, Boolean) -> Unit,
    onDismissStatus: () -> Unit,
) {
    var nameInput by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var sensitivity by remember(profile.sensitivity) { mutableFloatStateOf(profile.sensitivity.toFloat()) }
    var soundEnabled by remember(profile.soundEnabled) { mutableStateOf(profile.soundEnabled) }

    LaunchedEffect(saveStatus) {
        if (saveStatus != null && !saveStatus.startsWith("Đang")) {
            kotlinx.coroutines.delay(2500L)
            onDismissStatus()
        }
    }

    Surface(
        color = CanyonDark.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "CÀI ĐẶT CHIẾN BINH",
                style = MaterialTheme.typography.titleLarge,
                color = SandCream,
            )

            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 30) nameInput = it },
                label = { Text("Biệt danh chiến trường") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Độ nhạy xoay tâm (Sensitivity)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SandCream,
                    )
                    Text(
                        text = String.format("%.2fx", sensitivity),
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberStrike,
                    )
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.3f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberStrike,
                        activeTrackColor = AmberStrike,
                    ),
                    modifier = Modifier.testTag("settings_sensitivity_slider"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Âm thanh súng & Hiệu ứng",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SandCream,
                    )
                    Text(
                        text = "Tiếng nổ, nạp đạn và âm báo Headshot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSand,
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = AmberStrike),
                    modifier = Modifier.testTag("settings_sound_switch"),
                )
            }

            if (saveStatus != null) {
                Text(
                    text = saveStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saveStatus.startsWith("Lỗi")) SquadRed else HealthGreen,
                )
            }

            Button(
                onClick = { onSave(nameInput, sensitivity.toDouble(), soundEnabled) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberStrike,
                    contentColor = Color(0xFF1A0F00),
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LƯU CÀI ĐẶT",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
