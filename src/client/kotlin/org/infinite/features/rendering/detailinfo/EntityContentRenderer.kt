package org.infinite.features.rendering.detailinfo

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.registry.Registries
import net.minecraft.util.math.ColorHelper
import org.infinite.libs.graphics.Graphics2D

object EntityContentRenderer {
    private const val PADDING = 5

    fun calculateHeight(
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.EntityDetail,
        uiWidth: Int,
    ): Int {
        val font = client.textRenderer
        var requiredHeight = DetailInfoRenderer.BORDER_WIDTH + PADDING + font.fontHeight + PADDING

        val entity = detail.entity
        if (entity is LivingEntity) {
            // Health bar
            requiredHeight += DetailInfoRenderer.BAR_HEIGHT + DetailInfoRenderer.BAR_PADDING

            // Equipment section
            requiredHeight += font.fontHeight + PADDING // Header
            val equipmentCount = getEquipmentCount(entity)
            requiredHeight += equipmentCount * font.fontHeight + if (equipmentCount > 0) PADDING else 0

            // Effects section
            requiredHeight += font.fontHeight + PADDING // Header
            val effects = entity.statusEffects
            requiredHeight += effects.size * font.fontHeight + if (effects.isNotEmpty()) PADDING else 0

            // Armor value
            requiredHeight += font.fontHeight + PADDING
        }

        // Position at the bottom
        requiredHeight += font.fontHeight + DetailInfoRenderer.BORDER_WIDTH + PADDING
        requiredHeight += font.fontHeight + PADDING
        return requiredHeight
    }

    fun draw(
        graphics2d: Graphics2D,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.EntityDetail,
        startX: Int,
        startY: Int,
        uiWidth: Int,
    ) {
        val font = client.textRenderer
        var currentY = startY + DetailInfoRenderer.BORDER_WIDTH + PADDING

        // Entity name and ID
        val textX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING
        val entityName = detail.entity.type.name.string
        val entityId = Registries.ENTITY_TYPE.getId(detail.entity.type).toString()
        graphics2d.drawText(entityName, textX, currentY, 0xFFFFFFFF.toInt(), true)
        val nameWidth = font.getWidth(entityName)
        graphics2d.drawText(
            "($entityId)",
            textX + nameWidth + 5,
            currentY,
            ColorHelper.getArgb(192, 255, 255, 255),
            true,
        )
        currentY += font.fontHeight + PADDING

        val entity = detail.entity
        if (entity is LivingEntity) {
            currentY += DetailInfoRenderer.BAR_HEIGHT + DetailInfoRenderer.BAR_PADDING

            // Equipment
            graphics2d.drawText("Equipment:", textX, currentY, 0xFFFFFFFF.toInt(), true)
            currentY += font.fontHeight + PADDING
            currentY = drawEquipment(graphics2d, font, entity, textX + PADDING, currentY)

            // Status Effects
            graphics2d.drawText("Status Effects:", textX, currentY, 0xFFFFFFFF.toInt(), true)
            currentY += font.fontHeight + PADDING
            currentY = drawEffects(graphics2d, font, entity.statusEffects, textX + PADDING, currentY)

            // Armor
            val armor = entity.armor
            graphics2d.drawText("Armor: $armor", textX, currentY, 0xFFFFFFFF.toInt(), true)
            currentY += font.fontHeight + PADDING
        }

        // Position
        val infoPos = detail.entity.blockPos
        val posText = "Pos: x=${infoPos.x}, y=${infoPos.y}, z=${infoPos.z}"
        graphics2d.drawText(posText, textX, currentY, 0xFFFFFFFF.toInt(), true)
    }

    private fun getEquipmentCount(entity: LivingEntity): Int {
        var count = 0
        if (!entity.mainHandStack.isEmpty) count++
        if (!entity.offHandStack.isEmpty) count++
        EquipmentSlot.entries
            .filter {
                it in
                    listOf(
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET,
                    )
            }.forEach {
                if (!entity.getEquippedStack(it).isEmpty) count++
            }
        return count
    }

    private fun drawEquipment(
        graphics2d: Graphics2D,
        font: net.minecraft.client.font.TextRenderer,
        entity: LivingEntity,
        x: Int,
        y: Int,
    ): Int {
        var currentY = y
        if (!entity.mainHandStack.isEmpty) {
            graphics2d.drawText(
                "Main Hand: ${entity.mainHandStack.name.string}",
                x,
                currentY,
                0xFFFFFFFF.toInt(),
                true,
            )
            currentY += font.fontHeight
        }
        if (!entity.offHandStack.isEmpty) {
            graphics2d.drawText(
                "Off Hand: ${entity.offHandStack.name.string}",
                x,
                currentY,
                0xFFFFFFFF.toInt(),
                true,
            )
            currentY += font.fontHeight
        }
        EquipmentSlot.entries
            .filter {
                it in
                    listOf(
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET,
                    )
            }.forEach {
                val stack = entity.getEquippedStack(it)
                if (!stack.isEmpty) {
                    graphics2d.drawText(
                        "${it.name.lowercase().replaceFirstChar { it.uppercase() }}: ${stack.name.string}",
                        x,
                        currentY,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                    currentY += font.fontHeight
                }
            }
        return if (getEquipmentCount(entity) > 0) currentY + PADDING else currentY
    }

    private fun drawEffects(
        graphics2d: Graphics2D,
        font: net.minecraft.client.font.TextRenderer,
        effects: Collection<StatusEffectInstance>,
        x: Int,
        y: Int,
    ): Int {
        var currentY = y
        effects.forEach { effect ->
            val effectId = Registries.STATUS_EFFECT.getId(effect.effectType.value())?.path ?: "Unknown"
            val effectName = effectId.replaceFirstChar { it.uppercase() }
            val duration = effect.duration / 20 // in seconds
            val amplifier = effect.amplifier + 1
            graphics2d.drawText(
                "$effectName $amplifier (${duration}s)",
                x,
                currentY,
                0xFFFFFFFF.toInt(),
                true,
            )
            currentY += font.fontHeight
        }
        return if (effects.isNotEmpty()) currentY + PADDING else currentY
    }
}
