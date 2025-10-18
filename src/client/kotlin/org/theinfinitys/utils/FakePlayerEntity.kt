package org.theinfinitys.utils

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import org.jetbrains.annotations.Nullable
import java.util.UUID

/**
 * Creates a client-side "fake player" entity that mimics the real player's appearance and inventory.
 * This is typically used for client-side modding features like visual testing or movement tricks.
 */
class FakePlayerEntity : OtherClientPlayerEntity {
    // MinecraftClientのインスタンスはシングルトンであるため、遅延初期化プロパティとして取得
    private val client: MinecraftClient = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity =
        client.player ?: throw IllegalStateException("Client player must be present.")
    private val world: ClientWorld = client.world ?: throw IllegalStateException("Client world must be present.")

    // PlayerListEntryは必要に応じて遅延で初期化
    private var playerListEntry: PlayerListEntry? = null

    constructor() : super(
        MinecraftClient.getInstance().world, // worldはコンストラクタでNullを許容しないが、ClientWorldが必須
        MinecraftClient.getInstance().player?.gameProfile
            ?: throw IllegalStateException("Client player profile must be present."),
    ) {
        // UUIDをランダムに設定し、本物のプレイヤーと区別
        uuid = UUID.randomUUID()

        // 位置と回転（視線）を本物のプレイヤーからコピー
        copyPositionAndRotation(player)

        // インベントリと体の向きをコピー
        copyInventory()
        copyRotation()

        // ワールドにスポーンさせる
        spawn()
    }

    override fun getPlayerListEntry(): @Nullable PlayerListEntry? {
        if (playerListEntry == null) {
            // 本物のプレイヤーのプロフィールIDでエントリを取得しようと試みる
            playerListEntry = client.networkHandler?.getPlayerListEntry(gameProfile.id)
        }
        return playerListEntry
    }

    override fun pushAway(entity: Entity) {
        // 本物のプレイヤーを押し出さないように、エンティティの押し合い処理を無効化
    }

    private fun copyInventory() {
        inventory.clone(player.inventory)
    }

    private fun copyRotation() {
        headYaw = player.headYaw
        bodyYaw = player.bodyYaw
    }

    private fun spawn() {
        // ワールドにエンティティとして追加
        world.addEntity(this)
    }

    /**
     * ワールドから偽プレイヤーを削除する
     */
    fun despawn() {
        discard() // エンティティを削除する
    }

    /**
     * 本物のプレイヤーの位置を、この偽プレイヤーの現在位置にリセットする
     */
    fun resetPlayerPosition() {
        player.refreshPositionAndAngles(x, y, z, yaw, pitch)
    }
}
