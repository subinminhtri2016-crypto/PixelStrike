package com.example.ui.match

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.PlayerProfile
import com.example.data.repository.PixelStrikeRepository
import com.example.game.MatchHudState
import com.example.game.RoundPhase
import com.example.game.TacticalItem
import com.example.game.Team
import com.example.game.VoxelGLSurfaceView
import com.example.game.VoxelGameEngine
import com.example.game.WeaponCatalog
import com.example.game.WeaponCategory
import com.example.ui.theme.AmberStrike
import com.example.ui.theme.ArmorCyan
import com.example.ui.theme.BasaltNight
import com.example.ui.theme.CanyonBorder
import com.example.ui.theme.CanyonDark
import com.example.ui.theme.CanyonSurfaceElevated
import com.example.ui.theme.HeadshotGold
import com.example.ui.theme.HealthGreen
import com.example.ui.theme.MutedSand
import com.example.ui.theme.SandCream
import com.example.ui.theme.SandstoneGold
import com.example.ui.theme.SquadBlue
import com.example.ui.theme.SquadRed
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun MatchScreen(
    currentUserId: String,
    profile: PlayerProfile,
    onExitToLobby: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: MatchViewModel = viewModel(
        key = "${currentUserId}_match",
        factory = viewModelFactory {
            initializer {
                val app = context.applicationContext
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                MatchViewModel(
                    appContext = app,
                    repository = PixelStrikeRepository(db),
                    profile = profile,
                )
            }
        }
    )

    var matchGeneration by remember { mutableIntStateOf(0) }
    val engine = remember(matchGeneration) { viewModel.engine }
    val hud by engine.hudState.collectAsStateWithLifecycle()
    val rankDelta by viewModel.rankDeltaState.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hud.phase) {
        if (hud.phase == RoundPhase.MATCH_END) {
            viewModel.onMatchEnded(hud)
        }
    }

    BackHandler {
        if (hud.phase == RoundPhase.MATCH_END) {
            onExitToLobby()
        } else {
            showExitDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BasaltNight)) {
        // 3D OpenGL ES Voxel World
        key(matchGeneration) {
            VoxelGLSurfaceView(
                engine = engine,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Sniper ADS Scope Vignette
        if (hud.isAds && hud.activeWeapon.category == WeaponCategory.SNIPER && hud.isAlive) {
            SniperScopeOverlay()
        }

        // Damage Red Vignette
        if (hud.damageFlashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SquadRed.copy(alpha = (hud.damageFlashAlpha * 0.35f).coerceIn(0f, 0.4f)))
            )
        }

        // Touch Input Layer (Left side empty area + Right side Look Drag)
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.45f).fillMaxHeight())
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .pointerInput(engine) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            engine.addLookDelta(-dragAmount.x * 0.24f, dragAmount.y * 0.19f)
                        }
                    }
            )
        }

        // Crosshair & Hitmarker in exact screen center
        if (hud.isAlive && hud.phase != RoundPhase.MATCH_END && !hud.shopOpen) {
            CenterCrosshairLayer(hud)
        }

        // Safe-Area HUD Controls Overlay
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            val isCompactWidth = maxWidth < 600.dp

            // TOP BAR: Radar + Round Scoreboard + Kill Feed & Exit
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Top-Left: Mini Radar & Shop Button
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TacticalRadar(hud = hud, sizeDp = if (isCompactWidth) 74 else 88)
                    if (hud.phase == RoundPhase.BUY_PHASE && !hud.shopOpen) {
                        Button(
                            onClick = { engine.setShopOpen(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberStrike,
                                contentColor = Color(0xFF1A0F00),
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.height(48.dp).testTag("open_shop_button"),
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("MUA SÚNG ($${hud.cash})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Top-Center: Clash Squad Scoreboard
                RoundScoreboardBanner(hud)

                // Top-Right: Exit + Kill Feed
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        color = CanyonDark.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, CanyonBorder),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        IconButton(
                            onClick = { showExitDialog = true },
                            modifier = Modifier.size(48.dp).testTag("match_exit_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Thoát trận",
                                tint = SandCream,
                            )
                        }
                    }

                    hud.killFeed.take(3).forEach { kf ->
                        Surface(
                            color = BasaltNight.copy(alpha = 0.75f),
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = kf.killerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kf.killerTeam == Team.BLUE) SquadBlue else SquadRed,
                                )
                                Text(
                                    text = if (kf.isHeadshot) "[HS ${kf.weaponName}]" else "[${kf.weaponName}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kf.isHeadshot) HeadshotGold else MutedSand,
                                )
                                Text(
                                    text = kf.victimName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kf.victimTeam == Team.BLUE) SquadBlue else SquadRed,
                                )
                            }
                        }
                    }
                }
            }

            // Spectate Notice when player eliminated in current round
            if (!hud.isAlive && hud.phase == RoundPhase.COMBAT_PHASE) {
                Surface(
                    color = CanyonDark.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, SquadRed),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = "ĐÃ BỊ HẠ • CHỜ HIỆP TIẾP THEO...",
                        style = MaterialTheme.typography.titleMedium,
                        color = SquadRed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // Round End Banner
            if (hud.phase == RoundPhase.ROUND_END && hud.roundBannerText != null) {
                val color = if (hud.roundBannerIsWin) HealthGreen else SquadRed
                Surface(
                    color = BasaltNight.copy(alpha = 0.9f),
                    border = BorderStroke(2.dp, color),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = hud.roundBannerText.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = color,
                        )
                        Text(
                            text = "Tỷ số hiện tại: ${hud.blueScore} - ${hud.redScore}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SandCream,
                        )
                    }
                }
            }

            // BOTTOM CONTROLS: Joystick + HP/Armor + Weapon Bar + Combat Buttons
            if (hud.phase != RoundPhase.MATCH_END && !hud.shopOpen) {
                // Bottom-Left: Virtual Joystick + Medkit/Gloo/HP
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TacticalHudPillButton(
                            icon = Icons.Filled.LocalHospital,
                            label = if (hud.isHealing) "${(hud.healProgress * 100).toInt()}%" else "HỒI (${hud.medkits})",
                            accent = HealthGreen,
                            enabled = hud.medkits > 0 && hud.hp < hud.maxHp && hud.phase == RoundPhase.COMBAT_PHASE,
                            onClick = { engine.useMedkit() },
                            testTag = "medkit_button",
                        )
                        TacticalHudPillButton(
                            icon = Icons.Filled.AddBox,
                            label = "KEO (${hud.glooBlocks})",
                            accent = ArmorCyan,
                            enabled = hud.glooBlocks > 0 && hud.isAlive,
                            onClick = { engine.deployGlooBlock() },
                            testTag = "gloo_button",
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        VirtualJoystick(
                            sizeDp = if (isCompactWidth) 108 else 124,
                            onMove = { x, y -> engine.setMoveInput(x, y) },
                        )
                        if (!isCompactWidth) {
                            HealthArmorPanel(hud)
                        }
                    }
                }

                // Bottom-Center: Compact HP (if narrow) + Weapon Switcher
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isCompactWidth) {
                        HealthArmorPanel(hud)
                    }
                    WeaponSlotBar(
                        hud = hud,
                        onSelectSlot = { slot -> engine.switchSlot(slot) },
                    )
                }

                // Bottom-Right: Fire, ADS, Jump, Reload Cluster
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CombatIconButton(
                            icon = Icons.Filled.CenterFocusStrong,
                            contentDesc = "Ngắm (ADS)",
                            active = hud.isAds,
                            onClick = { engine.toggleAds() },
                            testTag = "ads_button",
                        )
                        CombatIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDesc = "Nạp đạn",
                            active = hud.isReloading,
                            onClick = { engine.triggerReload() },
                            testTag = "reload_button",
                        )
                        CombatIconButton(
                            icon = Icons.Filled.ArrowUpward,
                            contentDesc = "Nhảy",
                            active = false,
                            onClick = { engine.triggerJump() },
                            testTag = "jump_button",
                        )
                    }

                    // Large Free Fire-style Shoot Button (hold + drag to aim while firing!)
                    Surface(
                        color = AmberStrike.copy(alpha = 0.88f),
                        border = BorderStroke(2.dp, HeadshotGold),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(if (isCompactWidth) 74.dp else 84.dp)
                            .testTag("fire_button")
                            .pointerInput(engine) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    engine.setFiring(true)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val active = event.changes.firstOrNull { it.pressed } ?: break
                                            val delta = active.position - active.previousPosition
                                            if (delta != Offset.Zero) {
                                                engine.addLookDelta(-delta.x * 0.2f, delta.y * 0.16f)
                                                active.consume()
                                            }
                                        }
                                    } finally {
                                        engine.setFiring(false)
                                    }
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.GpsFixed,
                                contentDescription = "Bắn",
                                tint = Color(0xFF1A0F00),
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                }
            }

            // BUY SHOP OVERLAY
            if (hud.phase == RoundPhase.BUY_PHASE && hud.shopOpen) {
                BuyShopOverlay(
                    hud = hud,
                    onBuyWeapon = { engine.buyWeapon(it) },
                    onBuyItem = { engine.buyTacticalItem(it) },
                    onCloseShop = { engine.setShopOpen(false) },
                    onStartRoundNow = { engine.skipBuyPhase() },
                )
            }

            // MATCH END SUMMARY OVERLAY
            if (hud.phase == RoundPhase.MATCH_END) {
                MatchEndOverlay(
                    hud = hud,
                    rankDelta = rankDelta,
                    syncError = syncError,
                    onPlayAgain = {
                        viewModel.restartMatch()
                        matchGeneration++
                    },
                    onExitToLobby = onExitToLobby,
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = CanyonDark,
            title = { Text("Thoát trận Tử Chiến?", color = SandCream) },
            text = {
                Text(
                    "Nếu thoát giữa trận, kết quả hiện tại sẽ không được ghi nhận vào điểm Rank.",
                    color = MutedSand,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onExitToLobby()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SquadRed),
                ) {
                    Text("Thoát về sảnh", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Tiếp tục chiến đấu", color = AmberStrike)
                }
            },
        )
    }
}

