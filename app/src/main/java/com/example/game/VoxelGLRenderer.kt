package com.example.game

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class VoxelGLRenderer(
    private val engine: VoxelGameEngine,
) : GLSurfaceView.Renderer {

    private var program = 0
    private var aPosLoc = 0
    private var aTexLoc = 0
    private var aShadeLoc = 0
    private var uMvpLoc = 0
    private var uTexLoc = 0
    private var uTintLoc = 0
    private var uFogColorLoc = 0
    private var uCamPosLoc = 0

    private var atlasTextureId = 0

    private var worldVboBuffer: FloatBuffer? = null
    private var worldVertexCount = 0
    private var builtWorldVersion = -1

    private var dynamicBuffer: FloatBuffer = allocateFloatBuffer(12000)

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewportAspect = 1.6f
    private var lastFrameNs = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.84f, 0.56f, 0.34f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        aShadeLoc = GLES20.glGetAttribLocation(program, "aShade")
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        uTexLoc = GLES20.glGetUniformLocation(program, "uTexture")
        uTintLoc = GLES20.glGetUniformLocation(program, "uTint")
        uFogColorLoc = GLES20.glGetUniformLocation(program, "uFogColor")
        uCamPosLoc = GLES20.glGetUniformLocation(program, "uCamPos")

        atlasTextureId = createPixelAtlasTexture()
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportAspect = width.toFloat() / max(1, height).toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        engine.step(dt)
        val scene = engine.getRenderSnapshot()

        if (scene.worldVersion != builtWorldVersion) {
            rebuildWorldMesh()
            builtWorldVersion = scene.worldVersion
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(uTexLoc, 0)
        GLES20.glUniform3f(uFogColorLoc, 0.84f, 0.56f, 0.34f)
        GLES20.glUniform3f(uCamPosLoc, scene.cameraX, scene.cameraY, scene.cameraZ)

        Matrix.perspectiveM(projMatrix, 0, scene.fovDeg, viewportAspect, 0.08f, 120f)
        val yawRad = Math.toRadians(scene.yawDeg.toDouble())
        val pitchRad = Math.toRadians(scene.pitchDeg.toDouble())
        val cosPitch = cos(pitchRad).toFloat()
        val dx = (sin(yawRad) * cosPitch).toFloat()
        val dy = -sin(pitchRad).toFloat()
        val dz = (cos(yawRad) * cosPitch).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            scene.cameraX, scene.cameraY, scene.cameraZ,
            scene.cameraX + dx, scene.cameraY + dy, scene.cameraZ + dz,
            0f, 1f, 0f
        )

        // 1. Draw Voxel World
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uTintLoc, 1f, 1f, 1f, 1f)
        worldVboBuffer?.let { buf ->
            drawBuffer(buf, worldVertexCount)
        }

        // 2. Draw Combatants (Minecraft-style articulated voxel soldiers)
        for (c in scene.combatants) {
            if (!c.isAlive || c.isPlayer) continue
            drawCombatant(c)
        }

        // 3. Draw Bullet Tracers
        if (scene.tracers.isNotEmpty()) {
            GLES20.glDisable(GLES20.GL_CULL_FACE)
            GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
            for (t in scene.tracers) {
                if (t.isGold) {
                    GLES20.glUniform4f(uTintLoc, 1.0f, 0.85f, 0.25f, 1f)
                } else {
                    GLES20.glUniform4f(uTintLoc, 1.0f, 0.96f, 0.78f, 1f)
                }
                drawTracerQuad(t)
            }
            GLES20.glEnable(GLES20.GL_CULL_FACE)
        }

        // 4. Draw First-Person Voxel Weapon Viewmodel (clear depth so gun never clips walls)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        drawFirstPersonWeapon(scene)
    }

    private fun drawCombatant(c: CombatantSnapshot) {
        val bodyTile = if (c.team == Team.BLUE) TILE_SOLDIER_BLUE else TILE_SOLDIER_RED
        val flash = if (c.hitFlash > 0f) 1.6f else 1.0f
        val tintR = if (c.hitFlash > 0f) 1.5f else 1.0f
        val tintG = if (c.hitFlash > 0f) 0.55f else 1.0f
        val tintB = if (c.hitFlash > 0f) 0.55f else 1.0f

        val swing = sin(c.walkPhase.toDouble()).toFloat() * 24f

        // Legs
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, -0.14f, 0.34f, 0f, swing, 0.22f, 0.68f, 0.24f, bodyTile, tintR, tintG, tintB)
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0.14f, 0.34f, 0f, -swing, 0.22f, 0.68f, 0.24f, bodyTile, tintR, tintG, tintB)

        // Torso + tactical armor vest
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0f, 1.02f, 0f, 0f, 0.54f, 0.68f, 0.30f, bodyTile, tintR * flash, tintG, tintB)

        // Head (with visor face on +Z local front)
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0f, 1.58f, 0f, 0f, 0.44f, 0.44f, 0.44f, TILE_FACE, tintR, tintG, tintB)

        // Arms
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, -0.37f, 1.02f, 0.08f, -20f - swing * 0.5f, 0.20f, 0.62f, 0.20f, bodyTile, tintR, tintG, tintB)
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0.37f, 1.04f, 0.16f, -65f, 0.20f, 0.62f, 0.20f, bodyTile, tintR, tintG, tintB)

        // Held Voxel Rifle
        val r = ((c.weaponColor shr 16) and 0xFF) / 255f
        val g = ((c.weaponColor shr 8) and 0xFF) / 255f
        val b = (c.weaponColor and 0xFF) / 255f
        drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0.35f, 1.12f, 0.44f, 0f, 0.12f, 0.16f, 0.56f, TILE_GUN, r * 1.4f, g * 1.4f, b * 1.4f)

        if (c.muzzleFlash > 0f) {
            drawBoxPart(c.x, c.y, c.z, c.yawDeg, 0.35f, 1.14f, 0.78f, 0f, 0.22f, 0.22f, 0.22f, TILE_MUZZLE, 1.4f, 1.2f, 0.6f)
        }
    }

    private fun drawBoxPart(
        worldX: Float, worldY: Float, worldZ: Float,
        yawDeg: Float,
        localX: Float, localY: Float, localZ: Float,
        limbPitchDeg: Float,
        sx: Float, sy: Float, sz: Float,
        tile: Int,
        tintR: Float, tintG: Float, tintB: Float,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldX, worldY, worldZ)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, localX, localY, localZ)
        if (limbPitchDeg != 0f) {
            Matrix.rotateM(modelMatrix, 0, limbPitchDeg, 1f, 0f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uTintLoc, tintR, tintG, tintB, 1f)

        val verts = emitUnitCubeVertices(tile)
        drawDynamicVertices(verts)
    }

    private fun drawFirstPersonWeapon(scene: RenderSceneSnapshot) {
        val w = scene.activeWeapon
        val reloadDip = if (scene.reloadProgress > 0f) {
            sin(scene.reloadProgress * Math.PI).toFloat() * 0.22f
        } else 0f
        val bobX = scene.playerWalkBob * 0.015f
        val bobY = kotlin.math.abs(scene.playerWalkBob) * 0.014f
        val adsLerp = if (scene.fovDeg < 60f) 1f else 0f

        val baseX = 0.28f * (1f - adsLerp * 0.72f) + bobX
        val baseY = -0.24f - reloadDip - bobY + scene.recoilKick * 0.025f
        val baseZ = -0.45f + scene.recoilKick * 0.07f

        val pr = ((w.primaryColor shr 16) and 0xFF) / 255f
        val pg = ((w.primaryColor shr 8) and 0xFF) / 255f
        val pb = (w.primaryColor and 0xFF) / 255f

        val ar = ((w.accentColor shr 16) and 0xFF) / 255f
        val ag = ((w.accentColor shr 8) and 0xFF) / 255f
        val ab = (w.accentColor and 0xFF) / 255f

        // Receiver block
        drawViewSpaceBox(
            baseX, baseY, baseZ,
            scene.recoilKick * 12f - reloadDip * 90f,
            0.09f, 0.11f, w.barrelLength * 0.7f,
            TILE_GUN, pr * 1.6f, pg * 1.6f, pb * 1.6f
        )

        // Barrel block
        drawViewSpaceBox(
            baseX, baseY + 0.03f, baseZ - w.barrelLength * 0.35f,
            scene.recoilKick * 12f - reloadDip * 90f,
            0.055f, 0.055f, w.barrelLength * 0.65f,
            TILE_GUN, ar * 1.5f, ag * 1.5f, ab * 1.5f
        )

        // Magazine / Grip accent
        if (w.category != WeaponCategory.MELEE) {
            drawViewSpaceBox(
                baseX, baseY - 0.08f, baseZ + 0.04f,
                scene.recoilKick * 12f - reloadDip * 90f,
                0.055f, 0.11f, 0.09f,
                TILE_GUN, ar * 1.6f, ag * 1.6f, ab * 1.6f
            )
        }

        // Sniper Scope or AR Iron Sight
        if (w.category == WeaponCategory.SNIPER || w.category == WeaponCategory.AR) {
            drawViewSpaceBox(
                baseX, baseY + 0.08f, baseZ - 0.03f,
                scene.recoilKick * 12f - reloadDip * 90f,
                0.045f, 0.045f, if (w.category == WeaponCategory.SNIPER) 0.22f else 0.08f,
                TILE_GUN, 0.35f, 0.4f, 0.45f
            )
        }

        // Muzzle Flash
        if (scene.playerMuzzleFlash > 0f && w.category != WeaponCategory.MELEE) {
            drawViewSpaceBox(
                baseX, baseY + 0.03f, baseZ - w.barrelLength * 0.78f,
                0f, 0.14f, 0.14f, 0.14f,
                TILE_MUZZLE, 1.5f, 1.3f, 0.6f
            )
        }
    }

    private fun drawViewSpaceBox(
        vx: Float, vy: Float, vz: Float,
        pitchDeg: Float,
        sx: Float, sy: Float, sz: Float,
        tile: Int,
        tintR: Float, tintG: Float, tintB: Float,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, vx, vy, vz)
        if (pitchDeg != 0f) Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)

        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uTintLoc, tintR, tintG, tintB, 1f)
        drawDynamicVertices(emitUnitCubeVertices(tile))
    }

    private fun drawTracerQuad(t: TracerFx) {
        val uv = tileUv(TILE_MUZZLE)
        val u0 = uv[0]
        val v0 = uv[1]
        val u1 = uv[2]
        val v1 = uv[3]
        val w = 0.025f
        val verts = floatArrayOf(
            t.x1, t.y1 - w, t.z1, u0, v0, 1.2f,
            t.x2, t.y2 - w, t.z2, u1, v0, 1.2f,
            t.x2, t.y2 + w, t.z2, u1, v1, 1.2f,
            t.x1, t.y1 - w, t.z1, u0, v0, 1.2f,
            t.x2, t.y2 + w, t.z2, u1, v1, 1.2f,
            t.x1, t.y1 + w, t.z1, u0, v1, 1.2f,
        )
        drawDynamicVertices(verts)
    }

    private fun drawDynamicVertices(verts: FloatArray) {
        val needed = verts.size
        if (dynamicBuffer.capacity() < needed) {
            dynamicBuffer = allocateFloatBuffer(needed)
        }
        dynamicBuffer.clear()
        dynamicBuffer.put(verts)
        dynamicBuffer.position(0)
        drawBuffer(dynamicBuffer, needed / FLOATS_PER_VERTEX)
    }

    private fun drawBuffer(buf: FloatBuffer, vertexCount: Int) {
        if (vertexCount <= 0) return
        val stride = FLOATS_PER_VERTEX * 4
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(aTexLoc)
        GLES20.glVertexAttribPointer(aTexLoc, 2, GLES20.GL_FLOAT, false, stride, buf)

        buf.position(5)
        GLES20.glEnableVertexAttribArray(aShadeLoc)
        GLES20.glVertexAttribPointer(aShadeLoc, 1, GLES20.GL_FLOAT, false, stride, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    private fun rebuildWorldMesh() {
        val world = engine.world
        val list = ArrayList<Float>(16000)

        for (y in 0 until world.sizeY) {
            for (z in 0 until world.sizeZ) {
                for (x in 0 until world.sizeX) {
                    val block = world.getBlock(x, y, z)
                    if (block == BlockType.AIR) continue
                    val tile = blockToTile(block)
                    val uv = tileUv(tile)
                    val u0 = uv[0]
                    val v0 = uv[1]
                    val u1 = uv[2]
                    val v1 = uv[3]

                    val fx = x.toFloat()
                    val fy = y.toFloat()
                    val fz = z.toFloat()

                    // +Y Top
                    if (!world.isSolid(x, y + 1, z)) {
                        addQuad(list, fx, fy + 1f, fz + 1f, fx + 1f, fy + 1f, fz + 1f, fx + 1f, fy + 1f, fz, fx, fy + 1f, fz, u0, v0, u1, v1, 1.0f)
                    }
                    // -Y Bottom
                    if (y > 0 && !world.isSolid(x, y - 1, z)) {
                        addQuad(list, fx, fy, fz, fx + 1f, fy, fz, fx + 1f, fy, fz + 1f, fx, fy, fz + 1f, u0, v0, u1, v1, 0.55f)
                    }
                    // +Z Front
                    if (!world.isSolid(x, y, z + 1)) {
                        addQuad(list, fx, fy, fz + 1f, fx + 1f, fy, fz + 1f, fx + 1f, fy + 1f, fz + 1f, fx, fy + 1f, fz + 1f, u0, v0, u1, v1, 0.88f)
                    }
                    // -Z Back
                    if (!world.isSolid(x, y, z - 1)) {
                        addQuad(list, fx + 1f, fy, fz, fx, fy, fz, fx, fy + 1f, fz, fx + 1f, fy + 1f, fz, u0, v0, u1, v1, 0.84f)
                    }
                    // +X Right
                    if (!world.isSolid(x + 1, y, z)) {
                        addQuad(list, fx + 1f, fy, fz + 1f, fx + 1f, fy, fz, fx + 1f, fy + 1f, fz, fx + 1f, fy + 1f, fz + 1f, u0, v0, u1, v1, 0.76f)
                    }
                    // -X Left
                    if (!world.isSolid(x - 1, y, z)) {
                        addQuad(list, fx, fy, fz, fx, fy, fz + 1f, fx, fy + 1f, fz + 1f, fx, fy + 1f, fz, u0, v0, u1, v1, 0.72f)
                    }
                }
            }
        }

        val arr = list.toFloatArray()
        worldVertexCount = arr.size / FLOATS_PER_VERTEX
        worldVboBuffer = allocateFloatBuffer(arr.size).apply {
            put(arr)
            position(0)
        }
    }

    private fun emitUnitCubeVertices(tile: Int): FloatArray {
        val uv = tileUv(tile)
        val u0 = uv[0]
        val v0 = uv[1]
        val u1 = uv[2]
        val v1 = uv[3]
        val list = ArrayList<Float>(36 * FLOATS_PER_VERTEX)
        val h = 0.5f
        // +Y
        addQuad(list, -h, h, h, h, h, h, h, h, -h, -h, h, -h, u0, v0, u1, v1, 1.0f)
        // -Y
        addQuad(list, -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h, u0, v0, u1, v1, 0.6f)
        // +Z
        addQuad(list, -h, -h, h, h, -h, h, h, h, h, -h, h, h, u0, v0, u1, v1, 0.92f)
        // -Z
        addQuad(list, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, u0, v0, u1, v1, 0.84f)
        // +X
        addQuad(list, h, -h, h, h, -h, -h, h, h, -h, h, h, h, u0, v0, u1, v1, 0.78f)
        // -X
        addQuad(list, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, u0, v0, u1, v1, 0.74f)
        return list.toFloatArray()
    }

    private fun addQuad(
        out: MutableList<Float>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        shade: Float,
    ) {
        // CCW triangles: (0,1,2) and (0,2,3)
        out.add(x0); out.add(y0); out.add(z0); out.add(u0); out.add(v1); out.add(shade)
        out.add(x1); out.add(y1); out.add(z1); out.add(u1); out.add(v1); out.add(shade)
        out.add(x2); out.add(y2); out.add(z2); out.add(u1); out.add(v0); out.add(shade)

        out.add(x0); out.add(y0); out.add(z0); out.add(u0); out.add(v1); out.add(shade)
        out.add(x2); out.add(y2); out.add(z2); out.add(u1); out.add(v0); out.add(shade)
        out.add(x3); out.add(y3); out.add(z3); out.add(u0); out.add(v0); out.add(shade)
    }

    private fun blockToTile(block: BlockType): Int = when (block) {
        BlockType.SAND -> TILE_SAND
        BlockType.SANDSTONE -> TILE_SANDSTONE
        BlockType.TERRACOTTA -> TILE_TERRACOTTA
        BlockType.BUNKER_CONCRETE -> TILE_CONCRETE
        BlockType.CRATE_WOOD -> TILE_CRATE
        BlockType.SANDBAG -> TILE_SANDBAG
        BlockType.PALM_LOG -> TILE_PALM_LOG
        BlockType.PALM_LEAVES -> TILE_PALM_LEAVES
        BlockType.GLOO_BLOCK -> TILE_GLOO
        BlockType.AIR -> TILE_SAND
    }

    private fun tileUv(tile: Int): FloatArray {
        val col = tile % ATLAS_COLS
        val row = tile / ATLAS_COLS
        val step = 1f / ATLAS_COLS
        val pad = 0.002f
        val u0 = col * step + pad
        val v0 = row * step + pad
        val u1 = (col + 1) * step - pad
        val v1 = (row + 1) * step - pad
        return floatArrayOf(u0, v0, u1, v1)
    }

    private fun createPixelAtlasTexture(): Int {
        val tileSize = 16
        val bmpSize = ATLAS_COLS * tileSize
        val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
        val rng = Random(42)

        fun paintTile(tileIdx: Int, pixelFn: (px: Int, py: Int) -> Int) {
            val ox = (tileIdx % ATLAS_COLS) * tileSize
            val oy = (tileIdx / ATLAS_COLS) * tileSize
            for (py in 0 until tileSize) {
                for (px in 0 until tileSize) {
                    bmp.setPixel(ox + px, oy + py, pixelFn(px, py))
                }
            }
        }

        // 0: Sand
        paintTile(TILE_SAND) { px, py ->
            val n = ((px * 13 + py * 29 + rng.nextInt(7)) % 5) - 2
            rgb(218 + n * 4, 184 + n * 4, 128 + n * 3)
        }
        // 1: Sandstone brick
        paintTile(TILE_SANDSTONE) { px, py ->
            val mortar = (py == 0 || py == 8 || (py < 8 && px == 8) || (py >= 8 && px == 0))
            if (mortar) rgb(172, 134, 86) else rgb(206 + ((px + py) % 3) * 4, 166 + (px % 2) * 5, 110)
        }
        // 2: Terracotta canyon
        paintTile(TILE_TERRACOTTA) { px, py ->
            val stripe = if (py % 5 == 0) -14 else if (py % 3 == 0) 8 else 0
            rgb(176 + stripe, 96 + stripe / 2, 62 + stripe / 3)
        }
        // 3: Military bunker concrete
        paintTile(TILE_CONCRETE) { px, py ->
            val border = px == 0 || py == 0 || px == 15 || py == 15
            val rivet = (px == 2 || px == 13) && (py == 2 || py == 13)
            when {
                rivet -> rgb(140, 145, 138)
                border -> rgb(74, 78, 72)
                else -> rgb(104 + ((px * 3 + py) % 4) * 3, 108 + ((px + py) % 3) * 3, 100)
            }
        }
        // 4: Supply wood crate
        paintTile(TILE_CRATE) { px, py ->
            val edge = px <= 1 || py <= 1 || px >= 14 || py >= 14
            val diag = px == py || px + py == 15
            when {
                edge -> rgb(112, 74, 38)
                diag -> rgb(134, 92, 48)
                else -> rgb(166, 118, 64)
            }
        }
        // 5: Sandbag cover
        paintTile(TILE_SANDBAG) { px, py ->
            val seam = py == 4 || py == 10 || (py < 4 && px == 7) || (py in 5..9 && px == 12) || (py > 10 && px == 4)
            if (seam) rgb(92, 84, 58) else rgb(146, 134, 92)
        }
        // 6: Palm log
        paintTile(TILE_PALM_LOG) { px, py ->
            val ring = if (py % 3 == 0) -18 else 0
            rgb(122 + ring, 86 + ring / 2, 52 + ring / 3)
        }
        // 7: Palm leaves
        paintTile(TILE_PALM_LEAVES) { px, py ->
            val shade = if ((px + py) % 3 == 0) -18 else 10
            rgb(64 + shade / 2, 126 + shade, 54 + shade / 2)
        }
        // 8: Gloo Block tactical shield
        paintTile(TILE_GLOO) { px, py ->
            val frame = px == 0 || py == 0 || px == 15 || py == 15
            val core = px in 5..10 && py in 5..10
            when {
                frame -> rgb(255, 140, 26)
                core -> rgb(96, 178, 224)
                else -> rgb(64, 116, 168)
            }
        }
        // 9: Blue Squad Soldier Camo
        paintTile(TILE_SOLDIER_BLUE) { px, py ->
            val camo = (px / 3 + py / 2) % 3
            when (camo) {
                0 -> rgb(38, 128, 210)
                1 -> rgb(28, 92, 164)
                else -> rgb(78, 168, 232)
            }
        }
        // 10: Red Squad Soldier Camo
        paintTile(TILE_SOLDIER_RED) { px, py ->
            val camo = (px / 3 + py / 2) % 3
            when (camo) {
                0 -> rgb(212, 60, 52)
                1 -> rgb(148, 38, 34)
                else -> rgb(235, 108, 86)
            }
        }
        // 11: Voxel Soldier Head / Tactical Visor
        paintTile(TILE_FACE) { px, py ->
            when {
                py <= 4 -> rgb(74, 82, 58) // Tactical helmet
                py in 5..8 && px in 2..13 -> rgb(255, 152, 26) // Glowing tactical visor
                else -> rgb(214, 172, 134) // Face skin
            }
        }
        // 12: Gunmetal
        paintTile(TILE_GUN) { px, py ->
            val stripe = if ((px + py) % 4 == 0) 14 else 0
            rgb(185 + stripe, 185 + stripe, 190 + stripe)
        }
        // 13: Muzzle Flash
        paintTile(TILE_MUZZLE) { px, py ->
            val dist = kotlin.math.abs(px - 8) + kotlin.math.abs(py - 8)
            if (dist < 6) rgb(255, 245, 180) else rgb(255, 148, 32)
        }

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return texIds[0]
    }

    private fun rgb(r: Int, g: Int, b: Int): Int {
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)
        return (0xFF shl 24) or (cr shl 16) or (cg shl 8) or cb
    }

    private fun allocateFloatBuffer(floatCount: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(floatCount * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        const val FLOATS_PER_VERTEX = 6
        const val ATLAS_COLS = 4

        const val TILE_SAND = 0
        const val TILE_SANDSTONE = 1
        const val TILE_TERRACOTTA = 2
        const val TILE_CONCRETE = 3
        const val TILE_CRATE = 4
        const val TILE_SANDBAG = 5
        const val TILE_PALM_LOG = 6
        const val TILE_PALM_LEAVES = 7
        const val TILE_GLOO = 8
        const val TILE_SOLDIER_BLUE = 9
        const val TILE_SOLDIER_RED = 10
        const val TILE_FACE = 11
        const val TILE_GUN = 12
        const val TILE_MUZZLE = 13

        const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            attribute vec3 aPosition;
            attribute vec2 aTexCoord;
            attribute float aShade;
            varying vec2 vTexCoord;
            varying float vShade;
            varying vec3 vWorldPos;
            void main() {
                vTexCoord = aTexCoord;
                vShade = aShade;
                vWorldPos = aPosition;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexture;
            uniform vec4 uTint;
            uniform vec3 uFogColor;
            uniform vec3 uCamPos;
            varying vec2 vTexCoord;
            varying float vShade;
            varying vec3 vWorldPos;
            void main() {
                vec4 tex = texture2D(uTexture, vTexCoord);
                vec3 lit = tex.rgb * vShade * uTint.rgb;
                float dist = distance(vWorldPos, uCamPos);
                float fogFactor = clamp((dist - 24.0) / 42.0, 0.0, 0.65);
                vec3 finalColor = mix(lit, uFogColor, fogFactor);
                gl_FragColor = vec4(finalColor, tex.a * uTint.a);
            }
        """
    }
}
