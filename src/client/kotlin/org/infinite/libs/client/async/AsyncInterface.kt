package org.infinite.libs.client.async

import java.util.Collections
import java.util.LinkedList
import java.util.Timer
import kotlin.concurrent.timerTask

object AsyncInterface {
    // ユーザー提供のデータクラス。delayは実行までの時間（ms）を示す
    data class AsyncAction(
        var delay: Long,
        val action: () -> Unit,
    )

    // --- 内部管理のための構造 ---

    // スケジュールされたアクションを内部で保持するためのデータクラス（実行目標時刻を保持）
    private data class ScheduledAction(
        val executeTime: Long,
        val action: () -> Unit,
    )

    // スレッドセーフなリストでアクションを保持 (LinkedListを同期化)
    private val scheduledActions = Collections.synchronizedList(LinkedList<ScheduledAction>())

    // --- タイムトラッキングとタイマー ---

    private var time: Long = System.currentTimeMillis()

    /** 最後にdeltaが取得されてからの経過時間（ms）。ゲームループなどで有用。 */
    val delta: Long
        get() {
            val currentTime = System.currentTimeMillis()
            // 少なくとも1msを保証
            val delta = (currentTime - time).coerceAtLeast(1)
            time = currentTime
            return delta
        }
    val timer = Timer()

    /**
     * タイマータスクをスケジュールして、非同期インターフェースを開始します。
     * 30msごとにprocess()が実行されます。
     */
    fun init() {
        // 0ms後に開始し、30ms間隔で実行
        timer.schedule(timerTask { process() }, 0, 30)
    }

    // --- アクション管理 ---

    /**
     * 新しい非同期アクションをスケジュールに追加します。
     * 実行目標時刻を計算し、内部リストに格納します。
     * @param action delay（遅延時間）と action（実行する処理）を含むAsyncActionオブジェクト
     */
    fun add(action: AsyncAction) {
        // 現在時刻 + 遅延時間 = 実行目標時刻
        val executeTime = System.currentTimeMillis() + action.delay
        scheduledActions.add(ScheduledAction(executeTime, action.action))
    }

    /**
     * スケジュールされたアクションを処理し、実行時間が過ぎたものを実行します。
     */
    fun process() {
        val currentTime = System.currentTimeMillis()

        // スレッドセーフなアクセスと変更のためにsynchronizedブロックを使用
        synchronized(scheduledActions) {
            val iterator = scheduledActions.iterator()
            while (iterator.hasNext()) {
                val scheduledAction = iterator.next()

                if (scheduledAction.executeTime <= currentTime) {
                    // 実行時間が過ぎたアクションを実行
                    try {
                        scheduledAction.action()
                    } catch (e: Exception) {
                        // アクション実行中の例外をキャッチ（非同期処理の例外処理は重要）
                        System.err.println("Async action execution failed: $e")
                    }
                    // 実行後、リストから削除
                    iterator.remove()
                }
            }
        }
    }
}
