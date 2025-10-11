package org.theinfinitys.features.movement.feather

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

// FeatherWalk Featureの定義
class FeatherWalk : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    // フィーチャーのロジックで利用する設定
    private val blockList: InfiniteSetting.BlockListSetting =
        InfiniteSetting.BlockListSetting(
            name = "Allowed Blocks",
            description = "ジャンプ・ダッシュを抑制するブロックIDのリストです。",
            defaultValue = mutableListOf("minecraft:farmland", "minecraft:gravel"), // 例として砂と砂利を設定
        )

    private val disableJump: InfiniteSetting.BooleanSetting =
        InfiniteSetting.BooleanSetting(
            name = "Disable Jump",
            description = "設定されたブロック上でのジャンプを無効にします。",
            defaultValue = true,
        )

    private val disableSprint: InfiniteSetting.BooleanSetting =
        InfiniteSetting.BooleanSetting(
            name = "Disable Sprint",
            description = "設定されたブロック上でのダッシュ（スプリント）を無効にします。",
            defaultValue = true,
        )
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            blockList,
            disableJump,
            disableSprint,
        )

    // --- 仮定されるゲームクライアントへのアクセスポイント ---
    // 実際の環境に合わせて適宜変更してください。
    // 例: Minecraft client object
    private var mc = MinecraftClient.getInstance()

    override fun tick() {
        var isWalkingOnFeatherBlock = false

        // 1. プレイヤーの現在のブロック座標を取得
        // プレイヤーの足元（Y-1）ではなく、プレイヤーの中心ブロックを取得する
        val playerX =
            mc.player
                ?.x
                ?.toInt() ?: return
        val playerY =
            mc.player
                ?.y
                ?.toInt() ?: return
        val playerZ =
            mc.player
                ?.z
                ?.toInt() ?: return

        // 2. プレイヤーの中心ブロックとその周囲1ブロック（3x3x3 = 27ブロック）を確認
        // X, Y, Z軸で -1 から +1 までの範囲を反復処理
        for (xOffset in -1..1) {
            for (yOffset in -1..1) {
                for (zOffset in -1..1) {
                    // 確認するブロックの座標
                    val checkX = playerX + xOffset
                    val checkY = playerY + yOffset
                    val checkZ = playerZ + zOffset

                    // 現在のブロックのID/名前を取得 (環境依存のメソッド)
                    // 例: mc.world.getBlockState(x, y, z).id.toString()
                    val blockName =
                        Registries.BLOCK
                            .getId(mc.world?.getBlockState(BlockPos(Vec3i(checkX, checkY, checkZ)))?.block)
                            .toString()

                    // 設定されたブロックリストに含まれているかチェック
                    if (blockList.value.contains(blockName)) {
                        isWalkingOnFeatherBlock = true
                        // 一致するブロックが見つかったら、すぐにループを終了
                        break
                    }
                }
                if (isWalkingOnFeatherBlock) break
            }
            if (isWalkingOnFeatherBlock) break
        }

        // 3. 設定に基づいて制御を適用
        if (isWalkingOnFeatherBlock) {
            if (disableJump.value) {
                // ジャンプ入力を抑制するロジック (例: mc.player.input.jumping = false)
                mc.options.jumpKey.isPressed = false
            }
            if (disableSprint.value) {
                mc.player?.isSprinting = false // Stop sprinting if hunger is too low
            }
        }
    }
}
