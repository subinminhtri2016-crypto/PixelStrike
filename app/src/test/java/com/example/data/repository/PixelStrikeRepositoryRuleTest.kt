package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PixelStrikeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureProfile_and_recordMatch_persistsToProfileMatchesAndLeaderboard() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PixelStrikeRepository(firestore, auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureProfile("GhostVoxel")
        }
        assertTrue("Profile creation failed: ${profileResult.exceptionOrNull()}", profileResult.isSuccess)

        val deltaResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordMatchResult(
                playerTeamScore = 4,
                enemyTeamScore = 2,
                kills = 8,
                deaths = 2,
                headshots = 3,
                damageDealt = 1600,
                fallbackDisplayName = "GhostVoxel"
            )
        }
        assertTrue("Match record failed: ${deltaResult.exceptionOrNull()}", deltaResult.isSuccess)

        val observedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null && it.matchesPlayed >= 1L }
        }
        assertNotNull(observedProfile)
        assertEquals(1L, observedProfile!!.wins)

        val matches = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMatchHistory(uid).first { it.isNotEmpty() }
        }
        assertTrue(matches.isNotEmpty())

        val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLeaderboard(25L).first { list -> list.any { it.userId == uid } }
        }
        assertTrue(leaderboard.any { it.userId == uid })
    }

    @Test
    fun updatePreferences_validPayload_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PixelStrikeRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureProfile("GhostVoxel").getOrThrow() }

        val updateRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePreferences("SniperKing", 1.75, false)
        }
        assertTrue("Preferences update failed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

        val updated = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it?.displayName == "SniperKing" }
        }
        assertEquals(false, updated?.soundEnabled)
    }

    @Test
    fun observeProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PixelStrikeRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureProfile("AliceVoxel").getOrThrow() }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PixelStrikeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProfile(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user profile access")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    @Test
    fun observeLeaderboard_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PixelStrikeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeLeaderboard(10L).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.pixel@test.com"
        const val BOB_EMAIL = "bob.pixel@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
