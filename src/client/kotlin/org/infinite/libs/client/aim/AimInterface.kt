package org.infinite.libs.client.aim

import net.minecraft.client.MinecraftClient
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimProcessResult

object AimInterface {
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    private var tasks: ArrayDeque<AimTask> = ArrayDeque(listOf())

    fun addTask(task: AimTask) {
        when (task.priority) {
            AimPriority.Normally -> tasks.addLast(task) // 一番後ろに追加 (通常のタスク)
            AimPriority.Immediately -> tasks.addFirst(task) // 一番前に追加 (即時実行タスク)
            AimPriority.Preferentially -> {
                val insertionIndex = tasks.indexOfFirst { it.priority == AimPriority.Normally }
                if (insertionIndex != -1) {
                    tasks.add(insertionIndex, task)
                } else {
                    tasks.addLast(task)
                }
            }
        }
    }

    fun taskLength(): Int = tasks.size

    fun currentTask(): AimTask? = tasks.firstOrNull()

    fun process() {
        val currentTask = currentTask() ?: return
        when (currentTask.process(client)) {
            AimProcessResult.Progress -> {}

            AimProcessResult.Failure -> {
                currentTask.atFailure()
                tasks.removeFirst()
            }

            AimProcessResult.Success -> {
                currentTask.atSuccess()
                tasks.removeFirst()
            }
        }
    }
}
