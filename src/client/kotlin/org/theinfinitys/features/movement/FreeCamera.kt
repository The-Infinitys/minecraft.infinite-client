package org.theinfinitys.features.movement

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

/**
 * FreeCamera (自由視点カメラ) Feature。
 * プレイヤー本体をその場に固定し、カメラを自由操作する。
 * FakePlayerの管理は依存関係にあるFreeze Featureが行う。
 */
class FreeCamera : ConfigurableFeature(initialEnabled = false) {
    private val mc: MinecraftClient = MinecraftClient.getInstance()
    override val available: Boolean = false

    // カメラの現在の座標と視点を保持する変数
    private var cameraPosition: Vec3d? = null
    private var cameraYaw: Float = 0f
    private var cameraPitch: Float = 0f

    private var realPlayer: ClientPlayerEntity? = null

    // --- 設定 ---
    private val speedSetting =
        InfiniteSetting.FloatSetting(
            "Speed",
            "Freecam中の移動速度を設定します。",
            1.0f,
            0.1f,
            5.0f,
        )

    override val settings: List<InfiniteSetting<*>> = listOf(speedSetting)
    override val depends: List<Class<out ConfigurableFeature>> = listOf(Freeze::class.java)

    override fun start() {
        realPlayer = mc.player ?: return

        // 1. カメラの位置と視点を、Freecam開始時の本物のプレイヤーと同期させる
        cameraPosition = realPlayer!!.pos
        cameraYaw = realPlayer!!.yaw
        cameraPitch = realPlayer!!.pitch

        // 2. カメラエンティティをFreecamの位置に設定（Mixin側で処理）
        // NOTE: FreeCameraのMixinでmc.gameRenderer.camera.thirdPerson = true; などを設定する必要がある場合がある
    }

    override fun stop() {
        val player = realPlayer ?: return
        val currentCameraPos = cameraPosition ?: return

        // 1. 本物のプレイヤーをカメラの最終位置に戻す
        // Freezeによって移動パケットが送信される際、この位置からワープが開始される
        player.teleport(currentCameraPos.x, currentCameraPos.y, currentCameraPos.z, true)

        // 2. 視点を復元
        player.setYaw(cameraYaw)
        player.setPitch(cameraPitch)

        // 3. 状態変数をクリア
        cameraPosition = null
        realPlayer = null
    }

    override fun tick() {
        val player = realPlayer ?: return
        var currentPos = cameraPosition ?: return

        // プレイヤーの視線（カメラの現在の視線）を更新
        cameraYaw = player.yaw
        cameraPitch = player.pitch

        // Freecam中の移動制御
        val option = mc.options
        val speed = speedSetting.value

        // 入力キーの状態に基づいて移動ベクトルを生成
        var deltaX = 0.0
        var deltaY = 0.0 // 上下移動
        var deltaZ = 0.0

        // 前後左右
        if (option.forwardKey.isPressed) deltaZ += speed
        if (option.backKey.isPressed) deltaZ -= speed
        if (option.rightKey.isPressed) deltaX += speed
        if (option.leftKey.isPressed) deltaX -= speed

        // 上昇下降 (通常はジャンプキーとスニークキー)
        if (option.jumpKey.isPressed) deltaY += speed
        if (option.sneakKey.isPressed) deltaY -= speed

        // 現在のカメラの視線方向に基づいて移動ベクトルを変換
        val rotationVector = player.rotationVector
        val rightVector = Vec3d(rotationVector.z, 0.0, -rotationVector.x).normalize()

        var moveVec = Vec3d(0.0, 0.0, 0.0)
        moveVec = moveVec.add(rotationVector.multiply(deltaZ.toDouble())) // 前後
        moveVec = moveVec.add(rightVector.multiply(deltaX.toDouble())) // 左右
        moveVec = moveVec.add(0.0, deltaY.toDouble(), 0.0) // 上下

        // 移動を実行し、座標を更新
        currentPos = currentPos.add(moveVec.multiply(0.1)) // 適切な係数で移動
        cameraPosition = currentPos

        // カメラの描画位置を更新
        // NOTE: この部分が最も重要で、Mixinまたは別のフックでこのcameraPositionを参照する必要がある。
        // 現在のMixinの骨格ではこのフックがないため、動作しない可能性がある。
    }

    /**
     * カメラの現在の位置を返す（Mixinからアクセスされることを想定）
     */
    fun getCameraPosition(): Vec3d? = cameraPosition

    /**
     * カメラの現在のYaw（左右）を返す
     */
    fun getCameraYaw(): Float = cameraYaw

    /**
     * カメラの現在のPitch（上下）を返す
     */
    fun getCameraPitch(): Float = cameraPitch
}
