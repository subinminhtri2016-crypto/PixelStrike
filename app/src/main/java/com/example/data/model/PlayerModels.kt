package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "VoxelSoldier",
    val rankPoints: Long = 1000L,
    val rankTier: String = "Bronze I",
    val wins: Long = 0L,
    val matchesPlayed: Long = 0L,
    val totalKills: Long = 0L,
    val totalDeaths: Long = 0L,
    val totalHeadshots: Long = 0L,
    val sensitivity: Double = 1.0,
    val soundEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MatchHistoryRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val playerTeamScore: Long = 0L,
    val enemyTeamScore: Long = 0L,
    val won: Boolean = false,
    val kills: Long = 0L,
    val deaths: Long = 0L,
    val headshots: Long = 0L,
    val damageDealt: Long = 0L,
    val rankDelta: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "VoxelSoldier",
    val rankPoints: Long = 1000L,
    val rankTier: String = "Bronze I",
    val wins: Long = 0L,
    val totalKills: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

object RankTierHelper {
    fun computeTier(points: Long): String = when {
        points >= 3200L -> "Huyền Thoại"
        points >= 2600L -> "Kim Cương"
        points >= 2100L -> "Bạch Kim"
        points >= 1700L -> "Vàng II"
        points >= 1450L -> "Vàng I"
        points >= 1250L -> "Bạc II"
        points >= 1100L -> "Bạc I"
        points >= 950L -> "Đồng II"
        else -> "Đồng I"
    }
}
