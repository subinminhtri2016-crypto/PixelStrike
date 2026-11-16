package com.example.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class Team { BLUE, RED }

enum class RoundPhase {
    BUY_PHASE,
    COMBAT_PHASE,
    ROUND_END,
    MATCH_END,
}

data class KillFeedItem(
    val id: Long,
    val killerName: String,
    val killerTeam: Team,
    val victimName: String,
    val victimTeam: Team,
    val weaponName: String,
    val isHeadshot: Boolean,
    val expiresAtMs: Long,
)

data class CombatantSnapshot(
    val id: String,
    val name: String,
    val team: Team,
    val isPlayer: Boolean,
    val x: Float,
    val y: Float,
    val z: Float,
    val yawDeg: Float,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val isAlive: Boolean,
    val walkPhase: Float,
    val hitFlash: Float,
    val muzzleFlash: Float,
    val kills: Int,
    val deaths: Int,
    val headshots: Int,
    val damageDealt: Int,
    val weaponColor: Int,
)

data class TracerFx(
    val x1: Float,
    val y1: Float,
    val z1: Float,
    val x2: Float,
    val y2: Float,
    val z2: Float,
    val isGold: Boolean,
    val life: Float,
)

data class RenderSceneSnapshot(
    val worldVersion: Int,
    val cameraX: Float,
    val cameraY: Float,
    val cameraZ: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val fovDeg: Float,
    val activeWeapon: WeaponSpec,
    val recoilKick: Float,
    val reloadProgress: Float,
    val playerMuzzleFlash: Float,
    val playerWalkBob: Float,
    val combatants: List<CombatantSnapshot>,
    val tracers: List<TracerFx>,
)

data class MatchHudState(
    val roundNumber: Int = 1,
    val blueScore: Int = 0,
    val redScore: Int = 0,
    val phase: RoundPhase = RoundPhase.BUY_PHASE,
    val phaseSecondsLeft: Int = 10,
    val cash: Int = 800,
    val hp: Int = 200,
    val maxHp: Int = 200,
    val armor: Int = 0,
    val maxArmor: Int = 130,
    val medkits: Int = 1,
    val glooBlocks: Int = 1,
    val isHealing: Boolean = false,
    val healProgress: Float = 0f,
    val isAds: Boolean = false,
    val isReloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val activeSlot: Int = 1, // 0: Primary, 1: Secondary, 2: Melee
    val primaryWeapon: WeaponSpec? = null,
    val primaryAmmo: Int = 0,
    val primaryReserve: Int = 0,
    val secondaryWeapon: WeaponSpec = WeaponCatalog.G18,
    val secondaryAmmo: Int = WeaponCatalog.G18.magSize,
    val secondaryReserve: Int = WeaponCatalog.G18.reserveAmmo,
    val activeWeapon: WeaponSpec = WeaponCatalog.G18,
    val blueAlive: Int = 4,
    val redAlive: Int = 4,
    val playerKills: Int = 0,
    val playerDeaths: Int = 0,
    val playerHeadshots: Int = 0,
    val playerDamage: Int = 0,
    val crosshairSpread: Float = 0f,
    val hitMarkerAlpha: Float = 0f,
    val hitMarkerHeadshot: Boolean = false,
    val lastDamagePopup: String? = null,
    val lastDamagePopupHeadshot: Boolean = false,
    val damageFlashAlpha: Float = 0f,
    val damageIndicatorAngleDeg: Float? = null,
    val roundBannerText: String? = null,
    val roundBannerIsWin: Boolean = true,
    val killFeed: List<KillFeedItem> = emptyList(),
    val scoreboard: List<CombatantSnapshot> = emptyList(),
    val radarDots: List<CombatantSnapshot> = emptyList(),
    val playerX: Float = 15.5f,
    val playerZ: Float = 6.5f,
    val playerYawDeg: Float = 0f,
    val shopOpen: Boolean = true,
    val isAlive: Boolean = true,
)

private class MutableCombatant(
    val id: String,
    val name: String,
    val team: Team,
    val isPlayer: Boolean,
) {
    var x = 15.5f
    var y = 1.0f
    var z = 6.5f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var yawDeg = 0f
    var pitchDeg = 0f
    var onGround = false

    var hp = 200
    val maxHp = 200
    var armor = 0
    var isAlive = true

    var primaryWeapon: WeaponSpec? = null
    var primaryAmmo = 0
    var primaryReserve = 0
    var secondaryWeapon: WeaponSpec = WeaponCatalog.G18
    var secondaryAmmo = WeaponCatalog.G18.magSize
    var secondaryReserve = WeaponCatalog.G18.reserveAmmo
    var activeSlot = 1 // 0 primary, 1 secondary, 2 melee

    var fireCooldown = 0f
    var reloadTimer = 0f
    var isReloading = false
    var recoilKick = 0f
    var walkPhase = 0f
    var hitFlash = 0f
    var muzzleFlash = 0f

    var kills = 0
    var deaths = 0
    var headshots = 0
    var damageDealt = 0

    // Bot AI state
    var aiTargetWaypoint: Triple<Float, Float, Float>? = null
    var aiStrafeDir = if (Random.nextBoolean()) 1f else -1f
    var aiStrafeTimer = 1.2f
    var aiReactionTimer = 0f

    fun activeWeapon(): WeaponSpec = when (activeSlot) {
        0 -> primaryWeapon ?: secondaryWeapon
        2 -> WeaponCatalog.KNIFE
        else -> secondaryWeapon
    }

    fun currentAmmo(): Int = when (activeSlot) {
        0 -> primaryAmmo
        2 -> 1
        else -> secondaryAmmo
    }

    fun currentReserve(): Int = when (activeSlot) {
        0 -> primaryReserve
        2 -> 0
        else -> secondaryReserve
    }
}

