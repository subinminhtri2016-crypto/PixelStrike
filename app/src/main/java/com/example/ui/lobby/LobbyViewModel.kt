package com.example.ui.lobby

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LeaderboardEntry
import com.example.data.model.MatchHistoryRecord
import com.example.data.model.PlayerProfile
import com.example.data.repository.PixelStrikeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class LobbyViewModel(
    private val repository: PixelStrikeRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
) : ViewModel() {

    val profileState: StateFlow<UiState<PlayerProfile?>> = repository.observeProfile(currentUserId)
        .map<PlayerProfile?, UiState<PlayerProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error) ?: throw error
            Log.w(TAG, "Error observing profile", fsError)
            emit(UiState.Error(fsError.message ?: "Không thể tải hồ sơ người chơi"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val matchHistoryState: StateFlow<UiState<List<MatchHistoryRecord>>> = repository.observeMatchHistory(currentUserId)
        .map<List<MatchHistoryRecord>, UiState<List<MatchHistoryRecord>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error) ?: throw error
            Log.w(TAG, "Error observing match history", fsError)
            emit(UiState.Error(fsError.message ?: "Không thể tải lịch sử trận đấu"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> = repository.observeLeaderboard(25L)
        .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = unwrapFirestoreError(error) ?: throw error
            Log.w(TAG, "Error observing leaderboard", fsError)
            emit(UiState.Error(fsError.message ?: "Không thể tải bảng xếp hạng"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfile(initialDisplayName)
        }
    }

    fun saveSettings(displayName: String, sensitivity: Double, soundEnabled: Boolean) {
        viewModelScope.launch {
            _saveStatus.value = "Đang lưu..."
            val res = repository.updatePreferences(displayName, sensitivity, soundEnabled)
            _saveStatus.value = if (res.isSuccess) {
                "Đã đồng bộ lên đám mây!"
            } else {
                "Lỗi lưu cài đặt: ${res.exceptionOrNull()?.localizedMessage ?: "Thử lại sau"}"
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): Throwable? {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return null
        return firestoreError ?: error
    }

    private companion object {
        const val TAG = "LobbyViewModel"
    }
}