@Composable
private fun SniperScopeOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.42f
        drawRect(color = Color(0xCC000000))
        drawCircle(
            color = Color.Transparent,
            radius = radius,
            center = Offset(cx, cy),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
        )
        drawCircle(
            color = AmberStrike,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 4f),
        )
        drawLine(Color.Black, Offset(cx - radius, cy), Offset(cx + radius, cy), strokeWidth = 2.5f)
        drawLine(Color.Black, Offset(cx, cy - radius), Offset(cx, cy + radius), strokeWidth = 2.5f)
    }
}

@Composable
private fun CenterCrosshairLayer(hud: MatchHudState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val gap = (8f + hud.crosshairSpread * 2.6f).coerceIn(6f, 24f)
            val len = 9f
            val chColor = if (hud.isAds) HeadshotGold else Color.White

            drawLine(chColor, Offset(cx - gap - len, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
            drawLine(chColor, Offset(cx + gap, cy), Offset(cx + gap + len, cy), strokeWidth = 2.5f)
            drawLine(chColor, Offset(cx, cy - gap - len), Offset(cx, cy - gap), strokeWidth = 2.5f)
            drawLine(chColor, Offset(cx, cy + gap), Offset(cx, cy + gap + len), strokeWidth = 2.5f)
            drawCircle(chColor, radius = 2.2f, center = Offset(cx, cy))

            if (hud.hitMarkerAlpha > 0.02f) {
                val hmColor = (if (hud.hitMarkerHeadshot) HeadshotGold else Color.White).copy(alpha = hud.hitMarkerAlpha)
                val d1 = 6f
                val d2 = 15f
                drawLine(hmColor, Offset(cx - d2, cy - d2), Offset(cx - d1, cy - d1), strokeWidth = 3.5f)
                drawLine(hmColor, Offset(cx + d1, cy + d1), Offset(cx + d2, cy + d2), strokeWidth = 3.5f)
                drawLine(hmColor, Offset(cx + d2, cy - d2), Offset(cx + d1, cy - d1), strokeWidth = 3.5f)
                drawLine(hmColor, Offset(cx - d1, cy + d1), Offset(cx - d2, cy + d2), strokeWidth = 3.5f)
            }
        }

        if (hud.lastDamagePopup != null) {
            Text(
                text = hud.lastDamagePopup.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = if (hud.lastDamagePopupHeadshot) HeadshotGold else SandCream,
                modifier = Modifier.padding(bottom = 52.dp),
            )
        }
    }
}

