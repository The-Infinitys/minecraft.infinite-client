package org.infinite

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.GameOptions
import net.minecraft.client.world.ClientWorld
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.world.WorldManager
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.lwjgl.glfw.GLFW

enum class FeatureLevel {
    UTILS, // ユーリティ。基本どこで使ってても問題ない。
    EXTEND, // 実際には不可能なので、見られると気づかれる可能性がある
    CHEAT, // サーバー側で簡単に検知される
}

abstract class ConfigurableFeature(
    private val initialEnabled: Boolean = false,
) {
    internal val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    internal val player: ClientPlayerEntity?
        get() = client.player
    internal val world: ClientWorld?
        get() = client.world
    internal val options: GameOptions
        get() = client.options
    internal var enabled: Property<Boolean> = Property(initialEnabled)
    private val disabled: Property<Boolean> = Property(!initialEnabled)
    open val toggleKeyBind: Property<Int> = Property(GLFW.GLFW_DONT_CARE)
    open val togglable = true
    open val preRegisterCommands: List<String> = listOf("enable", "disable", "toggle", "set", "get", "add", "del")
    open val level: FeatureLevel = FeatureLevel.EXTEND

    // リスナーの同期に使用する専用のロックオブジェクト
    private val listenerLock = Any()

    // 依存関係・矛盾関係のリスナーを保持するリスト
    private val dependencyListeners = mutableListOf<() -> Unit>()

    init {
        // Featureが有効になったとき
        enabled.addListener { _, newValue ->
            disabled.value = !newValue
            if (newValue) {
                // 依存・矛盾の即時解決
                resolve()
                enabled()
            } else {
                // Featureが無効になったとき
                disabled()
            }
        }

        // Featureが無効になったとき
        disabled.addListener { _, newValue ->
            enabled.value = !newValue
            if (newValue) {
                disabled()
            } else {
                resolve()
                enabled()
            }
        }
    }

    fun reset() {
        enabled.value = initialEnabled
        settings.forEach { it.reset() }
    }

    abstract val settings: List<FeatureSetting<*>>
    open val depends: List<Class<out ConfigurableFeature>> = emptyList()
    open val conflicts: List<Class<out ConfigurableFeature>> = emptyList()

    open fun tick() {}

    open fun start() {}

    // --- リスナー登録用API ---
    fun addEnabledChangeListener(listener: (oldValue: Boolean, newValue: Boolean) -> Unit) {
        enabled.addListener(listener)
    }

    private fun startResolver() {
        // 【スレッドセーフ化】専用のロックオブジェクトで同期
        synchronized(listenerLock) {
            for (depend in depends) {
                val feature = InfiniteClient.getFeature(depend) ?: continue
                val listener: (Boolean, Boolean) -> Unit = { _, newDisabled ->
                    if (newDisabled && isEnabled()) {
                        disable()
                    }
                }
                feature.disabled.addListener(listener)
                dependencyListeners.add { feature.disabled.removeListener(listener) }
            }
            for (conflict in conflicts) {
                val feature = InfiniteClient.getFeature(conflict) ?: continue
                val listener: (Boolean, Boolean) -> Unit = { _, newEnabled ->
                    if (newEnabled && isEnabled()) {
                        disable()
                    }
                }
                feature.enabled.addListener(listener)
                dependencyListeners.add { feature.enabled.removeListener(listener) }
            }
        }
    }

    open fun stop() {}

    private fun stopResolver() {
        // 【スレッドセーフ化】専用のロックオブジェクトで同期
        synchronized(listenerLock) {
            dependencyListeners.forEach { it() }
            dependencyListeners.clear()
        }
    }

    open fun enabled() {}

    open fun disabled() {}

    fun enable() {
        if (isEnabled()) return

        // 1. 依存関係の監視を開始し、リスナーを登録
        startResolver()
        // 2. プロパティの値を変更 (Property.setValue()を実行)
        enabled.value = true
    }

    fun disable() {
        if (isDisabled()) return

        // 1. 依存関係の監視を停止し、リスナーを解除
        stopResolver()
        // 2. プロパティの値を変更 (Property.setValue()を実行)
        disabled.value = true
    }

    fun isEnabled(): Boolean = enabled.value

    fun isDisabled(): Boolean = disabled.value

    fun getSetting(name: String): FeatureSetting<*>? = settings.find { it.name == name }

    open fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {}

    private fun resolve() {
        for (depend in depends) {
            val feature = InfiniteClient.getFeature(depend) ?: continue
            if (feature.isDisabled()) {
                feature.enable()
            }
        }
        for (conflict in conflicts) {
            val feature = InfiniteClient.getFeature(conflict) ?: continue
            if (feature.isEnabled()) {
                feature.disable()
            }
        }
    }

    open fun render2d(graphics2D: Graphics2D) {
    }

    open fun render3d(graphics3D: Graphics3D) {
    }

    open fun handleChunk(worldChunk: WorldManager.Chunk) {}
}
