package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchHistoryRecord
import com.example.data.model.PlayerProfile
import com.example.data.model.RankTierHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.max

class PixelStrikeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Người chơi cần đăng nhập Google trước khi truy cập dữ liệu.")
    }

    fun observeProfile(userId: String): Flow<PlayerProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            PlayerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logFlowError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeMatchHistory(userId: String): Flow<List<MatchHistoryRecord>> = flow {
        val path = "users/$userId/matches"
        emitAll(
            db.collection("users").document(userId).collection("matches")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        MatchHistoryRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeLeaderboard(limitCount: Long = 25L): Flow<List<LeaderboardEntry>> = flow {
        val boundedLimit = limitCount.coerceIn(1L, 50L)
        val path = "leaderboard"
        val uid = auth.currentUser?.uid
        emitAll(
            db.collection("leaderboard")
                .orderBy("rankPoints", Query.Direction.DESCENDING)
                .limit(boundedLimit)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (uid != null) {
                        logFlowError(error, OperationType.LIST, path, uid)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfile(rawDisplayName: String): Result<PlayerProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = sanitizeDisplayName(rawDisplayName)
        val userRef = db.collection("users").document(uid)
        val lbRef = db.collection("leaderboard").document(uid)

        return try {
            val snap = userRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: PlayerProfile(id = uid, userId = uid, displayName = cleanName)
                val lbSnap = lbRef.get().await()
                if (!lbSnap.exists()) {
                    lbRef.set(
                        mapOf(
                            "userId" to uid,
                            "displayName" to existing.displayName,
                            "rankPoints" to existing.rankPoints,
                            "rankTier" to existing.rankTier,
                            "wins" to existing.wins,
                            "totalKills" to existing.totalKills,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
                Result.success(existing)
            } else {
                val initialTier = RankTierHelper.computeTier(1000L)
                val profilePayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "rankPoints" to 1000L,
                    "rankTier" to initialTier,
                    "wins" to 0L,
                    "matchesPlayed" to 0L,
                    "totalKills" to 0L,
                    "totalDeaths" to 0L,
                    "totalHeadshots" to 0L,
                    "sensitivity" to 1.0,
                    "soundEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                val lbPayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "rankPoints" to 1000L,
                    "rankTier" to initialTier,
                    "wins" to 0L,
                    "totalKills" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                val batch = db.batch()
                batch.set(userRef, profilePayload)
                batch.set(lbRef, lbPayload)
                batch.commit().await()

                val created = userRef.get().await().toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: PlayerProfile(id = uid, userId = uid, displayName = cleanName, rankTier = initialTier)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        displayName: String,
        sensitivity: Double,
        soundEnabled: Boolean,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanName = sanitizeDisplayName(displayName)
        val clampedSensitivity = sensitivity.coerceIn(0.2, 3.0)
        val userRef = db.collection("users").document(uid)
        val lbRef = db.collection("leaderboard").document(uid)

        return try {
            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "displayName" to cleanName,
                    "sensitivity" to clampedSensitivity,
                    "soundEnabled" to soundEnabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            val lbSnap = lbRef.get().await()
            if (lbSnap.exists()) {
                batch.update(
                    lbRef,
                    mapOf(
                        "displayName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(
        playerTeamScore: Int,
        enemyTeamScore: Int,
        kills: Int,
        deaths: Int,
        headshots: Int,
        damageDealt: Int,
        fallbackDisplayName: String = "VoxelSoldier",
    ): Result<Long> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val won = playerTeamScore > enemyTeamScore
        val rankDelta = if (won) {
            (25L + (kills.coerceAtLeast(0) * 2L) + headshots.coerceAtLeast(0)).coerceAtMost(65L)
        } else {
            (-14L + kills.coerceAtLeast(0)).coerceIn(-20L, 4L)
        }

        val userRef = db.collection("users").document(uid)
        val matchId = "m_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val matchRef = userRef.collection("matches").document(matchId)
        val lbRef = db.collection("leaderboard").document(uid)

        return try {
            val userSnap = userRef.get().await()
            val lbSnap = lbRef.get().await()
            val batch = db.batch()

            val currentProfile = if (userSnap.exists()) {
                userSnap.toObject(
                    PlayerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null

            val cleanName = sanitizeDisplayName(currentProfile?.displayName ?: fallbackDisplayName)
            val newPoints = max(0L, (currentProfile?.rankPoints ?: 1000L) + rankDelta)
            val newTier = RankTierHelper.computeTier(newPoints)
            val newWins = (currentProfile?.wins ?: 0L) + if (won) 1L else 0L
            val newMatches = (currentProfile?.matchesPlayed ?: 0L) + 1L
            val newKills = (currentProfile?.totalKills ?: 0L) + kills.coerceAtLeast(0).toLong()
            val newDeaths = (currentProfile?.totalDeaths ?: 0L) + deaths.coerceAtLeast(0).toLong()
            val newHeadshots = (currentProfile?.totalHeadshots ?: 0L) + headshots.coerceAtLeast(0).toLong()

            if (currentProfile == null) {
                batch.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "rankPoints" to newPoints,
                        "rankTier" to newTier,
                        "wins" to newWins,
                        "matchesPlayed" to newMatches,
                        "totalKills" to newKills,
                        "totalDeaths" to newDeaths,
                        "totalHeadshots" to newHeadshots,
                        "sensitivity" to 1.0,
                        "soundEnabled" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                batch.update(
                    userRef,
                    mapOf(
                        "displayName" to cleanName,
                        "rankPoints" to newPoints,
                        "rankTier" to newTier,
                        "wins" to newWins,
                        "matchesPlayed" to newMatches,
                        "totalKills" to newKills,
                        "totalDeaths" to newDeaths,
                        "totalHeadshots" to newHeadshots,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.set(
                matchRef,
                mapOf(
                    "userId" to uid,
                    "playerTeamScore" to playerTeamScore.coerceIn(0, 7).toLong(),
                    "enemyTeamScore" to enemyTeamScore.coerceIn(0, 7).toLong(),
                    "won" to won,
                    "kills" to kills.coerceIn(0, 100).toLong(),
                    "deaths" to deaths.coerceIn(0, 50).toLong(),
                    "headshots" to headshots.coerceIn(0, 100).toLong(),
                    "damageDealt" to damageDealt.coerceIn(0, 50000).toLong(),
                    "rankDelta" to rankDelta,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )

            if (lbSnap.exists()) {
                batch.update(
                    lbRef,
                    mapOf(
                        "displayName" to cleanName,
                        "rankPoints" to newPoints,
                        "rankTier" to newTier,
                        "wins" to newWins,
                        "totalKills" to newKills,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                batch.set(
                    lbRef,
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "rankPoints" to newPoints,
                        "rankTier" to newTier,
                        "wins" to newWins,
                        "totalKills" to newKills,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.commit().await()
            Result.success(rankDelta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, matchRef.path)
            Result.failure(e)
        }
    }

    private fun logFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun sanitizeDisplayName(name: String): String {
        val trimmed = name.trim().take(40)
        return trimmed.ifBlank { "VoxelSoldier" }
    }
}