@Composable
private fun RoundScoreboardBanner(hud: MatchHudState) {
    Surface(
        color = CanyonDark.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "XANH ${hud.blueAlive}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SquadBlue,
                )
                Text(
                    text = "${hud.blueScore} : ${hud.redScore}",
                    style = MaterialTheme.typography.titleLarge,
                    color = SandCream,
                )
                Text(
                    text = "${hud.redAlive} ĐỎ",
                    style = MaterialTheme.typography.labelSmall,
                    color = SquadRed,
                )
            }
            val phaseText = when (hud.phase) {
                RoundPhase.BUY_PHASE -> "MUA SÚNG • ${hud.phaseSecondsLeft}s"
                RoundPhase.COMBAT_PHASE -> "HIỆP ${hud.roundNumber} • ${hud.phaseSecondsLeft}s"
                RoundPhase.ROUND_END -> "KẾT THÚC HIỆP"
                RoundPhase.MATCH_END -> "HOÀN TẤT"
            }
            Text(
                text = phaseText,
                style = MaterialTheme.typography.labelSmall,
                color = AmberStrike,
            )
        }
    }
}

@Composable
private fun TacticalRadar(hud: MatchHudState, sizeDp: Int) {
    Surface(
        color = BasaltNight.copy(alpha = 0.82f),
        border = BorderStroke(1.5.dp, SandstoneGold),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(sizeDp.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height
            // Map world (32 x 44) into radar
            hud.radarDots.forEach { dot ->
                if (!dot.isAlive) return@forEach
                val rx = (dot.x / 32f) * w
                val ry = (1f - dot.z / 44f) * h
                when {
                    dot.isPlayer -> {
                        val yawRad = Math.toRadians(hud.playerYawDeg.toDouble())
                        val fx = sin(yawRad).toFloat()
                        val fy = -cos(yawRad).toFloat()
                        val path = Path().apply {
                            moveTo(rx + fx * 7f, ry + fy * 7f)
                            lineTo(rx - fy * 4.5f - fx * 4f, ry + fx * 4.5f - fy * 4f)
                            lineTo(rx + fy * 4.5f - fx * 4f, ry - fx * 4.5f - fy * 4f)
                            close()
                        }
                        drawPath(path, color = AmberStrike)
                    }
                    dot.team == Team.BLUE -> {
                        drawCircle(SquadBlue, radius = 3.5f, center = Offset(rx, ry))
                    }
                    else -> {
                        drawCircle(SquadRed, radius = 3.2f, center = Offset(rx, ry))
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    sizeDp: Int,
    onMove: (Float, Float) -> Unit,
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val sizeVal = sizeDp.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizeVal)
            .clip(CircleShape)
            .background(CanyonDark.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                val radius = size.width / 2f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(radius, radius)
                    fun updateFrom(pos: Offset) {
                        val delta = pos - center
                        val dist = hypot(delta.x, delta.y)
                        val maxR = radius * 0.72f
                        val clamped = if (dist > maxR && dist > 0f) delta * (maxR / dist) else delta
                        knobOffset = clamped
                        onMove((clamped.x / maxR).coerceIn(-1f, 1f), (-clamped.y / maxR).coerceIn(-1f, 1f))
                    }
                    updateFrom(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.id == down.id && it.pressed } ?: break
                            updateFrom(active.position)
                            active.consume()
                        }
                    } finally {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = SandstoneGold.copy(alpha = 0.45f),
                radius = size.minDimension / 2f - 2f,
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = AmberStrike.copy(alpha = 0.85f),
                radius = size.minDimension * 0.21f,
                center = center + knobOffset,
            )
        }
    }
}

@Composable
private fun HealthArmorPanel(hud: MatchHudState) {
    Surface(
        color = BasaltNight.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.width(165.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("HP ${hud.hp}/${hud.maxHp}", style = MaterialTheme.typography.labelSmall, color = SandCream)
                Text("GIÁP ${hud.armor}", style = MaterialTheme.typography.labelSmall, color = ArmorCyan)
            }
            LinearProgressIndicator(
                progress = { (hud.hp.toFloat() / hud.maxHp).coerceIn(0f, 1f) },
                color = if (hud.hp > 60) HealthGreen else SquadRed,
                trackColor = CanyonDark,
                modifier = Modifier.fillMaxWidth().height(7.dp),
            )
            LinearProgressIndicator(
                progress = { (hud.armor.toFloat() / hud.maxArmor).coerceIn(0f, 1f) },
                color = ArmorCyan,
                trackColor = CanyonDark,
                modifier = Modifier.fillMaxWidth().height(5.dp),
            )
        }
    }
}

