package org.theinfinitys.settings

class Property<T>(
    initialValue: T,
) {
    // リスナーリストの同期に使用するロックオブジェクト (Propertyインスタンス自体をロックすることも可能だが、ここでは明示的なロックを使用)
    private val listenerLock = Any()

    // リスナーリスト自体はプライベートなままで、操作時にロックを使用
    private val listeners = mutableListOf<(oldValue: T, newValue: T) -> Unit>()

    var value: T = initialValue
        set(newValue) {
            if (field != newValue) {
                val oldValue = field
                field = newValue

                // 【重要修正 1】リスナーを実行する前に、ロックを使って安全にリストのコピーを作成する
                val listenersToNotify =
                    synchronized(listenerLock) {
                        listeners.toList() // コピーを作成
                    }

                // コピーに対して反復処理を実行。これにより ConcurrentModificationException を回避
                listenersToNotify.forEach { it(oldValue, newValue) }
            }
        }

    fun addListener(listener: (oldValue: T, newValue: T) -> Unit) {
        // 【重要修正 2】リストの変更操作を同期
        synchronized(listenerLock) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (oldValue: T, newValue: T) -> Unit) {
        // 【重要修正 3】リストの変更操作を同期
        synchronized(listenerLock) {
            listeners.remove(listener)
        }
    }
}
