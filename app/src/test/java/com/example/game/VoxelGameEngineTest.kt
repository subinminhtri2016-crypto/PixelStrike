package com.example.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VoxelGameEngineTest {

    @Test
    fun buyPhase_buyWeaponAndGloo_updatesLoadoutAndCash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sound = SoundSynthEngine(context).apply { soundEnabled = false }
        val engine = VoxelGameEngine("Tester", sound)

        val initialHud = engine.hudState.value
        assertEquals(RoundPhase.BUY_PHASE, initialHud.phase)
        assertEquals(800, initialHud.cash)

        engine.buyWeapon(WeaponCatalog.DESERT_EAGLE)
        engine.buyTacticalItem(TacticalItem.MEDKIT)
        engine.skipBuyPhase()
        engine.step(0.016f)

        val combatHud = engine.hudState.value
        assertEquals(RoundPhase.COMBAT_PHASE, combatHud.phase)
        assertEquals(0, combatHud.cash)
        assertEquals(WeaponCatalog.DESERT_EAGLE.id, combatHud.secondaryWeapon.id)
        assertEquals(2, combatHud.medkits)
    }

    @Test
    fun glooWall_deploysAndBlocksRaycast() {
        val world = VoxelWorld()
        val placed = world.placeGlooWall(15.5f, 1.0f, 12.5f, 0f)
        assertTrue(placed)

        val hit = world.raycastBlock(15.5f, 1.5f, 12.5f, 0f, 0f, 1f, 10f)
        assertNotNull(hit)
        assertEquals(BlockType.GLOO_BLOCK, hit!!.blockType)
    }
}