@Composable
private fun WeaponSlotBar(
    hud: MatchHudState,
    onSelectSlot: (Int) -> Unit,
) {
    Surface(
        color = BasaltNight.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, CanyonBorder),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (hud.primaryWeapon != null) {
                WeaponSlotChip(
                    title = hud.primaryWeapon.name,
                    ammo = "${hud.primaryAmmo}/${hud.primaryReserve}",
                    selected = hud.activeSlot == 0,
                    onClick = { onSelectSlot(0) },
                    testTag = "slot_primary",
                )
            }
            WeaponSlotChip(
                title = hud.secondaryWeapon.name,
                ammo = "${hud.secondaryAmmo}/${hud.secondaryReserve}",
                selected = hud.activeSlot == 1,
                onClick = { onSelectSlot(1) },
                testTag = "slot_secondary",
            )
            WeaponSlotChip(
                title = "Dao",
                ammo = "∞",
                selected = hud.activeSlot == 2,
                onClick = { onSelectSlot(2) },
                testTag = "slot_melee",
            )
        }
    }
}

@Composable
private fun WeaponSlotChip(
    title: String,
    ammo: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        color = if (selected) AmberStrike.copy(alpha = 0.24f) else CanyonDark,
        border = BorderStroke(1.dp, if (selected) AmberStrike else CanyonBorder),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AmberStrike else SandCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ammo,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSand,
            )
        }
    }
}