class VoxelGameEngine(
    private val playerName: String,
    private val soundEngine: SoundSynthEngine,
) {
    val world = VoxelWorld()

    private val _hudState = MutableStateFlow(MatchHudState(shopOpen = true))
    val hudState: StateFlow<MatchHudState> = _hudState.asStateFlow()

    @Volatile
    var touchSensitivity: Float = 1.0f

    @Volatile
    private var moveInputX = 0f

    @Volatile
    private var moveInputY = 0f

    @Volatile
    private var firingHeld = false

    @Volatile
    private var jumpQueued = false

    @Volatile
    private var adsEnabled = false

    private val stateLock = Any()

    private val combatants = mutableListOf<MutableCombatant>()
    private val tracers = mutableListOf<TracerFx>()
    private val killFeed = mutableListOf<KillFeedItem>()
    private var killFeedSeq = 1L

    private var roundNumber = 1
    private var blueScore = 0
    private var redScore = 0
    private var phase = RoundPhase.BUY_PHASE
    private var phaseTimer = BUY_DURATION_SEC
    private var cash = 800
    private var medkits = 1
    private var glooBlocks = 1
    private var isHealing = false
    private var healTimer = 0f
    private var shopOpen = true

    private var hitMarkerTimer = 0f
    private var hitMarkerHeadshot = false
    private var lastDamagePopup: String? = null
    private var lastDamagePopupHeadshot = false
    private var damagePopupTimer = 0f
    private var damageFlashAlpha = 0f
    private var damageIndicatorAngleDeg: Float? = null
    private var damageIndicatorTimer = 0f
    private var roundBannerText: String? = null
    private var roundBannerIsWin = true
    private var hudSyncAccumulator = 0f

    @Volatile
    private var cachedSnapshot: RenderSceneSnapshot

    init {
        combatants += MutableCombatant("player", playerName.ifBlank { "Bạn" }, Team.BLUE, true)
        combatants += MutableCombatant("b1", "Ghost-01", Team.BLUE, false)
        combatants += MutableCombatant("b2", "Viper-02", Team.BLUE, false)
        combatants += MutableCombatant("b3", "Atlas-03", Team.BLUE, false)

        combatants += MutableCombatant("r1", "Scorpion", Team.RED, false)
        combatants += MutableCombatant("r2", "Jackal", Team.RED, false)
        combatants += MutableCombatant("r3", "Mirage", Team.RED, false)
        combatants += MutableCombatant("r4", "Cobra", Team.RED, false)

        setupRound(isFirstRound = true)
        cachedSnapshot = buildRenderSnapshotLocked()
        publishHudLocked()
    }

    fun getRenderSnapshot(): RenderSceneSnapshot = cachedSnapshot

    fun setMoveInput(x: Float, y: Float) {
        moveInputX = x.coerceIn(-1f, 1f)
        moveInputY = y.coerceIn(-1f, 1f)
    }

    fun addLookDelta(deltaYawDeg: Float, deltaPitchDeg: Float) {
        synchronized(stateLock) {
            val player = combatants.first()
            val adsScale = if (adsEnabled) 0.55f else 1.0f
            val sens = touchSensitivity * adsScale
            player.yawDeg = (player.yawDeg + deltaYawDeg * sens) % 360f
            player.pitchDeg = (player.pitchDeg + deltaPitchDeg * sens).coerceIn(-75f, 75f)
        }
    }

    fun setFiring(held: Boolean) {
        firingHeld = held
    }

    fun triggerJump() {
        jumpQueued = true
    }

    fun toggleAds() {
        synchronized(stateLock) {
            val player = combatants.first()
            if (player.activeSlot != 2) {
                adsEnabled = !adsEnabled
            } else {
                adsEnabled = false
            }
            publishHudLocked()
        }
    }

    fun switchSlot(slot: Int) {
        synchronized(stateLock) {
            val player = combatants.first()
            if (!player.isAlive) return
            if (slot == 0 && player.primaryWeapon == null) return
            player.activeSlot = slot.coerceIn(0, 2)
            player.isReloading = false
            player.reloadTimer = 0f
            if (slot == 2) adsEnabled = false
            publishHudLocked()
        }
    }

    fun triggerReload() {
        synchronized(stateLock) {
            val player = combatants.first()
            startReloadLocked(player)
            publishHudLocked()
        }
    }

    fun useMedkit() {
        synchronized(stateLock) {
            val player = combatants.first()
            if (!player.isAlive || phase != RoundPhase.COMBAT_PHASE) return
            if (medkits <= 0 || player.hp >= player.maxHp || isHealing) return
            isHealing = true
            healTimer = MEDKIT_DURATION_SEC
            publishHudLocked()
        }
    }

    fun deployGlooBlock() {
        synchronized(stateLock) {
            val player = combatants.first()
            if (!player.isAlive || phase == RoundPhase.ROUND_END || phase == RoundPhase.MATCH_END) return
            if (glooBlocks <= 0) return
            if (world.placeGlooWall(player.x, player.y, player.z, player.yawDeg)) {
                glooBlocks--
                soundEngine.playGlooPlace()
                publishHudLocked()
            }
        }
    }

    fun setShopOpen(open: Boolean) {
        synchronized(stateLock) {
            shopOpen = open && phase == RoundPhase.BUY_PHASE
            publishHudLocked()
        }
    }

    fun skipBuyPhase() {
        synchronized(stateLock) {
            if (phase == RoundPhase.BUY_PHASE) {
                phase = RoundPhase.COMBAT_PHASE
                phaseTimer = COMBAT_DURATION_SEC
                shopOpen = false
                publishHudLocked()
            }
        }
    }

    fun buyWeapon(weapon: WeaponSpec) {
        synchronized(stateLock) {
            if (phase != RoundPhase.BUY_PHASE) return
            val player = combatants.first()
            if (cash < weapon.cost) return
            if (weapon.isPrimary && player.primaryWeapon?.id == weapon.id) {
                // Refill ammo if already owned
                player.primaryAmmo = weapon.magSize
                player.primaryReserve = weapon.reserveAmmo
                publishHudLocked()
                return
            }
            cash -= weapon.cost
            if (weapon.isPrimary) {
                player.primaryWeapon = weapon
                player.primaryAmmo = weapon.magSize
                player.primaryReserve = weapon.reserveAmmo
                player.activeSlot = 0
            } else if (weapon.isSecondary) {
                player.secondaryWeapon = weapon
                player.secondaryAmmo = weapon.magSize
                player.secondaryReserve = weapon.reserveAmmo
                player.activeSlot = 1
            }
            soundEngine.playReload()
            publishHudLocked()
        }
    }

    fun buyTacticalItem(item: TacticalItem) {
        synchronized(stateLock) {
            if (phase != RoundPhase.BUY_PHASE) return
            val player = combatants.first()
            if (cash < item.cost) return
            when (item) {
                TacticalItem.ARMOR_LV1 -> {
                    if (player.armor >= 50) return
                    cash -= item.cost
                    player.armor = 50
                }
                TacticalItem.ARMOR_LV2 -> {
                    if (player.armor >= 90) return
                    cash -= item.cost
                    player.armor = 90
                }
                TacticalItem.ARMOR_LV3 -> {
                    if (player.armor >= 130) return
                    cash -= item.cost
                    player.armor = 130
                }
                TacticalItem.MEDKIT -> {
                    if (medkits >= 3) return
                    cash -= item.cost
                    medkits++
                }
                TacticalItem.GLOO_BLOCK -> {
                    if (glooBlocks >= 3) return
                    cash -= item.cost
                    glooBlocks++
                }
            }
            soundEngine.playReload()
            publishHudLocked()
        }
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        synchronized(stateLock) {
            updateTimersLocked(dt)
            if (phase == RoundPhase.BUY_PHASE || phase == RoundPhase.COMBAT_PHASE) {
                updatePlayerLocked(dt)
                updateBotsLocked(dt)
            }
            updateCombatantEffectsLocked(dt)
            updateTracersLocked(dt)
            checkRoundCompletionLocked()

            cachedSnapshot = buildRenderSnapshotLocked()
            hudSyncAccumulator += dt
            if (hudSyncAccumulator >= 0.05f) {
                hudSyncAccumulator = 0f
                publishHudLocked()
            }
        }
    }

    private fun setupRound(isFirstRound: Boolean) {
        world.clearGlooBlocks()
        tracers.clear()
        phase = RoundPhase.BUY_PHASE
        phaseTimer = BUY_DURATION_SEC
        shopOpen = true
        roundBannerText = null
        isHealing = false
        healTimer = 0f
        adsEnabled = false

        val blue = combatants.filter { it.team == Team.BLUE }
        val red = combatants.filter { it.team == Team.RED }

        blue.forEachIndexed { idx, c ->
            val sp = world.blueSpawns[idx % world.blueSpawns.size]
            val survivedPrev = !isFirstRound && c.isAlive
            c.x = sp.first
            c.y = sp.second
            c.z = sp.third
            c.vx = 0f
            c.vy = 0f
            c.vz = 0f
            c.yawDeg = 0f // Looking +Z toward North Red base (cos/sin convention below: yaw 0 looks +Z)
            c.pitchDeg = 0f
            c.hp = c.maxHp
            c.isAlive = true
            c.isReloading = false
            c.reloadTimer = 0f
            c.fireCooldown = 0f
            c.hitFlash = 0f
            c.muzzleFlash = 0f

            if (!survivedPrev) {
                c.armor = 0
                if (c.isPlayer) {
                    c.primaryWeapon = null
                    c.primaryAmmo = 0
                    c.primaryReserve = 0
                    c.secondaryWeapon = WeaponCatalog.G18
                    c.secondaryAmmo = WeaponCatalog.G18.magSize
                    c.secondaryReserve = WeaponCatalog.G18.reserveAmmo
                    c.activeSlot = 1
                } else {
                    assignBotLoadout(c, roundNumber)
                }
            } else {
                // Surviving player keeps primary & refills reserve ammo
                c.primaryWeapon?.let {
                    c.primaryAmmo = it.magSize
                    c.primaryReserve = it.reserveAmmo
                }
                c.secondaryAmmo = c.secondaryWeapon.magSize
                c.secondaryReserve = c.secondaryWeapon.reserveAmmo
            }
            c.aiTargetWaypoint = world.tacticalWaypoints.random()
        }

        red.forEachIndexed { idx, c ->
            val sp = world.redSpawns[idx % world.redSpawns.size]
            c.x = sp.first
            c.y = sp.second
            c.z = sp.third
            c.vx = 0f
            c.vy = 0f
            c.vz = 0f
            c.yawDeg = 180f // Facing South toward Blue base
            c.pitchDeg = 0f
            c.hp = c.maxHp
            c.isAlive = true
            c.isReloading = false
            c.reloadTimer = 0f
            c.fireCooldown = 0f
            c.hitFlash = 0f
            c.muzzleFlash = 0f
            assignBotLoadout(c, roundNumber)
            c.aiTargetWaypoint = world.tacticalWaypoints.random()
        }
    }

    private fun assignBotLoadout(bot: MutableCombatant, round: Int) {
        val weapon = when {
            round == 1 -> if (Random.nextFloat() < 0.45f) WeaponCatalog.DESERT_EAGLE else WeaponCatalog.G18
            round == 2 -> listOf(WeaponCatalog.MP5, WeaponCatalog.M1014, WeaponCatalog.DESERT_EAGLE).random()
            else -> listOf(WeaponCatalog.AK47, WeaponCatalog.MP5, WeaponCatalog.M1014, WeaponCatalog.AWM).random()
        }
        if (weapon.isPrimary) {
            bot.primaryWeapon = weapon
            bot.primaryAmmo = weapon.magSize
            bot.primaryReserve = weapon.reserveAmmo
            bot.activeSlot = 0
        } else {
            bot.primaryWeapon = null
            bot.secondaryWeapon = weapon
            bot.secondaryAmmo = weapon.magSize
            bot.secondaryReserve = weapon.reserveAmmo
            bot.activeSlot = 1
        }
        bot.armor = when {
            round >= 4 -> 90
            round >= 2 -> 50
            else -> 0
        }
    }

    private fun updateTimersLocked(dt: Float) {
        phaseTimer -= dt
        when (phase) {
            RoundPhase.BUY_PHASE -> {
                if (phaseTimer <= 0f) {
                    phase = RoundPhase.COMBAT_PHASE
                    phaseTimer = COMBAT_DURATION_SEC
                    shopOpen = false
                }
            }
            RoundPhase.COMBAT_PHASE -> {
                if (phaseTimer <= 0f) {
                    // Time expired: team with more alive members (or higher total HP) wins the round
                    val blueAliveList = combatants.filter { it.team == Team.BLUE && it.isAlive }
                    val redAliveList = combatants.filter { it.team == Team.RED && it.isAlive }
                    val blueWins = if (blueAliveList.size != redAliveList.size) {
                        blueAliveList.size > redAliveList.size
                    } else {
                        blueAliveList.sumOf { it.hp } >= redAliveList.sumOf { it.hp }
                    }
                    endRoundLocked(blueWins)
                }
            }
            RoundPhase.ROUND_END -> {
                if (phaseTimer <= 0f) {
                    if (blueScore >= WINS_TO_WIN_MATCH || redScore >= WINS_TO_WIN_MATCH) {
                        phase = RoundPhase.MATCH_END
                        phaseTimer = 0f
                    } else {
                        roundNumber++
                        setupRound(isFirstRound = false)
                    }
                }
            }
            RoundPhase.MATCH_END -> Unit
        }

        if (hitMarkerTimer > 0f) hitMarkerTimer = max(0f, hitMarkerTimer - dt)
        if (damagePopupTimer > 0f) {
            damagePopupTimer = max(0f, damagePopupTimer - dt)
            if (damagePopupTimer <= 0f) lastDamagePopup = null
        }
        if (damageFlashAlpha > 0f) damageFlashAlpha = max(0f, damageFlashAlpha - dt * 1.8f)
        if (damageIndicatorTimer > 0f) {
            damageIndicatorTimer = max(0f, damageIndicatorTimer - dt)
            if (damageIndicatorTimer <= 0f) damageIndicatorAngleDeg = null
        }
    }

    private fun updatePlayerLocked(dt: Float) {
        val player = combatants.first()
        if (!player.isAlive) return

        // Update healing channel
        if (isHealing) {
            healTimer -= dt
            if (healTimer <= 0f) {
                isHealing = false
                medkits = max(0, medkits - 1)
                player.hp = min(player.maxHp, player.hp + 75)
            }
        }

        // Yaw convention: yaw=0 -> +Z (North), yaw=90 -> -X (West) or +X?
        // Using standard OpenGL camera look: forward = (sin(yaw), 0, cos(yaw))?
        // Let's keep a single consistent convention:
        // forward = (-sin(yawRad), 0, cos(yawRad)) or (sin(yawRad), 0, cos(yawRad)).
        // Let's use forward = (sin(yawRad), 0, cos(yawRad)), right = (-cos(yawRad), 0, sin(yawRad)).
        // Check cross: forward x up = (sin, 0, cos) x (0, 1, 0) = (-cos, 0, sin)! Exact right-handed basis!
        val yawRad = Math.toRadians(player.yawDeg.toDouble())
        val fx = sin(yawRad).toFloat()
        val fz = cos(yawRad).toFloat()
        val rx = -cos(yawRad).toFloat()
        val rz = sin(yawRad).toFloat()

        val speed = when {
            isHealing -> 2.1f
            adsEnabled -> 3.2f
            player.activeSlot == 2 -> 6.0f
            else -> 5.2f
        }
        val wishX = rx * moveInputX + fx * moveInputY
        val wishZ = rz * moveInputX + fz * moveInputY
        val wishLen = sqrt(wishX * wishX + wishZ * wishZ)
        if (wishLen > 0.05f) {
            val scale = min(1f, wishLen) * speed / wishLen
            player.vx = wishX * scale
            player.vz = wishZ * scale
            player.walkPhase += dt * speed * 2.1f
        } else {
            player.vx = 0f
            player.vz = 0f
        }

        if (jumpQueued) {
            jumpQueued = false
            if (player.onGround) {
                player.vy = JUMP_VELOCITY
                player.onGround = false
            }
        }

        moveWithCollisionLocked(player, dt, restrictToSpawn = (phase == RoundPhase.BUY_PHASE))

        // Reload & Fire
        updateWeaponTimersLocked(player, dt)
        if (phase == RoundPhase.COMBAT_PHASE && firingHeld && !shopOpen) {
            tryFireLocked(player)
        }
    }

    private fun updateBotsLocked(dt: Float) {
        for (bot in combatants) {
            if (bot.isPlayer || !bot.isAlive) continue
            updateWeaponTimersLocked(bot, dt)

            if (phase == RoundPhase.BUY_PHASE) {
                // Idle subtle look around in spawn during buy phase
                bot.vx = 0f
                bot.vz = 0f
                moveWithCollisionLocked(bot, dt, restrictToSpawn = true)
                continue
            }

            // Find closest visible enemy
            val enemies = combatants.filter { it.team != bot.team && it.isAlive }
            var bestEnemy: MutableCombatant? = null
            var bestDist = Float.MAX_VALUE
            var canSeeBest = false

            for (e in enemies) {
                val dx = e.x - bot.x
                val dy = (e.y + 1.35f) - (bot.y + 1.45f)
                val dz = e.z - bot.z
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                val visible = hasLineOfSight(bot.x, bot.y + 1.45f, bot.z, e.x, e.y + 1.35f, e.z)
                val scoreDist = if (visible) d * 0.45f else d
                if (scoreDist < bestDist) {
                    bestDist = scoreDist
                    bestEnemy = e
                    canSeeBest = visible
                }
            }

            bot.aiStrafeTimer -= dt
            if (bot.aiStrafeTimer <= 0f) {
                bot.aiStrafeTimer = Random.nextFloat() * 1.1f + 0.6f
                bot.aiStrafeDir = if (Random.nextBoolean()) 1f else -1f
            }

            if (bestEnemy != null && canSeeBest) {
                val dx = bestEnemy.x - bot.x
                val dy = (bestEnemy.y + 1.32f) - (bot.y + 1.52f)
                val dz = bestEnemy.z - bot.z
                val horiz = max(0.01f, sqrt(dx * dx + dz * dz))
                val targetYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                val targetPitch = -Math.toDegrees(atan2(dy.toDouble(), horiz.toDouble())).toFloat()

                bot.yawDeg = lerpAngle(bot.yawDeg, targetYaw, dt * 8.5f)
                bot.pitchDeg = (bot.pitchDeg + (targetPitch - bot.pitchDeg) * min(1f, dt * 8.5f)).coerceIn(-65f, 65f)

                // Strafe while engaging; approach if far
                val yawRad = Math.toRadians(bot.yawDeg.toDouble())
                val fx = sin(yawRad).toFloat()
                val fz = cos(yawRad).toFloat()
                val rx = -cos(yawRad).toFloat()
                val rz = sin(yawRad).toFloat()

                val forwardFactor = when {
                    horiz > 14f -> 0.7f
                    horiz < 5f -> -0.5f
                    else -> 0.0f
                }
                val strafeSpeed = 3.5f
                bot.vx = (fx * forwardFactor + rx * bot.aiStrafeDir * 0.75f) * strafeSpeed
                bot.vz = (fz * forwardFactor + rz * bot.aiStrafeDir * 0.75f) * strafeSpeed
                bot.walkPhase += dt * 7f

                bot.aiReactionTimer += dt
                if (bot.aiReactionTimer > 0.22f) {
                    tryFireLocked(bot)
                }
            } else {
                bot.aiReactionTimer = 0f
                val wp = bot.aiTargetWaypoint ?: world.tacticalWaypoints.random().also { bot.aiTargetWaypoint = it }
                val dx = wp.first - bot.x
                val dz = wp.third - bot.z
                val dist = sqrt(dx * dx + dz * dz)
                if (dist < 1.5f) {
                    bot.aiTargetWaypoint = if (bestEnemy != null && Random.nextFloat() < 0.6f) {
                        Triple(bestEnemy.x, bestEnemy.y, bestEnemy.z)
                    } else {
                        world.tacticalWaypoints.random()
                    }
                } else {
                    val nx = dx / dist
                    val nz = dz / dist
                    val speed = 4.0f
                    bot.vx = nx * speed
                    bot.vz = nz * speed
                    val targetYaw = Math.toDegrees(atan2(nx.toDouble(), nz.toDouble())).toFloat()
                    bot.yawDeg = lerpAngle(bot.yawDeg, targetYaw, dt * 6f)
                    bot.pitchDeg *= (1f - min(1f, dt * 5f))
                    bot.walkPhase += dt * 8f

                    // Jump if blocked by a low 1-block obstacle ahead
                    val probeX = floor((bot.x + nx * 0.8f).toDouble()).toInt()
                    val probeZ = floor((bot.z + nz * 0.8f).toDouble()).toInt()
                    val footY = floor(bot.y.toDouble()).toInt()
                    if (world.isSolid(probeX, footY, probeZ) && !world.isSolid(probeX, footY + 1, probeZ) && bot.onGround) {
                        bot.vy = JUMP_VELOCITY
                        bot.onGround = false
                    }
                }
            }

            moveWithCollisionLocked(bot, dt, restrictToSpawn = false)
        }
    }

    private fun moveWithCollisionLocked(c: MutableCombatant, dt: Float, restrictToSpawn: Boolean) {
        val halfW = 0.34f
        val height = 1.76f

        // Gravity
        c.vy -= GRAVITY * dt

        // Move X
        val nextX = c.x + c.vx * dt
        if (!collidesWorld(nextX, c.y, c.z, halfW, height)) {
            c.x = nextX
        } else {
            c.vx = 0f
        }

        // Move Z
        val nextZ = c.z + c.vz * dt
        val spawnBlocked = restrictToSpawn && ((c.team == Team.BLUE && nextZ > 11.2f) || (c.team == Team.RED && nextZ < 32.8f))
        if (!spawnBlocked && !collidesWorld(c.x, c.y, nextZ, halfW, height)) {
            c.z = nextZ
        } else {
            c.vz = 0f
        }

        // Move Y
        val nextY = c.y + c.vy * dt
        if (!collidesWorld(c.x, nextY, c.z, halfW, height)) {
            c.y = nextY
            c.onGround = false
        } else {
            if (c.vy < 0f) {
                // Snap cleanly to block top
                c.y = floor(c.y.toDouble()).toFloat()
                c.onGround = true
            }
            c.vy = 0f
        }

        // Keep inside arena bounds
        c.x = c.x.coerceIn(2.4f, world.sizeX - 2.4f)
        c.z = c.z.coerceIn(2.4f, world.sizeZ - 2.4f)
        if (c.y < 1.0f) {
            c.y = 1.0f
            c.vy = 0f
            c.onGround = true
        }
    }

    private fun collidesWorld(x: Float, y: Float, z: Float, halfW: Float, height: Float): Boolean {
        val minX = floor((x - halfW).toDouble()).toInt()
        val maxX = floor((x + halfW).toDouble()).toInt()
        val minY = floor((y + 0.02f).toDouble()).toInt()
        val maxY = floor((y + height - 0.02f).toDouble()).toInt()
        val minZ = floor((z - halfW).toDouble()).toInt()
        val maxZ = floor((z + halfW).toDouble()).toInt()

        for (by in minY..maxY) {
            for (bz in minZ..maxZ) {
                for (bx in minX..maxX) {
                    if (world.isSolid(bx, by, bz)) return true
                }
            }
        }
        return false
    }

    private fun updateWeaponTimersLocked(c: MutableCombatant, dt: Float) {
        if (c.fireCooldown > 0f) c.fireCooldown = max(0f, c.fireCooldown - dt)
        if (c.recoilKick > 0f) c.recoilKick = max(0f, c.recoilKick - dt * 6.5f)
        if (c.isReloading) {
            c.reloadTimer -= dt
            if (c.reloadTimer <= 0f) {
                finishReloadLocked(c)
            }
        }
    }

    private fun startReloadLocked(c: MutableCombatant) {
        if (c.isReloading || c.activeSlot == 2) return
        val w = c.activeWeapon()
        if (c.currentAmmo() >= w.magSize || c.currentReserve() <= 0) return
        c.isReloading = true
        c.reloadTimer = w.reloadTimeSec
        if (c.isPlayer) soundEngine.playReload()
    }

    private fun finishReloadLocked(c: MutableCombatant) {
        c.isReloading = false
        c.reloadTimer = 0f
        val w = c.activeWeapon()
        if (c.activeSlot == 0) {
            val needed = w.magSize - c.primaryAmmo
            val take = min(needed, c.primaryReserve)
            c.primaryAmmo += take
            c.primaryReserve -= take
        } else if (c.activeSlot == 1) {
            val needed = w.magSize - c.secondaryAmmo
            val take = min(needed, c.secondaryReserve)
            c.secondaryAmmo += take
            c.secondaryReserve -= take
        }
    }

    private fun tryFireLocked(shooter: MutableCombatant) {
        if (shooter.isReloading || shooter.fireCooldown > 0f) return
        val w = shooter.activeWeapon()

        if (shooter.activeSlot != 2) {
            val ammo = shooter.currentAmmo()
            if (ammo <= 0) {
                startReloadLocked(shooter)
                return
            }
            if (shooter.activeSlot == 0) shooter.primaryAmmo-- else shooter.secondaryAmmo--
        }

        shooter.fireCooldown = if (shooter.isPlayer) w.fireIntervalSec else w.fireIntervalSec * 1.75f
        shooter.recoilKick = min(1.0f, shooter.recoilKick + 0.45f)
        shooter.muzzleFlash = 0.08f
        if (shooter.isPlayer) {
            soundEngine.playFire(w.category)
            shooter.pitchDeg = (shooter.pitchDeg - w.recoilPitch * 0.45f).coerceIn(-75f, 75f)
        }

        val eyeX = shooter.x
        val eyeY = shooter.y + EYE_HEIGHT
        val eyeZ = shooter.z

        val baseSpread = when {
            shooter.isPlayer && adsEnabled -> w.adsSpreadDeg
            shooter.isPlayer -> w.spreadDeg
            else -> w.spreadDeg + 3.6f // Fair bot accuracy
        }

        repeat(w.pellets) {
            val spreadYaw = (Random.nextFloat() * 2f - 1f) * baseSpread
            val spreadPitch = (Random.nextFloat() * 2f - 1f) * baseSpread

            val yawRad = Math.toRadians((shooter.yawDeg + spreadYaw).toDouble())
            val pitchRad = Math.toRadians((shooter.pitchDeg + spreadPitch).toDouble())
            val cosPitch = cos(pitchRad).toFloat()
            val dx = (sin(yawRad) * cosPitch).toFloat()
            val dy = -sin(pitchRad).toFloat()
            val dz = (cos(yawRad) * cosPitch).toFloat()

            val blockHit = world.raycastBlock(eyeX, eyeY, eyeZ, dx, dy, dz, w.range)
            var maxHitDist = blockHit?.distance ?: w.range

            var hitEnemy: MutableCombatant? = null
            var hitHead = false
            var hitDist = maxHitDist

            for (target in combatants) {
                if (!target.isAlive || target.team == shooter.team) continue
                // Check Head box first (y + 1.36 .. y + 1.82)
                val headDist = rayBoxIntersection(
                    eyeX, eyeY, eyeZ, dx, dy, dz,
                    target.x - 0.25f, target.y + 1.36f, target.z - 0.25f,
                    target.x + 0.25f, target.y + 1.82f, target.z + 0.25f
                )
                if (headDist != null && headDist < hitDist) {
                    hitDist = headDist
                    hitEnemy = target
                    hitHead = true
                }
                // Check Torso/Legs box (y .. y + 1.36)
                val bodyDist = rayBoxIntersection(
                    eyeX, eyeY, eyeZ, dx, dy, dz,
                    target.x - 0.36f, target.y, target.z - 0.36f,
                    target.x + 0.36f, target.y + 1.36f, target.z + 0.36f
                )
                if (bodyDist != null && bodyDist < hitDist) {
                    hitDist = bodyDist
                    hitEnemy = target
                    hitHead = false
                }
            }

            // Spawn tracer
            if (w.category != WeaponCategory.MELEE) {
                val endX = eyeX + dx * hitDist
                val endY = eyeY + dy * hitDist
                val endZ = eyeZ + dz * hitDist
                val startOffset = 0.45f
                tracers += TracerFx(
                    x1 = eyeX + dx * startOffset,
                    y1 = eyeY + dy * startOffset - 0.12f,
                    z1 = eyeZ + dz * startOffset,
                    x2 = endX,
                    y2 = endY,
                    z2 = endZ,
                    isGold = hitHead,
                    life = 0.09f
                )
            }

            if (hitEnemy != null) {
                applyDamageLocked(shooter, hitEnemy, w, hitHead)
            } else if (blockHit != null && blockHit.blockType == BlockType.GLOO_BLOCK) {
                world.damageGlooAt(blockHit.bx, blockHit.by, blockHit.bz, w.damage)
            }
        }

        if (shooter.isPlayer && shooter.activeSlot != 2 && shooter.currentAmmo() == 0) {
            startReloadLocked(shooter)
        }
    }

    private fun applyDamageLocked(
        shooter: MutableCombatant,
        target: MutableCombatant,
        weapon: WeaponSpec,
        isHeadshot: Boolean,
    ) {
        if (!target.isAlive) return
        val rawDmg = if (isHeadshot) (weapon.damage * weapon.headshotMultiplier).toInt() else weapon.damage
        val absorbed = if (!isHeadshot && target.armor > 0) {
            min(target.armor, (rawDmg * 0.42f).toInt())
        } else 0
        target.armor = max(0, target.armor - absorbed)
        val finalDmg = max(1, rawDmg - absorbed)
        target.hp = max(0, target.hp - finalDmg)
        target.hitFlash = 0.18f

        shooter.damageDealt += finalDmg

        if (shooter.isPlayer) {
            hitMarkerTimer = 0.22f
            hitMarkerHeadshot = isHeadshot
            lastDamagePopup = if (isHeadshot) "CHÍ MẠNG -$finalDmg" else "-$finalDmg"
            lastDamagePopupHeadshot = isHeadshot
            damagePopupTimer = 0.65f
            soundEngine.playHitMarker(isHeadshot)
        }

        if (target.isPlayer) {
            damageFlashAlpha = 0.65f
            val angleToShooter = Math.toDegrees(
                atan2((shooter.x - target.x).toDouble(), (shooter.z - target.z).toDouble())
            ).toFloat()
            damageIndicatorAngleDeg = (angleToShooter - target.yawDeg + 360f) % 360f
            damageIndicatorTimer = 1.0f
            if (isHealing) {
                // Moving/shooting continues, healing still proceeds unless killed
            }
        }

        if (target.hp <= 0) {
            target.isAlive = false
            target.deaths++
            shooter.kills++
            if (isHeadshot) shooter.headshots++
            if (shooter.isPlayer) cash += 200
            if (target.isPlayer) {
                adsEnabled = false
                isHealing = false
            }

            killFeed.add(
                0,
                KillFeedItem(
                    id = killFeedSeq++,
                    killerName = shooter.name,
                    killerTeam = shooter.team,
                    victimName = target.name,
                    victimTeam = target.team,
                    weaponName = weapon.name,
                    isHeadshot = isHeadshot,
                    expiresAtMs = System.currentTimeMillis() + 5000L
                )
            )
            if (killFeed.size > 4) killFeed.removeAt(killFeed.lastIndex)
        }
    }

    private fun checkRoundCompletionLocked() {
        if (phase != RoundPhase.COMBAT_PHASE) return
        val blueAlive = combatants.count { it.team == Team.BLUE && it.isAlive }
        val redAlive = combatants.count { it.team == Team.RED && it.isAlive }
        if (blueAlive == 0 || redAlive == 0) {
            endRoundLocked(blueWins = redAlive == 0)
        }
    }

    private fun endRoundLocked(blueWins: Boolean) {
        if (blueWins) {
            blueScore++
            cash += 1800
            roundBannerText = "THẮNG HIỆP $roundNumber!"
            roundBannerIsWin = true
            soundEngine.playRoundWin()
        } else {
            redScore++
            cash += 1300
            roundBannerText = "THUA HIỆP $roundNumber"
            roundBannerIsWin = false
        }
        phase = RoundPhase.ROUND_END
        phaseTimer = 3.2f
        adsEnabled = false
        isHealing = false
        firingHeld = false
    }

    private fun updateCombatantEffectsLocked(dt: Float) {
        for (c in combatants) {
            if (c.hitFlash > 0f) c.hitFlash = max(0f, c.hitFlash - dt)
            if (c.muzzleFlash > 0f) c.muzzleFlash = max(0f, c.muzzleFlash - dt)
        }
    }

    private fun updateTracersLocked(dt: Float) {
        val it = tracers.iterator()
        while (it.hasNext()) {
            val t = it.next()
            val nextLife = t.life - dt
            if (nextLife <= 0f) it.remove()
        }
        for (i in tracers.indices) {
            tracers[i] = tracers[i].copy(life = tracers[i].life - dt)
        }
    }

    private fun hasLineOfSight(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 0.2f) return true
        val hit = world.raycastBlock(x1, y1, z1, dx, dy, dz, dist)
        return hit == null || hit.distance >= dist - 0.35f
    }

    private fun rayBoxIntersection(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        minX: Float, minY: Float, minZ: Float,
        maxX: Float, maxY: Float, maxZ: Float,
    ): Float? {
        var tmin = 0f
        var tmax = 1000f

        fun slab(o: Float, d: Float, minB: Float, maxB: Float): Boolean {
            if (abs(d) < 1e-6f) {
                return o in minB..maxB
            }
            var t1 = (minB - o) / d
            var t2 = (maxB - o) / d
            if (t1 > t2) {
                val tmp = t1
                t1 = t2
                t2 = tmp
            }
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            return tmin <= tmax
        }

        if (!slab(ox, dx, minX, maxX)) return null
        if (!slab(oy, dy, minY, maxY)) return null
        if (!slab(oz, dz, minZ, maxZ)) return null
        return if (tmin >= 0f) tmin else null
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = ((b - a + 540f) % 360f) - 180f
        return (a + diff * min(1f, t) + 360f) % 360f
    }

    private fun buildRenderSnapshotLocked(): RenderSceneSnapshot {
        val p = combatants.first()
        val w = p.activeWeapon()
        val fov = if (adsEnabled && p.isAlive) w.adsFov else 68f
        val reloadProg = if (p.isReloading && w.reloadTimeSec > 0f) {
            1f - (p.reloadTimer / w.reloadTimeSec).coerceIn(0f, 1f)
        } else 0f

        val snaps = combatants.map { c ->
            CombatantSnapshot(
                id = c.id,
                name = c.name,
                team = c.team,
                isPlayer = c.isPlayer,
                x = c.x,
                y = c.y,
                z = c.z,
                yawDeg = c.yawDeg,
                hp = c.hp,
                maxHp = c.maxHp,
                armor = c.armor,
                isAlive = c.isAlive,
                walkPhase = c.walkPhase,
                hitFlash = c.hitFlash,
                muzzleFlash = c.muzzleFlash,
                kills = c.kills,
                deaths = c.deaths,
                headshots = c.headshots,
                damageDealt = c.damageDealt,
                weaponColor = c.activeWeapon().primaryColor,
            )
        }

        return RenderSceneSnapshot(
            worldVersion = world.worldVersion,
            cameraX = p.x,
            cameraY = p.y + EYE_HEIGHT,
            cameraZ = p.z,
            yawDeg = p.yawDeg,
            pitchDeg = p.pitchDeg,
            fovDeg = fov,
            activeWeapon = w,
            recoilKick = p.recoilKick,
            reloadProgress = reloadProg,
            playerMuzzleFlash = p.muzzleFlash,
            playerWalkBob = sin(p.walkPhase.toDouble()).toFloat() * if (abs(p.vx) + abs(p.vz) > 0.2f) 1f else 0f,
            combatants = snaps,
            tracers = tracers.toList(),
        )
    }

    private fun publishHudLocked() {
        val p = combatants.first()
        val w = p.activeWeapon()
        val now = System.currentTimeMillis()
        killFeed.removeAll { it.expiresAtMs < now }

        val reloadProg = if (p.isReloading && w.reloadTimeSec > 0f) {
            1f - (p.reloadTimer / w.reloadTimeSec).coerceIn(0f, 1f)
        } else 0f
        val healProg = if (isHealing) {
            1f - (healTimer / MEDKIT_DURATION_SEC).coerceIn(0f, 1f)
        } else 0f

        val snaps = combatants.map { c ->
            CombatantSnapshot(
                id = c.id,
                name = c.name,
                team = c.team,
                isPlayer = c.isPlayer,
                x = c.x,
                y = c.y,
                z = c.z,
                yawDeg = c.yawDeg,
                hp = c.hp,
                maxHp = c.maxHp,
                armor = c.armor,
                isAlive = c.isAlive,
                walkPhase = c.walkPhase,
                hitFlash = c.hitFlash,
                muzzleFlash = c.muzzleFlash,
                kills = c.kills,
                deaths = c.deaths,
                headshots = c.headshots,
                damageDealt = c.damageDealt,
                weaponColor = c.activeWeapon().primaryColor,
            )
        }

        _hudState.value = MatchHudState(
            roundNumber = roundNumber,
            blueScore = blueScore,
            redScore = redScore,
            phase = phase,
            phaseSecondsLeft = max(0, phaseTimer.toInt() + 1),
            cash = cash,
            hp = p.hp,
            maxHp = p.maxHp,
            armor = p.armor,
            maxArmor = 130,
            medkits = medkits,
            glooBlocks = glooBlocks,
            isHealing = isHealing,
            healProgress = healProg,
            isAds = adsEnabled,
            isReloading = p.isReloading,
            reloadProgress = reloadProg,
            activeSlot = p.activeSlot,
            primaryWeapon = p.primaryWeapon,
            primaryAmmo = p.primaryAmmo,
            primaryReserve = p.primaryReserve,
            secondaryWeapon = p.secondaryWeapon,
            secondaryAmmo = p.secondaryAmmo,
            secondaryReserve = p.secondaryReserve,
            activeWeapon = w,
            blueAlive = combatants.count { it.team == Team.BLUE && it.isAlive },
            redAlive = combatants.count { it.team == Team.RED && it.isAlive },
            playerKills = p.kills,
            playerDeaths = p.deaths,
            playerHeadshots = p.headshots,
            playerDamage = p.damageDealt,
            crosshairSpread = if (adsEnabled) w.adsSpreadDeg else w.spreadDeg + p.recoilKick * 3f,
            hitMarkerAlpha = (hitMarkerTimer / 0.22f).coerceIn(0f, 1f),
            hitMarkerHeadshot = hitMarkerHeadshot,
            lastDamagePopup = lastDamagePopup,
            lastDamagePopupHeadshot = lastDamagePopupHeadshot,
            damageFlashAlpha = damageFlashAlpha,
            damageIndicatorAngleDeg = damageIndicatorAngleDeg,
            roundBannerText = roundBannerText,
            roundBannerIsWin = roundBannerIsWin,
            killFeed = killFeed.toList(),
            scoreboard = snaps.sortedByDescending { it.kills * 1000 + it.damageDealt },
            radarDots = snaps,
            playerX = p.x,
            playerZ = p.z,
            playerYawDeg = p.yawDeg,
            shopOpen = shopOpen,
            isAlive = p.isAlive,
        )
    }

    private companion object {
        const val BUY_DURATION_SEC = 10f
        const val COMBAT_DURATION_SEC = 75f
        const val MEDKIT_DURATION_SEC = 1.6f
        const val WINS_TO_WIN_MATCH = 4
        const val EYE_HEIGHT = 1.56f
        const val GRAVITY = 22f
        const val JUMP_VELOCITY = 7.7f
    }
}
