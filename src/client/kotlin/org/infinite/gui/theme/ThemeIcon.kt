package org.infinite.gui.theme
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier

class ThemeIcon(
    val identifier: Identifier,
    customWidth: Int? = null,
    customHeight: Int? = null,
) {
    val width: Int
    val height: Int

    init {
        if (customWidth != null && customHeight != null) {
            width = customWidth
            height = customHeight
        } else {
            val mc: MinecraftClient = MinecraftClient.getInstance()
            val resourceManager: ResourceManager = mc.resourceManager
            val resource = resourceManager.getResource(identifier)
            if (resource.isPresent) {
                val iconResource = resource.get()
                val image = NativeImage.read(iconResource.inputStream)
                width = image.width
                height = image.height
            } else {
                width = 256
                height = 256
            }
        }
    }
}