@Composable
private fun TacticalHudPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        color = if (enabled) CanyonDark.copy(alpha = 0.9f) else BasaltNight.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, if (enabled) accent else CanyonBorder),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) accent else MutedSand, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (enabled) SandCream else MutedSand)
        }
    }
}

@Composable
private fun CombatIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        color = if (active) AmberStrike else CanyonDark.copy(alpha = 0.84f),
        border = BorderStroke(1.5.dp, if (active) HeadshotGold else SandstoneGold),
        shape = CircleShape,
        modifier = Modifier.size(48.dp).testTag(testTag),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = if (active) Color(0xFF1A0F00) else SandCream,
            )
        }
    }
}

@Composable
private fun BuyShopOverlay(
    hud: MatchHudState,
    onBuyWeapon: (com.example.game.WeaponSpec) -> Unit,
    onBuyItem: (TacticalItem) -> Unit,
    onCloseShop: () -> Unit,
    onStartRoundNow: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB80E0C0A)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = CanyonDark,
            border = BorderStroke(1.5.dp, AmberStrike),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "CỬA HÀNG TỬ CHIẾN • HIỆP ${hud.roundNumber}",
                            style = MaterialTheme.typography.titleLarge,
                            color = SandCream,
                        )
                        Text(
                            text = "Ngân sách: $${hud.cash} • Còn ${hud.phaseSecondsLeft}s",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberStrike,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartRoundNow,
                            colors = ButtonDefaults.buttonColors(containerColor = HealthGreen),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.height(48.dp).testTag("skip_buy_button"),
                        ) {
                            Text("VÀO TRẬN", style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                        IconButton(
                            onClick = onCloseShop,
                            modifier = Modifier.size(48.dp).testTag("close_shop_button"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Đóng cửa hàng", tint = SandCream)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text("VŨ KHÍ", style = MaterialTheme.typography.labelLarge, color = SandstoneGold)
                    }
                    items(WeaponCatalog.shopWeapons, key = { it.id }) { weapon ->
                        val owned = hud.primaryWeapon?.id == weapon.id || hud.secondaryWeapon.id == weapon.id
                        val affordable = hud.cash >= weapon.cost
                        Surface(
                            color = CanyonSurfaceElevated,
                            border = BorderStroke(1.dp, if (owned) HealthGreen else CanyonBorder),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = affordable) { onBuyWeapon(weapon) }
                                .testTag("buy_weapon_${weapon.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${weapon.name} (${weapon.category.labelVi})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SandCream,
                                    )
                                    Text(
                                        text = "Sát thương: ${weapon.damage * weapon.pellets} • Đạn: ${weapon.magSize}/${weapon.reserveAmmo}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSand,
                                    )
                                }
                                Text(
                                    text = if (owned) "NẠP ĐẠN" else "$${weapon.cost}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = when {
                                        owned -> HealthGreen
                                        affordable -> AmberStrike
                                        else -> SquadRed
                                    },
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("GIÁP & VẬT PHẨM CHIẾN THUẬT", style = MaterialTheme.typography.labelLarge, color = SandstoneGold)
                    }

                    items(TacticalItem.entries, key = { it.id }) { item ->
                        val affordable = hud.cash >= item.cost
                        Surface(
                            color = CanyonSurfaceElevated,
                            border = BorderStroke(1.dp, CanyonBorder),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = affordable) { onBuyItem(item) }
                                .testTag("buy_item_${item.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Shield, contentDescription = null, tint = ArmorCyan, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = SandCream)
                                        Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MutedSand)
                                    }
                                }
                                Text(
                                    text = "$${item.cost}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (affordable) AmberStrike else SquadRed,
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
private fun MatchEndOverlay(
    hud: MatchHudState,
    rankDelta: Long?,
    syncError: String?,
    onPlayAgain: () -> Unit,
    onExitToLobby: () -> Unit,
) {
    val won = hud.blueScore > hud.redScore
    val accent = if (won) HeadshotGold else SquadRed

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD8100E0C)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = CanyonDark,
            border = BorderStroke(2.dp, accent),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(0.95f).fillMaxHeight(0.88f),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (won) "BOOYAH! CHIẾN THẮNG" else "THẤT BẠI",
                    style = MaterialTheme.typography.displayLarge,
                    color = accent,
                )
                Text(
                    text = "Tỷ số chung cuộc: Đội Xanh ${hud.blueScore} - ${hud.redScore} Đội Đỏ",
                    style = MaterialTheme.typography.titleMedium,
                    color = SandCream,
                )
                if (rankDelta != null) {
                    val sign = if (rankDelta >= 0) "+" else ""
                    Text(
                        text = "Điểm Rank: $sign$rankDelta RP (Đã đồng bộ Cloud)",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rankDelta >= 0) HealthGreen else SquadRed,
                    )
                } else if (syncError != null) {
                    Text(text = syncError, style = MaterialTheme.typography.bodyMedium, color = SquadRed)
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(hud.scoreboard, key = { it.id }) { row ->
                        val teamColor = if (row.team == Team.BLUE) SquadBlue else SquadRed
                        Surface(
                            color = if (row.isPlayer) CanyonSurfaceElevated else BasaltNight.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, if (row.isPlayer) AmberStrike else CanyonBorder),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (row.isPlayer) "${row.name} (Bạn)" else row.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = teamColor,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${row.kills}K / ${row.deaths}D • ${row.headshots}HS • ${row.damageDealt} DMG",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SandCream,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onExitToLobby,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("match_end_lobby_button"),
                    ) {
                        Text("VỀ SẢNH", color = SandCream, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onPlayAgain,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberStrike, contentColor = Color(0xFF1A0F00)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f).height(50.dp).testTag("match_end_replay_button"),
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("ĐẤU LẠI", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
