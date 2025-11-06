package org.infinite

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.world.WorldManager
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.infinite.utils.toSnakeCase
import org.lwjgl.glfw.GLFW

abstract class ConfigurableFeature(
    private val initialEnabled: Boolean = false,
) : ClientInterface() {
    enum class FeatureLevel {
        Utils, // ユーリティ。基本どこで使ってても問題ない。
        Extend, // 実際には不可能なので、見られると気づかれる可能性がある
        Cheat, // サーバー側で簡単に検知される
    }

    enum class TickTiming {
        Start,
        End,
    }

    open val tickTiming: TickTiming = TickTiming.Start
    internal var enabled: Property<Boolean> = Property(initialEnabled)
    private val disabled: Property<Boolean> = Property(!initialEnabled)
    open val toggleKeyBind: Property<Int> = Property(GLFW.GLFW_DONT_CARE)
    open val togglable = true
    open val preRegisterCommands: List<String> = listOf("enable", "disable", "toggle", "set", "get", "add", "del")
    open val level: FeatureLevel = FeatureLevel.Extend

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

    class ActionKeybind(
        val name: String,
        private val key: Int,
        val action: () -> Unit,
    ) {
        lateinit var keyBinding: KeyBinding
        lateinit var translationKey: String

        fun register(
            categoryName: String,
            featureName: String,
            keyBindingCategory: KeyBinding.Category,
        ): ActionKeybind {
            translationKey =
                "key.infinite-client.action.${toSnakeCase(categoryName)}.${toSnakeCase(featureName)}.${toSnakeCase(name)}"
            keyBinding =
                KeyBinding(
                    translationKey,
                    InputUtil.Type.KEYSYM,
                    key,
                    keyBindingCategory,
                )
            KeyBindingHelper.registerKeyBinding(
                keyBinding,
            )
            return this
        }
    }

    open val actionKeybinds: List<ActionKeybind> = listOf()

    fun toggle() {
        if (isEnabled()) disable() else enable()
    }

    open fun respawn() {}

    fun registerKeybinds(
        categoryName: String,
        featureName: String,
        keyBindingCategory: KeyBinding.Category,
    ): List<ActionKeybind> = actionKeybinds.map { it.register(categoryName, featureName, keyBindingCategory) }

    fun registeredTranslations(): List<String> = actionKeybinds.map { it.translationKey }
}
