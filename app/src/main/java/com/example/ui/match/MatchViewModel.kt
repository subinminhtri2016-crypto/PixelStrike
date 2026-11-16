package com.example.ui.match

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.PlayerProfile
import com.example.data.repository.PixelStrikeRepository
import com.example.game.MatchHudState
import com.example.game.RoundPhase
import com.example.game.SoundSynthEngine
import com.example.game.VoxelGameEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MatchViewModel(
    appContext: Context,
    private val repository: PixelStrikeRepository,
    private val profile: PlayerProfile,
) : ViewModel() {

    private val soundEngine = SoundSynthEngine(appContext).apply {
        soundEnabled = profile.soundEnabled
    }

    var engine: VoxelGameEngine = createEngine()
        private set

    private val _rankDeltaState = MutableStateFlow<Long?>(null)
    val rankDeltaState: StateFlow<Long?> = _rankDeltaState.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var matchSubmitted = false

    private fun createEngine(): VoxelGameEngine {
        return VoxelGameEngine(
            playerName = profile.displayName,
            soundEngine = soundEngine,
        ).apply {
            touchSensitivity = profile.sensitivity.toFloat().coerceIn(0.2f, 3.0f)
        }
    }

    fun onMatchEnded(hud: MatchHudState) {
        if (hud.phase != RoundPhase.MATCH_END || matchSubmitted) return
        matchSubmitted = true
        viewModelScope.launch {
            val res = repository.recordMatchResult(
                playerTeamScore = hud.blueScore,
                enemyTeamScore = hud.redScore,
                kills = hud.playerKills,
                deaths = hud.playerDeaths,
                headshots = hud.playerHeadshots,
                damageDealt = hud.playerDamage,
                fallbackDisplayName = profile.displayName,
            )
            if (res.isSuccess) {
                _rankDeltaState.value = res.getOrNull()
                _syncError.value = null
            } else {
                _syncError.value = "Không thể đồng bộ điểm Rank: ${res.exceptionOrNull()?.localizedMessage ?: "Lỗi mạng"}"
            }
        }
    }

    fun restartMatch(): VoxelGameEngine {
        matchSubmitted = false
        _rankDeltaState.value = null
        _syncError.value = null
        engine = createEngine()
        return engine
    }
}
