package org.infinite.utils

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import org.infinite.InfiniteClient
import java.util.concurrent.ConcurrentLinkedQueue

object LogQueue : ClientTickEvents.EndTick {
    // プレイヤーに送信待ちのメッセージを格納するスレッドセーフなキュー
    private val messageQueue = ConcurrentLinkedQueue<MutableText>()
    private const val MESSAGES_PER_TICK = 8 // 1ティックあたりに処理するメッセージの数

    /**
     * キューにメッセージを追加します。
     * どこから呼び出されてもスレッドセーフです。
     *
     * @param message プレイヤーに送信するメッセージ
     */
    fun enqueueMessage(message: MutableText) {
        messageQueue.add(message)
    }

    /**
     * クライアントのティックの終わりに呼び出されます。
     * キューからメッセージを取り出し、プレイヤーに送信します。
     */
    override fun onEndTick(client: MinecraftClient) {
        repeat(MESSAGES_PER_TICK) {
            val message = messageQueue.poll() // キューからメッセージを取り出す
            if (message != null) {
                val player = client.player
                if (player == null) {
                    println(message.string)
                } else {
                    player.sendMessage(message, false)
                }
            } else {
                return
            }
        }
    }

    /**
     * InfiniteClientのonInitializeClientでこのロガーを登録するために呼び出す
     */
    fun registerTickEvent() {
        ClientTickEvents.END_CLIENT_TICK.register(this)
        InfiniteClient.info("LogQueue tick event registered.")
    }
}
